# -*- coding: utf-8 -*-
"""自动生成检索评估测试集。

流程：
    1. 调用后端 POST /resume-kb/page/list 拉取全部知识条目（分页循环）；
    2. 对每条知识，用 DeepSeek 生成 N 个「用户可能会问、且该条知识能够回答」的中文问题；
    3. 输出 dataset.jsonl，每行一个样本：
       {"question": "...", "expected_id": <知识条目id>, "category": "..."}

用法示例：
    python build_dataset.py --limit 20 --per-item 3 --output dataset.jsonl
    python build_dataset.py --base-url http://localhost:8080 --per-item 2

依赖环境变量 DEEPSEEK_API_KEY（生成问题用）。
"""

import argparse
import json
import os
import re
import sys

import requests
from openai import OpenAI

import config

# 拉取知识分页的接口路径
PAGE_LIST_PATH = "/resume-kb/page/list"

# 生成问题的系统提示词
SYSTEM_PROMPT = (
    "你是一名资深招聘顾问兼简历教练。"
    "给定一段简历知识库里的知识正文，请站在求职者的角度，"
    "生成若干个「真实的、口语化的、该知识恰好能回答」的中文问题。"
    "要求：问题之间角度不同、不要重复；只输出严格的 JSON 字符串数组，"
    "不要输出任何解释、前缀或 Markdown 代码块。"
)


def fetch_all_knowledge(base_url, page_size=100, limit=0):
    """分页拉取全部知识条目，返回 [{'id':int, 'content':str, 'category':str}, ...]。

    后端返回 PageResponse 结构，条目列表位于 data 字段：
        {"success":true, "total":n, "current":1, "size":100, "pages":p, "data":[...]}

    :param limit: 大于 0 时最多拉取 limit 条，用于快速调试。
    """
    url = base_url.rstrip("/") + PAGE_LIST_PATH
    items = []
    current = 1
    while True:
        payload = {"current": current, "size": page_size, "category": ""}
        resp = requests.post(url, json=payload, timeout=config.HTTP_TIMEOUT)
        resp.raise_for_status()
        body = resp.json()
        if not body.get("success", False):
            raise RuntimeError(
                "分页查询失败：message=%s, errorCode=%s"
                % (body.get("message"), body.get("errorCode"))
            )
        # 结果列表在 data 字段（见 PageResponse<T>）
        records = body.get("data") or []
        for row in records:
            items.append(
                {
                    "id": row.get("id"),
                    "content": row.get("content") or "",
                    "category": row.get("category") or "",
                }
            )
            if limit > 0 and len(items) >= limit:
                return items
        total = body.get("total") or 0
        if not records or len(items) >= total or len(records) < page_size:
            break
        current += 1
    return items


def _extract_json_array(text):
    """从 LLM 返回文本中健壮地提取 JSON 数组。

    处理：
        - 剥离 ```json ... ``` 代码块围栏；
        - 定位首个 '[' 与末个 ']' 截取候选片段；
        - 解析失败返回 None。
    """
    if not text:
        return None
    # 剥离 Markdown 代码块围栏
    cleaned = re.sub(r"```(?:json)?", "", text, flags=re.IGNORECASE).replace("```", "").strip()
    start = cleaned.find("[")
    end = cleaned.rfind("]")
    if start == -1 or end == -1 or end <= start:
        return None
    candidate = cleaned[start : end + 1]
    try:
        parsed = json.loads(candidate)
    except json.JSONDecodeError:
        return None
    if not isinstance(parsed, list):
        return None
    return [str(x).strip() for x in parsed if str(x).strip()]


def generate_questions(client, model, content, per_item):
    """针对单条知识正文，用 LLM 生成 per_item 个问题，返回问题字符串列表。"""
    user_prompt = (
        "知识正文如下：\n---\n%s\n---\n\n"
        "请生成 %d 个中文问题，以 JSON 字符串数组返回，例如 [\"问题1\", \"问题2\"]。"
        % (content, per_item)
    )
    resp = client.chat.completions.create(
        model=model,
        messages=[
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": user_prompt},
        ],
        temperature=0.7,
        timeout=config.HTTP_TIMEOUT,
    )
    text = resp.choices[0].message.content if resp.choices else ""
    questions = _extract_json_array(text)
    return questions, text


def main():
    parser = argparse.ArgumentParser(
        description="从后端知识库自动生成检索评估测试集（dataset.jsonl）",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )
    parser.add_argument("--limit", type=int, default=0, help="最多处理的知识条数，0 表示全部")
    parser.add_argument("--per-item", type=int, default=2, help="每条知识生成几个问题")
    parser.add_argument("--output", default="dataset.jsonl", help="输出文件路径")
    parser.add_argument("--base-url", default=config.EVAL_BASE_URL, help="后端服务基础地址")
    parser.add_argument("--page-size", type=int, default=100, help="分页拉取时每页条数")
    parser.add_argument("--model", default=config.JUDGE_MODEL, help="生成问题所用模型名")
    args = parser.parse_args()

    config.require_deepseek_api_key()
    client = OpenAI(api_key=config.DEEPSEEK_API_KEY, base_url=config.DEEPSEEK_BASE_URL)

    print("[1/3] 拉取知识库条目：%s" % (args.base_url.rstrip("/") + PAGE_LIST_PATH))
    items = fetch_all_knowledge(args.base_url, page_size=args.page_size, limit=args.limit)
    print("      共获取 %d 条知识" % len(items))
    if not items:
        print("未获取到任何知识条目，请确认后端已启动且知识库非空（可先调用 /resume-kb/import/samples）。")
        return 1

    print("[2/3] 调用 LLM 生成问题（per-item=%d）..." % args.per_item)
    samples = []
    failed = 0
    for idx, item in enumerate(items, 1):
        try:
            questions, raw = generate_questions(
                client, args.model, item["content"], args.per_item
            )
        except Exception as exc:  # noqa: BLE001 - 单个条目失败不影响整体
            failed += 1
            print("      [警告] 第 %d 条（id=%s）调用 LLM 失败：%s" % (idx, item["id"], exc))
            continue
        if not questions:
            failed += 1
            print(
                "      [警告] 第 %d 条（id=%s）未能解析出 JSON 数组，已跳过。原始返回片段：%s"
                % (idx, item["id"], (raw or "")[:120].replace("\n", " "))
            )
            continue
        for q in questions:
            samples.append(
                {"question": q, "expected_id": item["id"], "category": item["category"]}
            )
        print("      [%d/%d] id=%s 生成 %d 个问题" % (idx, len(items), item["id"], len(questions)))

    print("[3/3] 写出测试集：%s" % os.path.abspath(args.output))
    with open(args.output, "w", encoding="utf-8") as fp:
        for s in samples:
            fp.write(json.dumps(s, ensure_ascii=False) + "\n")

    print("完成：共 %d 条样本（失败/跳过 %d 条知识）" % (len(samples), failed))
    return 0


if __name__ == "__main__":
    sys.exit(main())
