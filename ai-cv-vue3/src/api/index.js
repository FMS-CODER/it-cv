import request from './request'

/** 与后端 spring.ai.deepseek.chat.options.model 保持一致 */
export const CHAT_MODEL_NAME = 'deepseek-reasoner'

/**
 * 简历优化相关 API
 */
export const resumeApi = {
  /**
   * 上传简历文件
   * @param {File} file - 简历文件
   * @returns {Promise<{success: boolean, text: string, error?: string}>}
   */
  uploadResume(file) {
    return request.upload('/resume-optimize/upload', file)
  },

  /**
   * 优化简历
   * @param {Object} params - 优化参数
   * @param {string} params.resumeText - 简历文本
   * @param {string} params.targetPosition - 目标岗位
   * @param {string} params.additionalRequirements - 额外要求
   * @param {boolean} [params.knowledgeRag] - 是否启用知识库双路 RAG
   * @param {string} [params.kbCategory] - 知识库分类过滤
   * @param {number} [params.kbTopK] - 合并检索条数上限
   * @param {Function} options.onmessage - SSE 消息回调
   * @param {Function} options.onerror - SSE 错误回调
   * @param {AbortSignal} options.signal - 取消信号
   */
  optimizeResume(params, options) {
    return request.stream('/resume-optimize/optimize', params, options)
  }
}

/**
 * PostgreSQL 持久化对话（与后端 ChatController /chat 一致）
 */
export const chatApi = {
  /**
   * 新建会话（写入 t_chat）
   * @param {string} message - 首条消息，用于生成摘要
   */
  newChat(message) {
    return request.post('/chat/new', { message })
  },

  /**
   * 流式对话（SSE，完成后写入 t_chat_message）
   */
  completionStream(params, options) {
    return request.stream('/chat/completion', {
      message: params.message,
      chatId: params.chatId,
      modelName: params.modelName ?? CHAT_MODEL_NAME,
      temperature: params.temperature ?? 0.8
    }, options)
  },

  /** 会话分页列表 */
  listChatPage({ current = 1, size = 50 } = {}) {
    return request.post('/chat/list', { current, size })
  },

  /** 某会话消息分页 */
  listMessagePage({ chatId, current = 1, size = 50 }) {
    return request.post('/chat/message/list', { chatId, current, size })
  },

  /** 重命名会话摘要 */
  renameChat({ id, summary }) {
    return request.post('/chat/summary/rename', { id, summary })
  },

  /** 删除会话及消息 */
  deleteChat({ uuid }) {
    return request.post('/chat/delete', { uuid })
  },
}

/**
 * 简历知识库（resume_knowledge_base）
 */
export const knowledgeApi = {
  /**
   * 一键导入典型示例数据
   */
  importSamples() {
    return request.post('/resume-kb/import/samples', {})
  },

  /**
   * 自定义批量导入
   * @param {{items: Array<{content: string, category?: string, metadata?: string}>}} payload
   */
  importBatch(payload) {
    return request.post('/resume-kb/import', payload)
  },

  /**
   * 分页查询
   */
  listPage({ current = 1, size = 20, category = '' } = {}) {
    return request.post('/resume-kb/page/list', { current, size, category })
  },

  /**
   * 回填历史数据的向量字段（扫描 embedding 为空的记录批量调用 DashScope）
   * @param {number} [batchSize]
   */
  refillEmbeddings(batchSize) {
    const qs = batchSize ? `?batchSize=${batchSize}` : ''
    return request.post(`/resume-kb/embedding/refill${qs}`, {})
  },

  /**
   * 基于 DashScope 向量 + pgvector 余弦距离的 Top-K 相似度检索
   * @param {{query: string, topK?: number, category?: string}} payload
   */
  search(payload) {
    return request.post('/resume-kb/search', payload)
  },

  /**
   * 更新单条知识并触发后端重算 embedding
   * @param {{id:number, content:string, category?:string, metadata?:object|string}} payload
   */
  update(payload) {
    return request.post('/resume-kb/update', payload)
  }
}

/**
 * Agent 指标验证
 */
export const metricsApi = {
  baseline(payload = {}) {
    return request.post('/agent-metrics/baseline', payload)
  },
  recentRuns(payload = {}) {
    return request.post('/agent-metrics/runs/recent', payload)
  },
  ragBenchmark(payload = {}) {
    return request.post('/agent-metrics/benchmark/rag', payload)
  },
  webBenchmark(payload = {}) {
    return request.post('/agent-metrics/benchmark/web', payload)
  },
  chatBenchmark(payload = {}) {
    return request.post('/agent-metrics/benchmark/chat', payload)
  },
  report() {
    return request.post('/agent-metrics/report', {})
  }
}

export default {
  resume: resumeApi,
  chat: chatApi,
  knowledge: knowledgeApi,
  metrics: metricsApi
}
