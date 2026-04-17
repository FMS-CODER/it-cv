<template>
  <div
    :class="sidebarOpen ? 'translate-x-0' : '-translate-x-full'"
    class="w-64 bg-[#f9fbff] border-r border-gray-200 fixed left-0 top-0 h-full transition-transform duration-300 ease-in-out z-10 overflow-y-auto flex flex-col"
  >
    <div class="flex items-center justify-center p-4 cursor-pointer shrink-0" @click="goResume">
      <SvgIcon name="ai-robot-logo" customCss="w-8 h-8 text-gray-700 mr-3" />
      <span class="text-2xl font-bold font-sans tracking-wide text-gray-800">智能简历</span>
    </div>

    <button
      type="button"
      @click="onNewChat"
      class="mx-auto mb-4 px-6 py-2 text-white rounded-xl transition-colors new-chat-btn w-fit cursor-pointer shrink-0"
    >
      <SvgIcon name="new-chat" customCss="w-6 h-6 mr-1.5 inline text-[#4d6bfe]" />
      开启新对话
    </button>

    <div class="h-px border-b border-gray-200 mx-4 shrink-0" />

    <div class="px-2 py-3 flex-1 overflow-y-auto">
      <p class="text-xs text-gray-400 px-2 mb-2">历史对话</p>
      <div v-if="chatSession.loadingSessions" class="text-sm text-gray-400 px-2">加载中...</div>
      <ul v-else class="space-y-1">
        <li
          v-for="s in chatSession.sessions"
          :key="s.uuid"
          class="group rounded-lg px-2 py-2 cursor-pointer hover:bg-blue-50 flex items-start justify-between gap-1"
          :class="chatSession.currentUuid === s.uuid ? 'bg-blue-100' : ''"
          @click="openHistory(s.uuid)"
        >
          <span class="text-sm text-gray-800 line-clamp-2 flex-1">{{ s.summary || '未命名对话' }}</span>
          <span class="flex shrink-0 gap-0.5 opacity-0 group-hover:opacity-100">
            <button
              type="button"
              class="p-1 text-gray-500 hover:text-blue-600"
              title="重命名"
              @click.stop="openRename(s)"
            >
              ✎
            </button>
            <a-popconfirm title="确定删除该对话？" ok-text="删除" cancel-text="取消" @confirm="() => remove(s.uuid)">
              <button type="button" class="p-1 text-gray-500 hover:text-red-600" title="删除" @click.stop>✕</button>
            </a-popconfirm>
          </span>
        </li>
      </ul>
      <p v-if="!chatSession.loadingSessions && chatSession.sessions.length === 0" class="text-sm text-gray-400 px-2">
        暂无历史，去「智能对话」发一条吧
      </p>
    </div>

    <a-modal v-model:open="renameOpen" title="重命名对话" ok-text="保存" cancel-text="取消" :confirm-loading="renameSubmitting" @ok="submitRename">
      <a-input v-model:value="renameSummary" placeholder="摘要标题" />
    </a-modal>
  </div>

  <a-tooltip placement="bottom">
    <template #title>
      <span>{{ sidebarOpen ? '收缩边栏' : '打开边栏' }}</span>
    </template>
    <button
      :class="sidebarOpen ? 'left-64' : 'left-0'"
      type="button"
      @click="toggleSidebar"
      class="fixed top-4 z-20 bg-white border border-gray-200 rounded-r-lg p-2 transition-all duration-300"
    >
      <SvgIcon
        :name="sidebarOpen ? 'sidebar-open' : 'sidebar-close'"
        :customCss="sidebarOpen ? 'w-6 h-6 text-gray-400' : 'w-7 h-7 text-gray-400'"
      />
    </button>
  </a-tooltip>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import SvgIcon from '@/components/SvgIcon.vue'
import { useChatSessionStore } from '@/stores/chatSession'
import { useUiStore } from '@/stores/ui'

const props = defineProps({
  sidebarOpen: { type: Boolean, required: true }
})

const emit = defineEmits(['toggle-sidebar'])

const chatSession = useChatSessionStore()
const ui = useUiStore()

const renameOpen = ref(false)
const renameId = ref(null)
const renameSummary = ref('')
const renameSubmitting = ref(false)

onMounted(() => {
  chatSession.fetchSessions()
})

const toggleSidebar = () => emit('toggle-sidebar')

const goResume = () => {
  ui.setTab('resume')
}

const onNewChat = () => {
  chatSession.startNewChat()
  ui.setTab('chat')
}

const openHistory = async (uuid) => {
  ui.setTab('chat')
  await chatSession.openSession(uuid)
}

const remove = async (uuid) => {
  try {
    await chatSession.deleteSession(uuid)
    message.success('已删除')
  } catch (e) {
    message.error(e.message || '删除失败')
  }
}

const openRename = (s) => {
  renameId.value = s.id
  renameSummary.value = s.summary || ''
  renameOpen.value = true
}

const submitRename = async () => {
  if (!renameSummary.value.trim()) {
    message.warning('请输入摘要')
    return Promise.reject()
  }
  renameSubmitting.value = true
  try {
    await chatSession.renameSession(renameId.value, renameSummary.value.trim())
    renameOpen.value = false
    message.success('已保存')
  } catch (e) {
    message.error(e.message || '保存失败')
    return Promise.reject(e)
  } finally {
    renameSubmitting.value = false
  }
}
</script>

<style scoped>
.new-chat-btn {
  background-color: rgb(219 234 254);
  color: #4d6bfe;
}
.new-chat-btn:hover {
  background-color: #c6dcf8;
}
</style>
