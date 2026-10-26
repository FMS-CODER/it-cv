# -*- coding: utf-8 -*-
"""端到端评估：调用后端流式接口生成回答，再用 DeepSeek 作为裁判（LLM-as-Judge）打分。

流程：
    1. 读取 dataset.jsonl 中的 question；
    2. 对每个 question 调用后端 SSE 流式接口拿到完整回答文本：
       - 默认走「简历优化」接口 POST /resume-optimize/optimize（question 作为 resumeText 输入）；
       - 也可用 --endpoint chat 走「流式对话」接口 POST /chat/completion。
       SSE 每帧为 data:{"v": "..."}，脚本流式拼接所有 v 得到完整回答；
    3. 用 DeepSeek 对回答从「准确性 / 完整性 / 相关性」三个维度打 1-5 分（要求严格 JSON）；
    4. 汇总平均分，输出控制台报告 + JSON 到 eval/results/e2e_{label}_{timestamp}.json。

关于「有 RAG / 无 RAG」对比：
    后端两个接口都提供了 knowledgeRag 开关，因此可直接用 --rag on / --rag off 各跑一次，
    再以不同 --label 保存结果进行对比；
    若你的部署版本不支持该开关，则本脚本只做单组打分——此时要得到有/无 RAG 的对比，
    请分别在后端切换配置后以不同 --label 各跑一次。
"""

import argparse
import json
import os
import re
import sys
import time
from datetime import datetime

import requests
from openai import OpenAI

import config

# 后端接口路径
OPTIMIZE_PATH = "/resume-optimize/optimize"
CHAT_PATH = "/chat/completion"

# 三个打分维度
DIMENSIONS = ("accuracy", "completeness", "relevance")
DIMENSION_CN = {"accuracy": "准确性", "completeness": "完整性", "relevance": "相关性"}

# 裁判系统提示词：要求严格 JSON
JUDGE_SYSTEM_PROMPT = (
    "你是一名严格的资深技术面试官与简历顾问，负责评估 AI 回答的质量。"
    "请针对【用户问题】和【AI 回答】，从以下三个维度分别打分（1-5 的整数，5 为最好）：\n"
    "1. accuracy（准确性）：回答是否事实正确、无编造、无明显错误；\n"
    "2. completeness（完整性）：是否覆盖问题要点、给出足够可执行的信息；\n"
    "3. relevance（相关性）：是否紧扣问题、没有跑题或答非所问。\n"
    "只输出严格的 JSON 对象，不要输出任何解释、前缀或 Markdown 代码块，格式为：\n"
    '{"accuracy": 4, "completeness": 3, "relevance": 5, "reason": "一句话理由"}'
)


def load_questions(path, limit=0):
    """读取测试集里的 question 列表。"""
    if not os.path.exists(path):
        raise FileNotFoundError("测试集文件不存在：%s" % os.path.abspath(path))
    questions = []
    with open(path, "r", encoding="utf-8") as fp:
        for lineno, line in enumerate(fp, 1):
            line = line.strip()
            if not line:
                continue
            try:
                obj = json.loads(line)
            except json.JSONDecodeError as exc:
                print("[警告] 第 %d 行 JSON 解析失败，已跳过：%s" % (lineno, exc))
                continue
            if obj.get("question"):
                questions.append(obj["question"])
            if limit > 0 and len(questions) >= limit:
                break
    return questions


def call_stream_answer(base_url, question, endpoint, rag, model, kb_top_k, target_position):
    """调用后端 SSE 接口，流式拼接完整回答文本。

    返回 (answer_text, raw_frames)：answer_text 为拼接后的回答；raw_frames 为收到的帧数。
    """
    base = base_url.rstrip("/")
    if endpoint == "optimize":
        url = base + OPTIMIZE_PATH
        payload = {
            "resumeText": question,
            "targetPosition": target_position,
            "additionalRequirements": "",
            "knowledgeRag": rag,
            "kbTopK": kb_top_k,
        }
    else:  # chat
        url = base + CHAT_PATH
        payload = {
            "message": question,
            "chatId": "",
            "modelName": model,
            "temperature": 0.7,
            "knowledgeRag": rag,
            "kbTopK": kb_top_k,
        }

    headers = {"Content-Type": "application/json", "Accept": "text/event-stream"}
    pieces = []
    frames = 0
    with requests.post(
        url, json=payload, headers=headers, stream=True, timeout=config.STREAM_TIMEOUT
    ) as resp:
        resp.raise_for_status()
        for raw_line in resp.iter_lines(decode_unicode=True):
            if not raw_line:
                continue
            line = raw_line.strip()
            if not line.startswith("data:"):
                continue
            data_str = line[len("data:") :].strip()
            if not data_str or data_str == "[DONE]":
                continue
            try:
                frame = json.loads(data_str)
            except json.JSONDecodeError:
                # 非 JSON 帧（如纯文本心跳）直接忽略
                continue
            if isinstance(frame, dict):
                v = frame.get("v")
                if v:
                    pieces.append(v)
                    frames += 1
    return "".join(pieces), frames


def _extract_json_object(text):
    """从裁判返回中健壮地提取 JSON 对象，失败返回 None。"""
    if not text:
        return None
    cleaned = re.sub(r"```(?:json)?", "", text, flags=re.IGNORECASE).replace("```", "").strip()
    start = cleaned.find("{")
    end = cleaned.rfind("}")
    if start == -1 or end == -1 or end <= start:
        return None
    candidate = cleaned[start : end + 1]
    try:
        parsed = json.loads(candidate)
    except json.JSONDecodeError:
        return None
    return parsed if isinstance(parsed, dict) else None


def judge_answer(client, judge_model, question, answer):
    """用 LLM 裁判对单个回答打分，返回 {'accuracy':int, ..., 'reason':str}。"""
    if not answer.strip():
        return {
            "accuracy": 1,
            "completeness": 1,
            "relevance": 1,
            "reason": "回答为空，无法评估。",
        }
    user_prompt = (
        "【用户问题】\n%s\n\n【AI 回答】\n%s\n\n请按 JSON 格式给出评分。"
        % (question, answer[:4000])
    )
    resp = client.chat.completions.create(
        model=judge_model,
        messages=[
            {"role": "system", "content": JUDGE_SYSTEM_PROMPT},
            {"role": "user", "content": user_prompt},
        ],
        temperature=0.0,
        response_format={"type": "json_object"},
        timeout=config.HTTP_TIMEOUT,
    )
    text = resp.choices[0].message.content if resp.choices else ""
    parsed = _extract_json_object(text)
    if not parsed:
        return {
            "accuracy": None,
            "completeness": None,
            "relevance": None,
            "reason": "裁判返回无法解析：%s" % (text or "")[:120].replace("\n", " "),
        }
    result = {}
    for dim in DIMENSIONS:
        try:
            result[dim] = int(parsed.get(dim))
        except (TypeError, ValueError):
            result[dim] = None
    result["reason"] = str(parsed.get("reason", ""))[:200]
    return result


def run_eval(args):
    questions = load_questions(args.dataset, limit=args.limit)
    if not questions:
        print("测试集为空，请先用 build_dataset.py 生成或检查 %s" % args.dataset)
        return 1

    config.require_deepseek_api_key()
    client = OpenAI(api_key=config.DEEPSEEK_API_KEY, base_url=config.DEEPSEEK_BASE_URL)

    print(
        "开始端到端评估：dataset=%s, label=%s, endpoint=%s, rag=%s, 共 %d 条"
        % (os.path.abspath(args.dataset), args.label, args.endpoint, args.rag, len(questions))
    )

    cases = []
    sums = {dim: 0.0 for dim in DIMENSIONS}
    valid_counts = {dim: 0 for dim in DIMENSIONS}
    for i, question in enumerate(questions, 1):
        try:
            answer, frames = call_stream_answer(
                args.base_url,
                question,
                args.endpoint,
                args.rag,
                args.model,
                args.kb_top_k,
                args.target_position,
            )
        except Exception as exc:  # noqa: BLE001 - 单条失败不影响整体
            print("      [%d/%d] 调用后端失败：%s" % (i, len(questions), exc))
            answer, frames = "", 0

        scores = judge_answer(client, args.judge_model, question, answer)
        for dim in DIMENSIONS:
            if scores.get(dim) is not None:
                sums[dim] += scores[dim]
                valid_counts[dim] += 1

        cases.append(
            {
                "question": question,
                "answer": answer,
                "answer_length": len(answer),
                "sse_frames": frames,
                "scores": {dim: scores.get(dim) for dim in DIMENSIONS},
                "reason": scores.get("reason", ""),
            }
        )
        print(
            "      [%d/%d] 准确性=%s 完整性=%s 相关性=%s | %s"
            % (
                i,
                len(questions),
                scores.get("accuracy"),
                scores.get("completeness"),
                scores.get("relevance"),
                question[:30],
            )
        )

    averages = {
        dim: round(sums[dim] / valid_counts[dim], 4) if valid_counts[dim] else None
        for dim in DIMENSIONS
    }
    valid_avgs = [averages[d] for d in DIMENSIONS if averages[d] is not None]
    overall = round(sum(valid_avgs) / len(valid_avgs), 4) if valid_avgs else None
    metrics = {"averages": averages, "overall": overall, "total": len(questions)}

    # 控制台报告
    print("\n" + "=" * 72)
    print("端到端评估报告  |  label=%s" % args.label)
    print("=" * 72)
    print("%-4s %-8s %-8s %-8s %s" % ("#", "准确", "完整", "相关", "问题"))
    print("-" * 72)
    for i, c in enumerate(cases, 1):
        s = c["scores"]
        print(
            "%-4d %-8s %-8s %-8s %s"
            % (
                i,
                s["accuracy"] if s["accuracy"] is not None else "-",
                s["completeness"] if s["completeness"] is not None else "-",
                s["relevance"] if s["relevance"] is not None else "-",
                c["question"][:32],
            )
        )
    print("-" * 72)
    print("平均分（1-5）：")
    for dim in DIMENSIONS:
        print("  %-6s : %s" % (DIMENSION_CN[dim], averages[dim]))
    print("  综合平均 : %s" % overall)
    print("=" * 72 + "\n")

    # 落盘
    config.ensure_results_dir()
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    out_path = os.path.join(config.RESULTS_DIR, "e2e_%s_%s.json" % (args.label, timestamp))
    result = {
        "label": args.label,
        "dataset": os.path.abspath(args.dataset),
        "base_url": args.base_url,
        "endpoint": args.endpoint,
        "rag": args.rag,
        "judge_model": args.judge_model,
        "timestamp": time.strftime("%Y-%m-%d %H:%M:%S"),
        "metrics": metrics,
        "cases": cases,
    }
    with open(out_path, "w", encoding="utf-8") as fp:
        json.dump(result, fp, ensure_ascii=False, indent=2)
    print("结果已保存：%s" % out_path)
    return 0


def main():
    parser = argparse.ArgumentParser(
        description="端到端评估：调用后端 SSE 接口生成回答，并用 DeepSeek 作裁判（1-5 分）打分",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )
    parser.add_argument("--dataset", default="dataset.jsonl", help="测试集 jsonl 路径")
    parser.add_argument("--label", default="default", help="实验标签，如 rag-on/rag-off")
    parser.add_argument("--base-url", default=config.EVAL_BASE_URL, help="后端服务基础地址")
    parser.add_argument("--limit", type=int, default=0, help="最多评估条数，0 表示全部")
    parser.add_argument(
        "--endpoint",
        choices=["optimize", "chat"],
        default="optimize",
        help="调用的后端流式接口：optimize=简历优化，chat=流式对话",
    )
    parser.add_argument(
        "--rag",
        dest="rag",
        action="store_true",
        default=True,
        help="启用知识库 RAG（默认启用）",
    )
    parser.add_argument(
        "--no-rag",
        dest="rag",
        action="store_false",
        help="关闭知识库 RAG（与 --rag 互斥）",
    )
    parser.add_argument("--target-position", default="", help="简历优化时的目标岗位（optimize 接口用）")
    parser.add_argument("--kb-top-k", type=int, default=5, help="RAG 检索 topK")
    parser.add_argument(
        "--model", default="deepseek-reasoner", help="chat 接口调用的大模型名（后端必填字段）"
    )
    parser.add_argument("--judge-model", default=config.JUDGE_MODEL, help="裁判模型名")
    args = parser.parse_args()
    return run_eval(args)


if __name__ == "__main__":
    sys.exit(main())
