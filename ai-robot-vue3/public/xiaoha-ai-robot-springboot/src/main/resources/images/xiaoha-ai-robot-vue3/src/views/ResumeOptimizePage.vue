<template>
  <Layout>
    <template #main-content>
      <div class="h-screen flex flex-col overflow-y-auto" ref="chatContainer">
        <div class="flex-1 max-w-4xl mx-auto pb-24 pt-4 px-4 w-full">
          <div v-if="chatList.length === 0" class="flex flex-col items-center justify-center h-full py-16">
            <div class="w-20 h-20 rounded-full bg-gradient-to-br from-green-500 to-teal-600 flex items-center justify-center mb-6">
              <SvgIcon name="resume-optimize" customCss="w-10 h-10 text-white"></SvgIcon>
            </div>
            <h1 class="text-2xl font-bold text-gray-800 mb-2">简历优化助手</h1>
            <p class="text-gray-500 mb-8 text-center">帮你优化简历、准备面试、规划职业发展</p>
            
            <div class="w-full max-w-2xl bg-white rounded-xl shadow-lg p-6 mb-6">
              <div class="mb-4">
                <label class="block text-sm font-medium text-gray-700 mb-2">选择目标岗位</label>
                <select v-model="targetPosition" class="w-full p-3 border border-gray-300 rounded-lg focus:ring-2 focus:ring-green-500 focus:border-green-500">
                  <option value="Java后端">Java 后端开发</option>
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
                  <input 
                    ref="fileInput" 
                    type="file" 
                    accept=".pdf,.docx" 
                    class="hidden"
                    @change="handleFileSelect"
                  />
                  <svg class="w-12 h-12 mx-auto text-gray-400 mb-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12"></path>
                  </svg>
                  <p class="text-gray-600">点击或拖拽上传简历</p>
                  <p class="text-gray-400 text-sm mt-1">支持 PDF、DOCX 格式</p>
                </div>
                <div v-if="uploadedFile" class="mt-3 flex items-center gap-2 p-3 bg-green-50 rounded-lg">
                  <svg class="w-5 h-5 text-green-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"></path>
                  </svg>
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

          <template v-for="(chat, index) in chatList" :key="index">
            <div v-if="chat.role === 'user'" class="flex justify-end mb-4">
              <div class="quesiton-container">
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
                  <button @click="stopGeneration" class="text-sm text-red-500 hover:text-red-700 flex items-center gap-1">
                    <svg class="w-4 h-4" viewBox="0 0 24 24" fill="currentColor">
                      <rect x="6" y="6" width="12" height="12" rx="1" />
                    </svg>
                    停止生成
                  </button>
                </div>
                <StreamMarkdownRender :content="chat.content" />
              </div>
            </div>
          </template>
        </div>

        <div class="sticky max-w-4xl mx-auto bg-white bottom-8 left-0 w-full px-4">
          <div class="flex items-center gap-3 mb-2">
            <button 
              :class="['px-3 py-1 rounded text-sm', enableSearch ? 'bg-green-500 text-white' : 'bg-gray-100 text-gray-600']"
              @click="enableSearch = !enableSearch"
            >
              {{ enableSearch ? '已联网' : '联网搜索' }}
            </button>
          </div>
          <ChatInputBox v-model="message" @sendMessage="sendMessage"
            :loading="optimizing"
            @stopGeneration="stopGeneration" />
        </div>
      </div>
    </template>
  </Layout>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import SvgIcon from '@/components/SvgIcon.vue'
import StreamMarkdownRender from '@/components/StreamMarkdownRender.vue'
import LoadingDots from '@/components/LoadingDots.vue'
import Layout from '@/layouts/Layout.vue'
import ChatInputBox from '@/components/ChatInputBox.vue'
import { fetchEventSource } from '@microsoft/fetch-event-source'

const message = ref('')
const chatContainer = ref(null)
const chatList = ref([])
const enableSearch = ref(false)
const chatId = 'resume-optimize-' + Date.now()
let abortController = null

const targetPosition = ref('Java后端')
const resumeText = ref('')
const additionalRequirements = ref('')
const uploadedFile = ref('')
const optimizing = ref(false)
const fileInput = ref(null)

const triggerFileInput = () => {
  fileInput.value.click()
}

const handleDrop = (e) => {
  const files = e.dataTransfer.files
  if (files.length > 0) {
    processFile(files[0])
  }
}

const handleFileSelect = (e) => {
  const files = e.target.files
  if (files.length > 0) {
    processFile(files[0])
  }
}

const processFile = async (file) => {
  if (!file.name.endsWith('.pdf') && !file.name.endsWith('.docx')) {
    alert('请上传 PDF 或 DOCX 格式的文件')
    return
  }

  uploadedFile.value = file.name
  
  const formData = new FormData()
  formData.append('file', file)

  try {
    const response = await fetch('/resume-optimize/upload', {
      method: 'POST',
      body: formData
    })

    console.log('上传响应状态:', response.status, response.statusText)

    if (!response.ok) {
      const errorText = await response.text()
      console.error('服务器错误响应:', errorText)
      alert('服务器错误: ' + response.status)
      uploadedFile.value = ''
      return
    }

    const contentType = response.headers.get('content-type') || ''
    console.log('响应 Content-Type:', contentType)

    let result
    if (contentType.includes('application/json')) {
      result = await response.json()
      console.log('解析的 JSON 结果:', result)
    } else {
      const text = await response.text()
      console.error('响应不是 JSON:', text)
      alert('服务器响应格式错误')
      uploadedFile.value = ''
      return
    }

    if (result.success) {
      resumeText.value = result.text
    } else {
      alert('文件解析失败: ' + (result.error || '未知错误'))
      uploadedFile.value = ''
    }
  } catch (error) {
    console.error('上传失败:', error)
    alert('上传失败: ' + (error.message || '请稍后重试'))
    uploadedFile.value = ''
  }
}

const startOptimize = async () => {
  if (!resumeText.value.trim()) {
    return
  }

  optimizing.value = true

  chatList.value.push({
    role: 'user',
    content: `请帮我优化简历，目标岗位：${targetPosition.value}${additionalRequirements.value ? '，额外要求：' + additionalRequirements.value : ''}`,
    loading: false
  })

  const aiMessageIndex = chatList.value.length
  chatList.value.push({
    role: 'assistant',
    content: '',
    loading: true
  })

  abortController = new AbortController()

  try {
    await fetchEventSource('/resume-optimize/optimize', {
      method: 'POST',
      signal: abortController.signal,
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        resumeText: resumeText.value,
        targetPosition: targetPosition.value,
        additionalRequirements: additionalRequirements.value
      }),
      onmessage(event) {
        if (event.data === '[DONE]') {
          chatList.value[aiMessageIndex].loading = false
          abortController = null
          optimizing.value = false
          return
        }
        try {
          const data = JSON.parse(event.data)
          if (data.v) {
            chatList.value[aiMessageIndex].content += data.v
          }
        } catch (e) {
          console.error('解析 SSE 数据失败:', e)
        }
      },
      onerror(err) {
        console.error('SSE 连接错误:', err)
        chatList.value[aiMessageIndex].loading = false
        chatList.value[aiMessageIndex].content += '\n\n抱歉，发生了一些错误，请稍后重试。'
        abortController = null
        optimizing.value = false
        throw err
      }
    })
  } catch (error) {
    if (error.name === 'AbortError') {
      console.log('用户中断了生成')
    } else {
      console.error('发送消息失败:', error)
      chatList.value[aiMessageIndex].loading = false
      chatList.value[aiMessageIndex].content += '\n\n抱歉，发送消息失败，请稍后重试。'
    }
    abortController = null
    optimizing.value = false
  }
}

const quickStart = (text) => {
  message.value = text
  sendMessage({ message: text })
}

const stopGeneration = () => {
  if (abortController) {
    abortController.abort()
    abortController = null
    const lastMessage = chatList.value[chatList.value.length - 1]
    if (lastMessage) {
      lastMessage.loading = false
    }
    optimizing.value = false
  }
}

const sendMessage = async (payload) => {
  const msg = message.value
  if (!msg || msg.trim() === '') {
    return
  }

  chatList.value.push({
    role: 'user',
    content: msg,
    loading: false
  })

  const aiMessageIndex = chatList.value.length
  chatList.value.push({
    role: 'assistant',
    content: '',
    loading: true
  })

  message.value = ''

  abortController = new AbortController()

  try {
    await fetchEventSource('/resume-optimize/chat', {
      method: 'POST',
      signal: abortController.signal,
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        message: msg,
        chatId: chatId,
        enableSearch: enableSearch.value
      }),
      onmessage(event) {
        if (event.data === '[DONE]') {
          chatList.value[aiMessageIndex].loading = false
          abortController = null
          return
        }
        try {
          const data = JSON.parse(event.data)
          if (data.v) {
            chatList.value[aiMessageIndex].content += data.v
          }
        } catch (e) {
          console.error('解析 SSE 数据失败:', e)
        }
      },
      onerror(err) {
        console.error('SSE 连接错误:', err)
        chatList.value[aiMessageIndex].loading = false
        chatList.value[aiMessageIndex].content += '\n\n抱歉，发生了一些错误，请稍后重试。'
        abortController = null
        throw err
      }
    })
  } catch (error) {
    if (error.name === 'AbortError') {
      console.log('用户中断了生成')
    } else {
      console.error('发送消息失败:', error)
      chatList.value[aiMessageIndex].loading = false
      chatList.value[aiMessageIndex].content += '\n\n抱歉，发送消息失败，请稍后重试。'
    }
    abortController = null
  }
}

onMounted(() => {
  if (chatContainer.value) {
    chatContainer.value.scrollTop = chatContainer.value.scrollHeight
  }
})
</script>

<style scoped>
.quesiton-container {
  font-size: 16px;
  line-height: 28px;
  color: white;
  padding: 12px 16px;
  box-sizing: border-box;
  white-space: pre-wrap;
  word-break: break-word;
  background-color: #3b82f6;
  border-radius: 8px;
  max-width: 80%;
}
</style>
