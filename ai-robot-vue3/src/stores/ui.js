import { defineStore } from 'pinia'

/**
 * 单页内 Tab：简历优化 / 智能对话 / 知识库
 */
export const useUiStore = defineStore('ui', {
  state: () => ({
    /** 'resume' | 'chat' | 'kb' */
    activeTab: 'resume'
  }),
  actions: {
    setTab(tab) {
      this.activeTab = tab
    }
  }
})
