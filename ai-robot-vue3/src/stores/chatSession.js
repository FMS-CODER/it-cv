import { defineStore } from 'pinia'
import { chatApi } from '@/api'

/**
 * 与后端 /chat 接口配合：会话列表 + 当前会话消息（PostgreSQL 持久化）
 */
export const useChatSessionStore = defineStore('chatSession', {
  state: () => ({
    /** 侧边栏会话列表（来自 /chat/list） */
    sessions: [],
    /** 当前会话 UUID（对应 t_chat.uuid） */
    currentUuid: null,
    /** 当前展示的消息气泡 */
    chatMessages: [],
    loadingSessions: false
  }),
  actions: {
    async fetchSessions() {
      this.loadingSessions = true
      try {
        const res = await chatApi.listChatPage({ current: 1, size: 50 })
        if (res.success && Array.isArray(res.data)) {
          this.sessions = res.data
        }
      } finally {
        this.loadingSessions = false
      }
    },

    /**
     * 打开历史会话并加载消息
     */
    async openSession(uuid) {
      this.currentUuid = uuid
      const res = await chatApi.listMessagePage({ chatId: uuid, current: 1, size: 200 })
      if (!res.success || !Array.isArray(res.data)) {
        this.chatMessages = []
        return
      }
      this.chatMessages = res.data.map((row) => ({
        role: row.role === 'user' || row.role === 'USER' ? 'user' : 'assistant',
        content: row.content || '',
        loading: false,
        reasoning: ''
      }))
    },

    /** 新对话：清空当前会话（不删库） */
    startNewChat() {
      this.currentUuid = null
      this.chatMessages = []
    },

    async deleteSession(uuid) {
      const res = await chatApi.deleteChat({ uuid })
      if (!res.success) {
        throw new Error(res.message || '删除失败')
      }
      await this.fetchSessions()
      if (this.currentUuid === uuid) {
        this.startNewChat()
      }
    },

    async renameSession(id, summary) {
      const res = await chatApi.renameChat({ id, summary })
      if (!res.success) {
        throw new Error(res.message || '重命名失败')
      }
      await this.fetchSessions()
    }
  }
})
