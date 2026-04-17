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
      networkSearch: params.networkSearch ?? false,
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

  /**
   * 普通对话（一次性返回）
   */
  generate(message) {
    return request.get('/ai/generate', { message })
  },

  /**
   * 流式对话（旧版 /ai/* 保留）
   */
  generateStream(message, chatId, options) {
    return request.stream('/ai/generateStream', { message, chatId }, options)
  }
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
  }
}

export default {
  resume: resumeApi,
  chat: chatApi,
  knowledge: knowledgeApi
}
