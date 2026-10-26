# -*- coding: utf-8 -*-
"""文档切分入库脚本：把 markdown 长文档切分成 chunk，向量化后写入 pgvector 知识库。

链路：读取 docs/knowledge/*.md → 递归字符切分 → DashScope embedding(text-embedding-v2)
      → 写入 cv.resume_knowledge_base

支持两种模式（用于对比实验）：
    --mode whole   整篇文档作为一条记录（baseline，不做切分）
    --mode chunk   递归切分成 chunk 后入库（推荐）

切分策略（对齐 README 中描述的 Chunk 策略）：
    分隔符优先级：二级标题(##) → 三级标题(###) → 段落 → 换行 → 句末标点 → 硬切
    chunk_size=512 字符，overlap=64 字符

Key 读取优先级：环境变量 DASHSCOPE_API_KEY > 参数 --api-key > Java 的 application.yml

依赖：requests、psycopg[binary]（建议直接用 python-ai/.venv 运行，依赖已齐全）

用法示例：
    # 只看切分效果，不入库
    python ingest_docs.py --mode chunk --dry-run
    # 整篇入库（baseline）
    python ingest_docs.py --mode whole --clear
    # 切分入库（chunked）
    python ingest_docs.py --mode chunk --clear
"""

import argparse
import json
import os
import re
import sys
import time

import requests

# --------------------------------------------------------------------------- #
# 路径与常量
# --------------------------------------------------------------------------- #
_SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
_PROJECT_ROOT = os.path.dirname(_SCRIPT_DIR)
_DEFAULT_DOCS_DIR = os.path.join(_PROJECT_ROOT, "docs", "knowledge")
_DEFAULT_JAVA_YML = os.path.join(
    _PROJECT_ROOT, "ai-robot-springboot", "src", "main", "resources", "application.yml"
)
_DEFAULT_DATABASE_URL = "postgresql://postgres:postgres@localhost:5432/postgres"
_DASHSCOPE_EMBEDDING_PATH = "/api/v1/services/embeddings/text-embedding/text-embedding"
_EMBEDDING_DIM = 1536  # text-embedding-v2

# 分隔符优先级（从强到弱），最后一项空串表示硬切
_SEPARATORS = ["\n## ", "\n### ", "\n\n", "\n", "。", "！", "？", "；", ""]


# --------------------------------------------------------------------------- #
# Key / 配置读取
# --------------------------------------------------------------------------- #
def load_dashscope_key(explicit_key=None, java_yml_path=None):
    """按优先级获取 DashScope Key：环境变量 → 显式传入 → Java application.yml。"""
    env_key = os.getenv("DASHSCOPE_API_KEY", "").strip()
    if env_key and env_key != "sk-":
        return env_key
    if explicit_key:
        return explicit_key.strip()

    yml_path = java_yml_path or _DEFAULT_JAVA_YML
    if os.path.exists(yml_path):
        key = _parse_key_from_yml(yml_path)
        if key:
            print("[配置] 已从 Java yml 读取 DashScope Key：%s" % os.path.basename(yml_path))
            return key
    raise RuntimeError(
        "未找到 DashScope API Key。请设置环境变量 DASHSCOPE_API_KEY，"
        "或在 %s 中配置 spring.ai.dashscope.api-key" % yml_path
    )


def _parse_key_from_yml(path):
    """从 application.yml 中用正则提取 dashscope.api-key（避免引入 pyyaml 依赖）。"""
    try:
        with open(path, "r", encoding="utf-8") as fp:
            text = fp.read()
    except OSError:
        return ""
    # 匹配 dashscope 节点下第一个 api-key
    m = re.search(r"dashscope:\s*\n(?:.*\n)*?\s*api-key:\s*(\S+)", text)
    if not m:
        return ""
    key = m.group(1).strip().strip('"').strip("'")
    return "" if key in ("", "sk-") else key


# --------------------------------------------------------------------------- #
# 切分：递归字符切分
# --------------------------------------------------------------------------- #
def split_to_atoms(text, chunk_size, separators):
    """按分隔符优先级递归切分，返回长度不超过 chunk_size 的原子片段列表。"""
    if len(text) <= chunk_size:
        return [text] if text.strip() else []
    if not separators:
        # 无法再分：硬切
        return [text[i:i + chunk_size] for i in range(0, len(text), chunk_size)]
    sep = separators[0]
    rest = separators[1:]
    if sep and sep in text:
        parts = text.split(sep)
        atoms = []
        for idx, part in enumerate(parts):
            piece = part if idx == 0 else sep + part  # 把分隔符拼回去，保持语义完整
            atoms.extend(split_to_atoms(piece, chunk_size, rest))
        return atoms
    return split_to_atoms(text, chunk_size, rest)


def merge_atoms(atoms, chunk_size, overlap):
    """把原子片段贪心合并成 chunk（带 overlap）。"""
    chunks = []
    current = ""
    for atom in atoms:
        if not current:
            current = atom
        elif len(current) + len(atom) <= chunk_size:
            current += atom
        else:
            chunks.append(current)
            tail = current[-overlap:] if overlap > 0 else ""
            current = tail + atom
            if len(current) > chunk_size:
                current = current[-chunk_size:]
    if current.strip():
        chunks.append(current)
    return [c.strip() for c in chunks if c.strip()]


def split_document(text, chunk_size=512, overlap=64):
    """文档 → chunk 列表（递归字符切分 + 重叠合并）。"""
    atoms = split_to_atoms(text, chunk_size, _SEPARATORS)
    return merge_atoms(atoms, chunk_size, overlap)


# --------------------------------------------------------------------------- #
# 文档读取
# --------------------------------------------------------------------------- #
def load_documents(docs_dir):
    """读取目录下所有 .md 文档，返回 [(文件名, 内容)]。"""
    if not os.path.isdir(docs_dir):
        raise FileNotFoundError("文档目录不存在：%s" % docs_dir)
    docs = []
    for name in sorted(os.listdir(docs_dir)):
        if not name.lower().endswith(".md"):
            continue
        path = os.path.join(docs_dir, name)
        with open(path, "r", encoding="utf-8") as fp:
            docs.append((name, fp.read()))
    return docs


# --------------------------------------------------------------------------- #
# Embedding（DashScope text-embedding-v2）
# --------------------------------------------------------------------------- #
def embed_texts(texts, api_key, base_url, batch_size=16, timeout=60, text_type="document"):
    """批量向量化，返回与入参等长的向量列表。"""
    url = base_url.rstrip("/") + _DASHSCOPE_EMBEDDING_PATH
    headers = {"Authorization": "Bearer %s" % api_key, "Content-Type": "application/json"}
    vectors = []
    total = len(texts)
    for start in range(0, total, batch_size):
        batch = texts[start:start + batch_size]
        payload = {
            "model": "text-embedding-v2",
            "input": {"texts": batch},
            "parameters": {"text_type": text_type},
        }
        resp = requests.post(url, headers=headers, json=payload, timeout=timeout)
        if resp.status_code != 200:
            raise RuntimeError("DashScope embedding 失败: HTTP %s, %s"
                               % (resp.status_code, resp.text[:300]))
        data = resp.json()
        embeddings = (data.get("output") or {}).get("embeddings") or []
        # 按 text_index 排序，保证顺序与入参一致
        embeddings.sort(key=lambda e: e.get("text_index", 0))
        for item in embeddings:
            vec = item.get("embedding")
            if not vec:
                raise RuntimeError("DashScope 返回的 embedding 为空: %s" % item)
            if len(vec) != _EMBEDDING_DIM:
                raise RuntimeError("embedding 维度异常：期望 %d，实际 %d"
                                   % (_EMBEDDING_DIM, len(vec)))
            vectors.append(vec)
        print("      向量化进度 %d/%d" % (min(start + batch_size, total), total))
    return vectors


# --------------------------------------------------------------------------- #
# 数据库写入
# --------------------------------------------------------------------------- #
def to_vector_str(vec):
    return "[" + ",".join(repr(float(x)) for x in vec) + "]"


def clear_by_source(conn, source):
    """删除指定来源的旧数据，避免重复入库。"""
    with conn.cursor() as cur:
        cur.execute(
            "DELETE FROM cv.resume_knowledge_base WHERE metadata->>'source' = %s",
            (source,),
        )
        deleted = cur.rowcount
    conn.commit()
    return deleted


def insert_rows(conn, rows):
    """批量写入知识库。rows: [(content, category, metadata_json, vector)]"""
    with conn.cursor() as cur:
        for content, category, metadata, vector in rows:
            cur.execute(
                """
                INSERT INTO cv.resume_knowledge_base (content, metadata, embedding, category)
                VALUES (%s, %s::jsonb, %s::vector, %s)
                """,
                (content, metadata, to_vector_str(vector), category),
            )
    conn.commit()


def connect(database_url):
    import psycopg

    conn = psycopg.connect(database_url)
    with conn.cursor() as cur:
        cur.execute("SET search_path TO cv, public")
    conn.commit()
    return conn


# --------------------------------------------------------------------------- #
# 主流程
# --------------------------------------------------------------------------- #
def build_records(docs, mode, chunk_size, overlap, category):
    """把文档转成待入库记录，返回 (records, 统计信息)。"""
    records = []
    stats = []
    for name, text in docs:
        title = _extract_title(text) or name
        if mode == "whole":
            pieces = [text.strip()]
        else:
            pieces = split_document(text, chunk_size, overlap)
        for idx, piece in enumerate(pieces):
            metadata = json.dumps(
                {"source": name, "title": title, "chunk_index": idx, "total_chunks": len(pieces),
                 "mode": mode},
                ensure_ascii=False,
            )
            records.append((piece, category or title, metadata))
        stats.append({
            "file": name,
            "chars": len(text),
            "chunks": len(pieces),
            "avg_chunk_chars": round(sum(len(p) for p in pieces) / max(1, len(pieces)), 1),
        })
    return records, stats


def _extract_title(text):
    """取第一个一级标题作为标题。"""
    for line in text.splitlines():
        line = line.strip()
        if line.startswith("# "):
            return line[2:].strip()
    return ""


def _print_stats(stats):
    print("\n%-42s %8s %8s %10s" % ("文档", "字符数", "chunk数", "平均chunk长"))
    print("-" * 74)
    for s in stats:
        print("%-42s %8d %8d %10s"
              % (s["file"][:40], s["chars"], s["chunks"], s["avg_chunk_chars"]))
    total_chunks = sum(s["chunks"] for s in stats)
    print("-" * 74)
    print("合计：%d 篇文档 → %d 个 chunk\n" % (len(stats), total_chunks))
    return total_chunks


def main():
    parser = argparse.ArgumentParser(
        description="文档切分入库：markdown → chunk → embedding → pgvector",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )
    parser.add_argument("--docs-dir", default=_DEFAULT_DOCS_DIR, help="markdown 文档目录")
    parser.add_argument("--mode", choices=["chunk", "whole"], default="chunk",
                        help="chunk=切分入库；whole=整篇入库(baseline)")
    parser.add_argument("--chunk-size", type=int, default=512, help="chunk 最大字符数")
    parser.add_argument("--overlap", type=int, default=64, help="相邻 chunk 重叠字符数")
    parser.add_argument("--category", default="知识文档", help="写入知识库的 category")
    parser.add_argument("--clear", action="store_true", help="入库前按 source 删除旧数据")
    parser.add_argument("--dry-run", action="store_true", help="只切分不入库，打印效果")
    parser.add_argument("--api-key", default=None, help="DashScope API Key（默认读环境变量/Java yml）")
    parser.add_argument("--java-yml", default=_DEFAULT_JAVA_YML, help="Java application.yml 路径")
    parser.add_argument("--database-url", default=_DEFAULT_DATABASE_URL, help="PostgreSQL 连接串")
    parser.add_argument("--base-url", default="https://dashscope.aliyuncs.com",
                        help="DashScope API 基础地址")
    parser.add_argument("--batch-size", type=int, default=16, help="embedding 批大小")
    args = parser.parse_args()

    docs = load_documents(args.docs_dir)
    if not docs:
        print("未在 %s 找到任何 .md 文档" % args.docs_dir)
        return 1
    print("读取到 %d 篇文档，模式=%s" % (len(docs), args.mode))

    records, stats = build_records(docs, args.mode, args.chunk_size, args.overlap, args.category)
    _print_stats(stats)

    if args.dry_run:
        print("== dry-run 模式，仅打印前 3 个 chunk 预览 ==\n")
        for i, (content, category, _meta) in enumerate(records[:3], 1):
            print("--- chunk %d（%d 字符，category=%s）---" % (i, len(content), category))
            print(content[:200].replace("\n", " ") + ("..." if len(content) > 200 else ""))
            print()
        return 0

    # 向量化
    api_key = load_dashscope_key(args.api_key, args.java_yml)
    print("开始向量化 %d 条文本..." % len(records))
    t0 = time.time()
    vectors = embed_texts([r[0] for r in records], api_key, args.base_url,
                          batch_size=args.batch_size, text_type="document")
    print("向量化完成，耗时 %.1fs\n" % (time.time() - t0))

    # 写库
    conn = connect(args.database_url)
    try:
        if args.clear:
            for name, _ in docs:
                deleted = clear_by_source(conn, name)
                if deleted:
                    print("[清理] %s 删除旧记录 %d 条" % (name, deleted))
        rows = [(r[0], r[1], r[2], vectors[i]) for i, r in enumerate(records)]
        insert_rows(conn, rows)
        with conn.cursor() as cur:
            cur.execute("SELECT count(*) FROM cv.resume_knowledge_base")
            total = cur.fetchone()[0]
    finally:
        conn.close()

    print("入库完成：本次写入 %d 条，知识库现有 %d 条" % (len(rows), total))
    return 0


if __name__ == "__main__":
    sys.exit(main())
