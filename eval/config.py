# -*- coding: utf-8 -*-
"""全局配置：统一从环境变量读取，提供合理默认值。

环境变量说明：
    EVAL_BASE_URL        后端服务基础地址，默认 http://localhost:8080
    DEEPSEEK_API_KEY     DeepSeek（OpenAI 兼容）API Key，必填
    DEEPSEEK_BASE_URL    DeepSeek OpenAI 兼容地址，默认 https://api.deepseek.com
    JUDGE_MODEL          裁判/生成所用模型名，默认 deepseek-chat
    EVAL_HTTP_TIMEOUT    普通 HTTP 请求超时（秒），默认 60
    EVAL_STREAM_TIMEOUT  SSE 流式请求超时（秒），默认 300
"""

import os

# 后端服务基础地址（本项目的 Spring Boot 服务）
EVAL_BASE_URL = os.getenv("EVAL_BASE_URL", "http://localhost:8080").rstrip("/")

# DeepSeek（OpenAI 兼容）配置
DEEPSEEK_API_KEY = os.getenv("DEEPSEEK_API_KEY", "")
DEEPSEEK_BASE_URL = os.getenv("DEEPSEEK_BASE_URL", "https://api.deepseek.com").rstrip("/")
JUDGE_MODEL = os.getenv("JUDGE_MODEL", "deepseek-chat")

# 超时配置（秒）
HTTP_TIMEOUT = float(os.getenv("EVAL_HTTP_TIMEOUT", "60"))
STREAM_TIMEOUT = float(os.getenv("EVAL_STREAM_TIMEOUT", "300"))

# 结果输出目录：eval/results/
RESULTS_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "results")


def ensure_results_dir():
    """确保结果目录存在，返回目录路径。"""
    os.makedirs(RESULTS_DIR, exist_ok=True)
    return RESULTS_DIR


def require_deepseek_api_key():
    """校验 DeepSeek API Key 是否存在，缺失时抛出带提示的异常。"""
    if not DEEPSEEK_API_KEY:
        raise RuntimeError(
            "未检测到 DEEPSEEK_API_KEY 环境变量。请先设置，例如：\n"
            "  Windows PowerShell:  $env:DEEPSEEK_API_KEY=\"sk-xxxx\"\n"
            "  Linux / macOS:       export DEEPSEEK_API_KEY=sk-xxxx"
        )
    return DEEPSEEK_API_KEY
