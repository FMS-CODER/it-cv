# -*- coding: utf-8 -*-
"""离线检索评估：衡量知识库向量检索的召回质量。

对 dataset.jsonl 中的每个 question 调用 POST /resume-kb/search（统一用较大 topK
以便同时计算 Hit@1/3/5/10），并汇总以下指标：
    - Hit@1 / Hit@3 / Hit@5 / Hit@10：期望 id 是否出现在前 K 个结果中
    - MRR：第一个命中的排名倒数（未命中记 0）的均值
    - 平均 top1 相似度：每条用例首条结果的 similarity 均值
    - 空结果率：data 为空数组的占比

输出：控制台可读报告 + JSON 结果文件 eval/results/retrieval_{label}_{timestamp}.json

用法示例：
    # 跑一次「单路检索」基线
    python retrieval_eval.py --label baseline
    # 跑「双路检索」/「加重排」版本（后端切换配置后）
    python retrieval_eval.py --label two-route
    python retrieval_eval.py --label rerank
    # 对比两次实验
    python retrieval_eval.py --compare results/retrieval_baseline_xxx.json results/retrieval_rerank_xxx.json
"""

import argparse
import json
import os
import sys
import time
from datetime import datetime

import requests

import config

# 检索接口路径
SEARCH_PATH = "/resume-kb/search"
# 需要统计的 Hit@K 档位
HIT_KS = (1, 3, 5, 10)


def load_dataset(path):
    """读取 jsonl 测试集，返回样本列表。"""
    if not os.path.exists(path):
        raise FileNotFoundError("测试集文件不存在：%s" % os.path.abspath(path))
    samples = []
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
            if "question" not in obj:
                print("[警告] 第 %d 行缺少 question 字段，已跳过" % lineno)
                continue
            samples.append(obj)
    return samples


def search(base_url, query, top_k, category=None):
    """调用检索接口，返回 data 列表（可能为空）。"""
    url = base_url.rstrip("/") + SEARCH_PATH
    payload = {"query": query, "topK": top_k}
    if category:
        payload["category"] = category
    resp = requests.post(url, json=payload, timeout=config.HTTP_TIMEOUT)
    resp.raise_for_status()
    body = resp.json()
    if not body.get("success", False):
        raise RuntimeError(
            "检索失败：message=%s, errorCode=%s" % (body.get("message"), body.get("errorCode"))
        )
    return body.get("data") or []


def evaluate(base_url, samples, top_k):
    """执行评估，返回 (metrics, cases)。"""
    cases = []
    hit_counts = {k: 0 for k in HIT_KS}
    reciprocal_ranks = []
    top1_similarities = []
    empty_count = 0

    for i, sample in enumerate(samples, 1):
        question = sample["question"]
        expected_id = sample.get("expected_id")
        category = sample.get("category") or None

        try:
            data = search(base_url, question, top_k, category=category)
        except Exception as exc:  # noqa: BLE001 - 单条失败不影响整体统计
            print("      [%d/%d] 检索异常：%s" % (i, len(samples), exc))
            data = []

        retrieved = [
            {"id": row.get("id"), "similarity": row.get("similarity")} for row in data
        ]
        is_empty = len(retrieved) == 0
        if is_empty:
            empty_count += 1

        # 计算期望 id 的命中排名（1-based），未命中为 None
        rank = None
        for pos, row in enumerate(retrieved, 1):
            if row["id"] == expected_id:
                rank = pos
                break

        for k in HIT_KS:
            if rank is not None and rank <= k:
                hit_counts[k] += 1

        reciprocal_ranks.append(1.0 / rank if rank else 0.0)
        if not is_empty and retrieved[0]["similarity"] is not None:
            top1_similarities.append(retrieved[0]["similarity"])

        cases.append(
            {
                "question": question,
                "expected_id": expected_id,
                "category": sample.get("category"),
                "hit_rank": rank,
                "empty": is_empty,
                "top1_similarity": retrieved[0]["similarity"] if not is_empty else None,
                "retrieved": retrieved,
            }
        )
        hit_mark = "命中@%d" % rank if rank else "未命中"
        print("      [%d/%d] %s | %s" % (i, len(samples), hit_mark, question[:40]))

    total = len(samples) or 1
    metrics = {
        "total": len(samples),
        "top_k": top_k,
        "MRR": round(sum(reciprocal_ranks) / total, 4),
        "avg_top1_similarity": round(sum(top1_similarities) / len(top1_similarities), 4)
        if top1_similarities
        else None,
        "empty_rate": round(empty_count / total, 4),
        "empty_count": empty_count,
    }
    for k in HIT_KS:
        metrics["Hit@%d" % k] = round(hit_counts[k] / total, 4)
    return metrics, cases


def _print_report(label, metrics, cases):
    """打印控制台可读报告（含每条用例命中情况）。"""
    print("\n" + "=" * 72)
    print("检索评估报告  |  label=%s" % label)
    print("=" * 72)

    header = "%-4s %-8s %-10s %-9s %s" % ("#", "命中", "排名", "top1相似", "问题")
    print(header)
    print("-" * 72)
    for i, c in enumerate(cases, 1):
        rank = c["hit_rank"]
        hit = "是" if rank else "否"
        rank_str = str(rank) if rank else "-"
        sim = "%.4f" % c["top1_similarity"] if c["top1_similarity"] is not None else "-"
        print("%-4d %-8s %-10s %-9s %s" % (i, hit, rank_str, sim, c["question"][:32]))
    print("-" * 72)

    print("\n指标汇总：")
    print("  样本数              : %d" % metrics["total"])
    for k in HIT_KS:
        print("  Hit@%-2d             : %.4f" % (k, metrics["Hit@%d" % k]))
    print("  MRR                 : %.4f" % metrics["MRR"])
    print("  平均 top1 相似度    : %s" % metrics["avg_top1_similarity"])
    print("  空结果率            : %.4f (%d 条)" % (metrics["empty_rate"], metrics["empty_count"]))
    print("=" * 72 + "\n")


def run_eval(args):
    """执行一次完整评估并落盘。"""
    samples = load_dataset(args.dataset)
    if not samples:
        print("测试集为空，请先用 build_dataset.py 生成或检查 %s" % args.dataset)
        return 1

    print("开始检索评估：dataset=%s, label=%s, topK=%d, base_url=%s"
          % (os.path.abspath(args.dataset), args.label, args.top_k, args.base_url))
    metrics, cases = evaluate(args.base_url, samples, args.top_k)
    _print_report(args.label, metrics, cases)

    config.ensure_results_dir()
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    out_path = os.path.join(config.RESULTS_DIR, "retrieval_%s_%s.json" % (args.label, timestamp))
    result = {
        "label": args.label,
        "dataset": os.path.abspath(args.dataset),
        "base_url": args.base_url,
        "top_k": args.top_k,
        "timestamp": time.strftime("%Y-%m-%d %H:%M:%S"),
        "metrics": metrics,
        "cases": cases,
    }
    with open(out_path, "w", encoding="utf-8") as fp:
        json.dump(result, fp, ensure_ascii=False, indent=2)
    print("结果已保存：%s" % out_path)
    return 0


def compare_results(path_a, path_b):
    """对比两份结果文件的指标差值，以表格输出。"""
    with open(path_a, "r", encoding="utf-8") as fp:
        a = json.load(fp)
    with open(path_b, "r", encoding="utf-8") as fp:
        b = json.load(fp)

    ma, mb = a.get("metrics", {}), b.get("metrics", {})
    label_a = a.get("label", os.path.basename(path_a))
    label_b = b.get("label", os.path.basename(path_b))

    metric_keys = ["Hit@%d" % k for k in HIT_KS] + ["MRR", "avg_top1_similarity", "empty_rate"]

    print("\n" + "=" * 72)
    print("检索实验对比  |  A=%s   B=%s" % (label_a, label_b))
    print("=" * 72)
    print("%-22s %-14s %-14s %-14s" % ("指标", "A", "B", "差值(B-A)"))
    print("-" * 72)
    for key in metric_keys:
        va, vb = ma.get(key), mb.get(key)
        va_str = "%.4f" % va if isinstance(va, (int, float)) else str(va)
        vb_str = "%.4f" % vb if isinstance(vb, (int, float)) else str(vb)
        if isinstance(va, (int, float)) and isinstance(vb, (int, float)):
            diff_str = "%+.4f" % (vb - va)
        else:
            diff_str = "-"
        print("%-22s %-14s %-14s %-14s" % (key, va_str, vb_str, diff_str))
    print("=" * 72)
    print("提示：Hit@K / MRR / avg_top1_similarity 越大越好；empty_rate 越小越好。\n")
    return 0


def main():
    parser = argparse.ArgumentParser(
        description="离线检索评估：计算 Hit@K / MRR / 平均相似度 / 空结果率，并支持两次实验对比",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )
    parser.add_argument("--dataset", default="dataset.jsonl", help="测试集 jsonl 路径")
    parser.add_argument("--label", default="default", help="实验标签，如 baseline/two-route/rerank")
    parser.add_argument("--top-k", type=int, default=10, help="检索 topK（建议 >=10 以便统计 Hit@10）")
    parser.add_argument("--base-url", default=config.EVAL_BASE_URL, help="后端服务基础地址")
    parser.add_argument(
        "--compare",
        nargs=2,
        metavar=("RESULT_A", "RESULT_B"),
        help="对比两份结果 JSON 文件的指标差值（B 相对 A）",
    )
    args = parser.parse_args()

    if args.compare:
        return compare_results(args.compare[0], args.compare[1])
    return run_eval(args)


if __name__ == "__main__":
    sys.exit(main())
