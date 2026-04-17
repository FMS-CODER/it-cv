<template>
  <Layout>
    <template #main-content>
      <div class="h-screen flex flex-col overflow-y-auto" ref="chatContainer">
        <div class="flex-1 max-w-4xl mx-auto pb-24 pt-4 px-4 w-full">
          <!-- Tab：单页内切换 -->
          <div class="flex gap-2 mb-4 border-b border-gray-200 pb-2">
            <button
              type="button"
              class="px-4 py-2 rounded-lg text-sm font-medium transition-colors"
              :class="ui.activeTab === 'resume' ? 'bg-green-100 text-green-800' : 'text-gray-600 hover:bg-gray-100'"
              @click="ui.setTab('resume')"
            >
              简历优化
            </button>
            <button
              type="button"
              class="px-4 py-2 rounded-lg text-sm font-medium transition-colors"
              :class="ui.activeTab === 'chat' ? 'bg-blue-100 text-blue-800' : 'text-gray-600 hover:bg-gray-100'"
              @click="ui.setTab('chat')"
            >
              智能对话
            </button>
          </div>

          <!-- ========== 简历优化 ========== -->
          <template v-if="ui.activeTab === 'resume'">
            <div v-if="optimizeList.length === 0" class="flex flex-col items-center justify-center min-h-[60vh] py-8">
              <div class="w-20 h-20 rounded-full bg-gradient-to-br from-green-500 to-teal-600 flex items-center justify-center mb-6">
                <SvgIcon name="resume-optimize" customCss="w-10 h-10 text-white"></SvgIcon>
              </div>
              <h1 class="text-2xl font-bold text-gray-800 mb-2">智能简历</h1>
              <p class="text-gray-500 mb-8 text-center">帮你优化简历、准备面试、规划职业发展</p>

              <div class="w-full max-w-2xl bg-white rounded-xl shadow-lg p-6 mb-6">
                <div class="flex items-center justify-between gap-3 mb-4">
                  <div class="text-sm text-gray-500">
                    知识库：用于沉淀简历优化方法、模板与面试话术（可一键导入示例）
                  </div>
                  <div class="flex items-center gap-2">
                    <button
                      type="button"
                      class="px-3 py-2 rounded-lg text-sm font-medium bg-gray-100 text-gray-700 hover:bg-gray-200 transition-colors"
                      @click="openKbModal"
                    >
                      导入知识库
                    </button>
                  </div>
                </div>

                <div class="mb-4">
                  <label class="block text-sm font-medium text-gray-700 mb-2">选择目标岗位</label>
                  <select v-model="targetPosition" class="w-full p-3 border border-gray-300 rounded-lg focus:ring-2 focus:ring-green-500 focus:border-green-500">
                    <option value="Java 后端">Java 后端开发</option>
                    <option value="前端开发">前端开发</option>
                    <option value="测试工程师">测试工程师</option>
                    <option value="产品经理">产品经理</option>
                    <option value="运营">运营</option>
                    <option value="设计">设计</option>
                  </select>
                </div>

                <div class="mb-4">
                  <label class="block text-sm font-medium text-gray-700 mb-2">上传简历</label>
                  <div
                    class="border-2 border-dashed border-gray-300 rounded-lg p-8 text-center cursor-pointer hover:border-green-500 transition-colors"
                    @dragover.prevent
                    @drop.prevent="handleDrop"
                    @click="triggerFileInput"
                  >
                    <input ref="fileInput" type="file" accept=".pdf,.docx" class="hidden" @change="handleFileSelect" />
                    <svg class="w-12 h-12 mx-auto text-gray-400 mb-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12"></path>
                    </svg>
                    <p class="text-gray-600">点击或拖拽上传简历</p>
                    <p class="text-gray-400 text-sm mt-1">支持 PDF、DOCX 格式</p>
                  </div>
                  <div v-if="uploadedFile" class="mt-3 flex items-center gap-2 p-3 bg-green-50 rounded-lg">
                    <span class="text-green-700 text-sm">{{ uploadedFile }}</span>
                  </div>
                </div>

                <div class="mb-4">
                  <label class="block text-sm font-medium text-gray-700 mb-2">或直接粘贴简历内容</label>
                  <textarea
                    v-model="resumeText"
                    rows="8"
                    class="w-full p-3 border border-gray-300 rounded-lg focus:ring-2 focus:ring-green-500 focus:border-green-500 resize-none"
                    placeholder="请在此粘贴您的简历内容..."
                  ></textarea>
                </div>

                <div class="mb-4">
                  <label class="block text-sm font-medium text-gray-700 mb-2">额外要求（可选）</label>
                  <textarea
                    v-model="additionalRequirements"
                    rows="3"
                    class="w-full p-3 border border-gray-300 rounded-lg focus:ring-2 focus:ring-green-500 focus:border-green-500 resize-none"
                    placeholder="例如：突出项目管理经验、强调技术深度..."
                  ></textarea>
                </div>

                <button
                  type="button"
                  @click="startOptimize"
                  :disabled="(!resumeText && !uploadedFile) || optimizing"
                  class="w-full py-3 bg-gradient-to-r from-green-500 to-teal-600 text-white rounded-lg font-medium hover:from-green-600 hover:to-teal-700 transition-all disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2"
                >
                  <svg v-if="optimizing" class="w-5 h-5 animate-spin" fill="none" viewBox="0 0 24 24">
                    <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                    <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                  </svg>
                  {{ optimizing ? '正在优化...' : '开始优化简历' }}
                </button>
              </div>

              <div class="grid grid-cols-1 md:grid-cols-2 gap-3 w-full max-w-2xl">
                <div @click="quickStart('帮我优化 Java 后端开发的简历')" class="p-4 bg-blue-50 rounded-lg cursor-pointer hover:bg-blue-100 transition-colors">
                  <p class="text-blue-700">📝 简历优化建议</p>
                </div>
                <div @click="quickStart('前端开发面试怎么准备')" class="p-4 bg-green-50 rounded-lg cursor-pointer hover:bg-green-100 transition-colors">
                  <p class="text-green-700">🎤 面试准备建议</p>
                </div>
                <div @click="quickStart('中级工程师怎么提升')" class="p-4 bg-purple-50 rounded-lg cursor-pointer hover:bg-purple-100 transition-colors">
                  <p class="text-purple-700">🚀 职业发展建议</p>
                </div>
                <div @click="quickStart('给我一份简历检查清单')" class="p-4 bg-orange-50 rounded-lg cursor-pointer hover:bg-orange-100 transition-colors">
                  <p class="text-orange-700">✅ 简历自查清单</p>
                </div>
              </div>
            </div>

            <!-- 简历优化对话流 -->
            <template v-else>
              <template v-for="(chat, index) in optimizeList" :key="'opt-' + index">
                <div v-if="chat.role === 'user'" class="flex justify-end mb-4">
                  <div class="question-container">
                    <p>{{ chat.content }}</p>
                  </div>
                </div>
                <div v-else class="flex mb-4">
                  <div class="flex-shrink-0 mr-3">
                    <div class="w-8 h-8 rounded-full flex items-center justify-center border border-gray-200 bg-gradient-to-br from-green-500 to-teal-600">
                      <SvgIcon name="resume-optimize" customCss="w-5 h-5 text-white"></SvgIcon>
                    </div>
                  </div>
                  <div class="p-1 mb-2 max-w-[90%]">
                    <div class="flex items-center gap-2 mb-2" v-if="chat.loading">
                      <LoadingDots />
                      <button type="button" @click="stopGeneration" class="text-sm text-red-500 hover:text-red-700 flex items-center gap-1">
                        停止生成
                      </button>
                    </div>
                    <StreamMarkdownRender :content="chat.content" />
                  </div>
                </div>
              </template>
            </template>
          </template>

          <!-- ========== 智能对话（PostgreSQL 历史） ========== -->
          <template v-else>
            <div v-if="chatSession.chatMessages.length === 0" class="flex flex-col items-center justify-center min-h-[50vh] py-8 text-center text-gray-500">
              <p class="mb-6">在下方输入框开始对话，历史记录保存在侧边栏。</p>
              <div class="grid grid-cols-1 md:grid-cols-2 gap-3 w-full max-w-2xl">
                <div @click="quickStart('帮我优化 Java 后端开发的简历')" class="p-4 bg-blue-50 rounded-lg cursor-pointer hover:bg-blue-100 transition-colors">
                  <p class="text-blue-700">📝 简历优化建议</p>
                </div>
                <div @click="quickStart('前端开发面试怎么准备')" class="p-4 bg-green-50 rounded-lg cursor-pointer hover:bg-green-100 transition-colors">
                  <p class="text-green-700">🎤 面试准备建议</p>
                </div>
              </div>
            </div>

            <template v-else>
              <template v-for="(chat, index) in chatSession.chatMessages" :key="'chat-' + index">
                <div v-if="chat.role === 'user'" class="flex justify-end mb-4">
                  <div class="question-container">
                    <p>{{ chat.content }}</p>
                  </div>
                </div>
                <div v-else class="flex mb-4">
                  <div class="flex-shrink-0 mr-3">
                    <div class="w-8 h-8 rounded-full flex items-center justify-center border border-gray-200 bg-gradient-to-br from-blue-500 to-indigo-600">
                      <SvgIcon name="resume-optimize" customCss="w-5 h-5 text-white"></SvgIcon>
                    </div>
                  </div>
                  <div class="p-1 mb-2 max-w-[90%]">
                    <div v-if="chat.reasoning" class="text-sm text-gray-500 mb-2 whitespace-pre-wrap border-l-2 border-gray-300 pl-2">
                      {{ chat.reasoning }}
                    </div>
                    <div class="flex items-center gap-2 mb-2" v-if="chat.loading">
                      <LoadingDots />
                      <button type="button" @click="stopGeneration" class="text-sm text-red-500 hover:text-red-700">停止生成</button>
                    </div>
                    <StreamMarkdownRender :content="chat.content" />
                  </div>
                </div>
              </template>
            </template>
          </template>
        </div>

        <!-- 底部输入：仅在「智能对话」Tab 显示 -->
        <div v-if="ui.activeTab === 'chat'" class="sticky max-w-4xl mx-auto bg-white bottom-8 left-0 w-full px-4">
          <div class="flex items-center gap-3 mb-2">
            <button
              type="button"
              :class="['px-3 py-1 rounded text-sm', enableSearch ? 'bg-green-500 text-white' : 'bg-gray-100 text-gray-600']"
              @click="enableSearch = !enableSearch"
            >
              {{ enableSearch ? '已联网' : '联网搜索' }}
            </button>
          </div>
          <ChatInputBox v-model="message" @sendMessage="sendMessage" :loading="chatSending" @stopGeneration="stopGeneration" />
        </div>

        <!-- 导入知识库弹窗 -->
        <a-modal
          v-model:open="kbModalOpen"
          title="导入知识库"
          ok-text="关闭"
          cancel-text="取消"
          :ok-button-props="{ disabled: true }"
          :cancel-button-props="{ disabled: true }"
          :footer="null"
        >
          <div class="space-y-3">
            <div class="text-sm text-gray-500">
              你可以一键导入典型示例数据，或者把自己的内容作为一条知识导入。
            </div>

            <div class="flex items-center gap-2">
              <button
                type="button"
                class="px-3 py-2 rounded-lg text-sm font-medium bg-blue-600 text-white hover:bg-blue-700 disabled:opacity-60"
                :disabled="kbSubmitting"
                @click="importKbSamples"
              >
                一键导入示例
              </button>
            </div>

            <div class="h-px bg-gray-200"></div>

            <div class="text-sm font-medium text-gray-700">自定义导入（单条）</div>
            <a-input v-model:value="kbCategory" placeholder="分类（例如：简历通用/项目描述/面试）" />
            <a-textarea v-model:value="kbCustomText" :rows="6" placeholder="粘贴你想导入到知识库的内容..." />
            <button
              type="button"
              class="px-3 py-2 rounded-lg text-sm font-medium bg-green-600 text-white hover:bg-green-700 disabled:opacity-60"
              :disabled="kbSubmitting"
              @click="importKbCustom"
            >
              导入这条内容
            </button>
          </div>
        </a-modal>
      </div>
    </template>
  </Layout>
</template>

<script setup>
import { ref, onMounted, onUnmounted, watch } from 'vue'
import { message as antMessage } from 'ant-design-vue'
import SvgIcon from '@/components/SvgIcon.vue'
import StreamMarkdownRender from '@/components/StreamMarkdownRender.vue'
import LoadingDots from '@/components/LoadingDots.vue'
import Layout from '@/layouts/Layout.vue'
import ChatInputBox from '@/components/ChatInputBox.vue'
import { resumeApi, chatApi, knowledgeApi } from '@/api'
import { useUiStore } from '@/stores/ui'
import { useChatSessionStore } from '@/stores/chatSession'

const ui = useUiStore()
const chatSession = useChatSessionStore()

const message = ref('')
const chatContainer = ref(null)
const optimizeList = ref([])
const enableSearch = ref(false)
let abortController = null
const chatSending = ref(false)

const targetPosition = ref('Java 后端')
const resumeText = ref('')
const additionalRequirements = ref('')
const uploadedFile = ref('')
const optimizing = ref(false)
const fileInput = ref(null)

const kbModalOpen = ref(false)
const kbSubmitting = ref(false)
const kbCategory = ref('简历通用')
const kbCustomText = ref('')

const triggerFileInput = () => fileInput.value?.click()

const handleDrop = (e) => {
  const files = e.dataTransfer.files
  if (files.length > 0) processFile(files[0])
}

const handleFileSelect = (e) => {
  const files = e.target.files
  if (files.length > 0) processFile(files[0])
}

const processFile = async (file) => {
  if (!file.name.endsWith('.pdf') && !file.name.endsWith('.docx')) {
    antMessage.warning('请上传 PDF 或 DOCX 格式的文件')
    return
  }
  const maxSize = 50 * 1024 * 1024
  if (file.size > maxSize) {
    antMessage.warning('文件大小不能超过 50MB')
    return
  }
  uploadedFile.value = file.name
  try {
    const result = await resumeApi.uploadResume(file)
    if (result.success) {
      resumeText.value = result.text
    } else {
      antMessage.error('文件解析失败：' + (result.error || '未知错误'))
      uploadedFile.value = ''
    }
  } catch (error) {
    console.error(error)
    antMessage.error('上传失败：' + (error.message || '请稍后重试'))
    uploadedFile.value = ''
  }
}

const startOptimize = async () => {
  if (!resumeText.value.trim()) return
  optimizing.value = true
  optimizeList.value.push({
    role: 'user',
    content: `请帮我优化简历，目标岗位：${targetPosition.value}${additionalRequirements.value ? '，额外要求：' + additionalRequirements.value : ''}`,
    loading: false
  })
  const aiMessageIndex = optimizeList.value.length
  optimizeList.value.push({ role: 'assistant', content: '', loading: true })
  abortController = new AbortController()
  const localController = abortController
  try {
    await resumeApi.optimizeResume(
      {
        resumeText: resumeText.value,
        targetPosition: targetPosition.value,
        additionalRequirements: additionalRequirements.value
      },
      {
        signal: abortController.signal,
        onmessage(event) {
          try {
            const data = JSON.parse(event.data)
            if (data.v) optimizeList.value[aiMessageIndex].content += data.v
          } catch (e) {
            console.error(e)
          }
        },
        onclose() {
          // 以连接关闭作为“正常结束”，不依赖后端发送 [DONE]
          optimizeList.value[aiMessageIndex].loading = false
          optimizing.value = false
          // 关键：close 后主动 abort，阻止 fetch-event-source 因可见性变化/策略再次 create 重连
          try { localController.abort() } catch (_) {}
          if (abortController === localController) abortController = null
        },
        onerror(err) {
          // fetch-event-source 默认会重连；AbortError 若不 throw 会被当成可重试错误导致“重复输出”
          if (err && err.name === 'AbortError') throw err
          console.error(err)
          optimizeList.value[aiMessageIndex].loading = false
          optimizeList.value[aiMessageIndex].content += '\n\n抱歉，发生了一些错误，请稍后重试。'
          abortController = null
          optimizing.value = false
          throw err
        }
      }
    )
  } catch (error) {
    if (error.name !== 'AbortError') {
      optimizeList.value[aiMessageIndex].loading = false
      optimizeList.value[aiMessageIndex].content += '\n\n抱歉，发送失败，请稍后重试。'
    }
    abortController = null
    optimizing.value = false
  } finally {
    if (optimizeList.value[aiMessageIndex]) optimizeList.value[aiMessageIndex].loading = false
    optimizing.value = false
  }
}

const quickStart = (text) => {
  ui.setTab('chat')
  message.value = text
  sendMessage({ message: text })
}

const stopGeneration = () => {
  if (abortController) {
    abortController.abort()
    abortController = null
    optimizing.value = false
    chatSending.value = false
  }
}

const sendMessage = async (payload) => {
  const msg = (payload && payload.message) != null ? payload.message : message.value
  if (!msg || String(msg).trim() === '') return

  ui.setTab('chat')
  const text = String(msg).trim()
  message.value = ''

  let uuid = chatSession.currentUuid
  if (!uuid) {
    try {
      const res = await chatApi.newChat(text)
      if (!res.success) {
        antMessage.error(res.message || '创建会话失败')
        return
      }
      uuid = res.data.uuid
      chatSession.currentUuid = uuid
      await chatSession.fetchSessions()
    } catch (e) {
      console.error(e)
      antMessage.error(e.message || '创建会话失败')
      return
    }
  }

  chatSession.chatMessages.push({ role: 'user', content: text, loading: false })
  chatSession.chatMessages.push({ role: 'assistant', content: '', loading: true, reasoning: '' })
  const aiIndex = chatSession.chatMessages.length - 1

  abortController = new AbortController()
  const localController = abortController
  chatSending.value = true

  try {
    await chatApi.completionStream(
      { message: text, chatId: uuid, networkSearch: enableSearch.value },
      {
        signal: abortController.signal,
        onmessage(event) {
          try {
            const data = JSON.parse(event.data)
            if (data.reasoning) {
              chatSession.chatMessages[aiIndex].reasoning += data.reasoning
            }
            if (data.v) {
              chatSession.chatMessages[aiIndex].content += data.v
            }
          } catch (e) {
            console.error('解析 SSE 失败', e)
          }
        },
        onclose() {
          // 以连接关闭作为“正常结束”，不依赖后端发送 [DONE]
          chatSession.chatMessages[aiIndex].loading = false
          chatSending.value = false
          chatSession.fetchSessions()
          // 关键：close 后主动 abort，阻止 fetch-event-source 因可见性变化/策略再次 create 重连
          try { localController.abort() } catch (_) {}
          if (abortController === localController) abortController = null
        },
        onerror(err) {
          // fetch-event-source 默认会重连；AbortError 若不 throw 会被当成可重试错误导致“重复输出”
          if (err && err.name === 'AbortError') throw err
          console.error(err)
          chatSession.chatMessages[aiIndex].loading = false
          chatSession.chatMessages[aiIndex].content += '\n\n抱歉，发生了一些错误，请稍后重试。'
          abortController = null
          chatSending.value = false
          throw err
        }
      }
    )
  } catch (error) {
    if (error.name !== 'AbortError') {
      chatSession.chatMessages[aiIndex].loading = false
      chatSession.chatMessages[aiIndex].content += '\n\n抱歉，发送消息失败，请稍后重试。'
    }
    abortController = null
    chatSending.value = false
  } finally {
    if (chatSession.chatMessages[aiIndex]) {
      chatSession.chatMessages[aiIndex].loading = false
    }
    chatSending.value = false
    chatSession.fetchSessions()
  }
}

const scrollToBottom = async () => {
  await new Promise((r) => setTimeout(r, 0))
  if (chatContainer.value) {
    chatContainer.value.scrollTop = chatContainer.value.scrollHeight
  }
}

watch(
  () => chatSession.chatMessages,
  () => scrollToBottom(),
  { deep: true }
)
watch(optimizeList, () => scrollToBottom(), { deep: true })

onMounted(() => {
  chatSession.fetchSessions()
  scrollToBottom()
})

onUnmounted(() => {
  // 页面切换/组件卸载时，主动取消未完成的 SSE，避免后台继续生成
  stopGeneration()
})

const openKbModal = () => {
  kbModalOpen.value = true
}

const importKbSamples = async () => {
  kbSubmitting.value = true
  try {
    const res = await knowledgeApi.importSamples()
    if (!res.success) {
      antMessage.error(res.message || '导入失败')
      return
    }
    antMessage.success(`已导入示例数据：${res.data?.imported ?? 0} 条`)
    kbModalOpen.value = false
  } catch (e) {
    console.error(e)
    antMessage.error(e.message || '导入失败')
  } finally {
    kbSubmitting.value = false
  }
}

const importKbCustom = async () => {
  const text = String(kbCustomText.value || '').trim()
  if (!text) {
    antMessage.warning('请先输入要导入的内容')
    return
  }
  kbSubmitting.value = true
  try {
    const res = await knowledgeApi.importBatch({
      items: [{ content: text, category: kbCategory.value, metadata: '{"来源":"前端手动导入"}' }]
    })
    if (!res.success) {
      antMessage.error(res.message || '导入失败')
      return
    }
    antMessage.success(`导入成功：${res.data?.imported ?? 0} 条`)
    kbCustomText.value = ''
    kbModalOpen.value = false
  } catch (e) {
    console.error(e)
    antMessage.error(e.message || '导入失败')
  } finally {
    kbSubmitting.value = false
  }
}
</script>

<style>
.question-container {
  background: linear-gradient(to right, #22c55e, #0d9488);
  color: white;
  padding: 0.5rem 1rem;
  border-radius: 0.75rem;
  max-width: 80%;
}
</style>
