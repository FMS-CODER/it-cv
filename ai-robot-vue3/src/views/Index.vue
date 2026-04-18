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
            <button
              type="button"
              class="px-4 py-2 rounded-lg text-sm font-medium transition-colors"
              :class="ui.activeTab === 'kb' ? 'bg-orange-100 text-orange-800' : 'text-gray-600 hover:bg-gray-100'"
              @click="openKbTab"
            >
              知识库
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
                    知识库：用于沉淀简历优化方法、模板与面试话术（可一键导入或手动新增）
                  </div>
                  <div class="flex items-center gap-2">
                    <button
                      type="button"
                      class="px-3 py-2 rounded-lg text-sm font-medium bg-gray-100 text-gray-700 hover:bg-gray-200 transition-colors"
                      @click="openKbTab"
                    >
                      管理知识库
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

                <div class="mb-4 flex flex-wrap items-center gap-4 p-3 bg-amber-50 rounded-lg border border-amber-100">
                  <label class="flex items-center gap-2 text-sm cursor-pointer select-none">
                    <input v-model="enableResumeKbRag" type="checkbox" class="rounded border-gray-300" />
                    <span>启用知识库 RAG（输入：岗位+简历摘要 · 输出：岗位+要求+写作要点）</span>
                  </label>
                  <div v-if="enableResumeKbRag" class="flex items-center gap-2">
                    <span class="text-sm text-gray-600">检索分类</span>
                    <select v-model="resumeKbCategory" class="text-sm border border-gray-300 rounded-lg px-2 py-1 bg-white">
                      <option value="">全部分类</option>
                      <option v-for="c in kbCategoryOptions" :key="'rkbc-' + c" :value="c">{{ c }}</option>
                    </select>
                  </div>
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

          <!-- ========== 知识库管理 ========== -->
          <template v-else-if="ui.activeTab === 'kb'">
            <div class="mb-6">
              <div class="flex items-center justify-between mb-4">
                <div>
                  <h2 class="text-xl font-bold text-gray-800">简历知识库</h2>
                  <p class="text-sm text-gray-500 mt-1">
                    沉淀你的简历模板 / 项目描述话术 / 面试要点，供 AI 在对话时检索引用
                  </p>
                </div>
                <div class="flex items-center gap-2">
                  <button
                    type="button"
                    class="px-3 py-2 rounded-lg text-sm font-medium bg-gray-100 text-gray-700 hover:bg-gray-200 transition-colors"
                    :disabled="kbSubmitting"
                    @click="importKbSamples"
                  >
                    一键导入示例
                  </button>
                  <button
                    type="button"
                    class="px-3 py-2 rounded-lg text-sm font-medium bg-gray-100 text-gray-700 hover:bg-gray-200 transition-colors"
                    :disabled="kbRefilling"
                    @click="refillKbEmbeddings"
                    title="为未生成向量的历史数据调用 DashScope 批量回填"
                  >
                    {{ kbRefilling ? '回填中...' : '回填向量' }}
                  </button>
                  <button
                    type="button"
                    class="px-3 py-2 rounded-lg text-sm font-medium bg-gradient-to-r from-orange-500 to-red-500 text-white hover:from-orange-600 hover:to-red-600 transition-all"
                    @click="kbFormOpen = !kbFormOpen"
                  >
                    {{ kbFormOpen ? '收起新增' : '新增知识' }}
                  </button>
                </div>
              </div>

              <!-- 向量检索（DashScope + pgvector） -->
              <div class="bg-white rounded-xl shadow p-4 mb-5 border border-orange-100">
                <div class="flex items-center gap-2 mb-2">
                  <span class="text-sm font-medium text-gray-700">向量检索：</span>
                  <span class="text-xs text-gray-400">通过 DashScope 对 query 向量化，再用 pgvector 做 Top-K 相似度匹配</span>
                </div>
                <div class="flex flex-col md:flex-row gap-2">
                  <input
                    v-model="kbSearchQuery"
                    type="text"
                    placeholder="例如：STAR 写法 / Java 后端 技能栈 / 面试自我介绍"
                    class="flex-1 p-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-orange-500 focus:border-orange-500"
                    @keyup.enter="searchKb"
                  />
                  <select v-model="kbSearchCategory" class="p-2 border border-gray-300 rounded-lg text-sm">
                    <option value="">全部分类</option>
                    <option v-for="c in kbCategoryOptions" :key="c" :value="c">{{ c }}</option>
                  </select>
                  <select v-model.number="kbSearchTopK" class="p-2 border border-gray-300 rounded-lg text-sm">
                    <option :value="3">Top 3</option>
                    <option :value="5">Top 5</option>
                    <option :value="10">Top 10</option>
                  </select>
                  <button
                    type="button"
                    class="px-4 py-2 rounded-lg text-sm font-medium bg-orange-600 text-white hover:bg-orange-700 disabled:opacity-60"
                    :disabled="kbSearching"
                    @click="searchKb"
                  >
                    {{ kbSearching ? '检索中...' : '检索' }}
                  </button>
                </div>
                <div v-if="kbSearchResults.length > 0" class="mt-3 divide-y">
                  <div v-for="item in kbSearchResults" :key="'s-' + item.id" class="py-3">
                    <div class="flex items-center justify-between mb-1">
                      <div class="flex items-center gap-2">
                        <span class="inline-block px-2 py-0.5 text-xs rounded bg-orange-50 text-orange-700">
                          {{ item.category || '未分类' }}
                        </span>
                        <span class="text-xs text-gray-400">#{{ item.id }}</span>
                      </div>
                      <span class="text-xs text-orange-600 font-medium">
                        相似度 {{ (item.similarity * 100).toFixed(2) }}%
                      </span>
                    </div>
                    <div class="text-sm text-gray-700 whitespace-pre-wrap">{{ item.content }}</div>
                  </div>
                </div>
                <div v-else-if="kbSearchExecuted && !kbSearching" class="mt-3 text-center text-gray-400 text-sm py-3">
                  暂无匹配结果
                </div>
              </div>

              <!-- 新增知识表单 -->
              <div v-if="kbFormOpen" class="bg-white rounded-xl shadow p-5 mb-5 border border-orange-100">
                <div class="grid grid-cols-1 md:grid-cols-2 gap-3 mb-3">
                  <div>
                    <label class="block text-sm font-medium text-gray-700 mb-1">分类</label>
                    <select
                      v-model="kbCategory"
                      class="w-full p-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-orange-500 focus:border-orange-500"
                    >
                      <option v-for="c in kbCategoryOptions" :key="c" :value="c">{{ c }}</option>
                    </select>
                  </div>
                  <div>
                    <label class="block text-sm font-medium text-gray-700 mb-1">标签（用逗号分隔，可选）</label>
                    <input
                      v-model="kbTags"
                      type="text"
                      placeholder="例如：STAR, 项目经验, 后端"
                      class="w-full p-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-orange-500 focus:border-orange-500"
                    />
                  </div>
                </div>

                <div class="mb-3">
                  <label class="block text-sm font-medium text-gray-700 mb-1">内容</label>
                  <textarea
                    v-model="kbCustomText"
                    :rows="6"
                    placeholder="粘贴一条或多条内容。多条之间用【空行】分隔，每一段会作为一条知识单独入库。"
                    class="w-full p-3 border border-gray-300 rounded-lg focus:ring-2 focus:ring-orange-500 focus:border-orange-500 resize-none"
                  ></textarea>
                  <div class="text-xs text-gray-400 mt-1">
                    小贴士：后端会在入库时自动生成向量（若已配置 Embedding 模型），否则仅文本落库，不影响检索升级。
                  </div>
                </div>

                <div class="flex items-center gap-2">
                  <button
                    type="button"
                    class="px-4 py-2 rounded-lg text-sm font-medium bg-orange-600 text-white hover:bg-orange-700 disabled:opacity-60"
                    :disabled="kbSubmitting"
                    @click="submitKbCustom"
                  >
                    {{ kbSubmitting ? '正在保存...' : '保存到知识库' }}
                  </button>
                  <button
                    type="button"
                    class="px-4 py-2 rounded-lg text-sm font-medium bg-gray-100 text-gray-600 hover:bg-gray-200"
                    @click="resetKbForm"
                  >
                    清空
                  </button>
                </div>
              </div>

              <!-- 列表筛选 -->
              <div class="flex items-center gap-2 mb-3">
                <label class="text-sm text-gray-600">分类筛选：</label>
                <select
                  v-model="kbFilterCategory"
                  class="p-2 border border-gray-300 rounded-lg text-sm"
                  @change="fetchKbList(1)"
                >
                  <option value="">全部</option>
                  <option v-for="c in kbCategoryOptions" :key="c" :value="c">{{ c }}</option>
                </select>
                <button
                  type="button"
                  class="px-3 py-1 rounded-lg text-sm bg-gray-100 text-gray-600 hover:bg-gray-200"
                  @click="fetchKbList(1)"
                >
                  刷新
                </button>
                <span class="text-xs text-gray-400 ml-auto">共 {{ kbTotal }} 条</span>
              </div>

              <!-- 列表 -->
              <div class="bg-white rounded-xl shadow border border-gray-100 divide-y">
                <div v-if="kbLoading" class="p-6 text-center text-gray-400">加载中...</div>
                <div v-else-if="kbList.length === 0" class="p-10 text-center text-gray-400">
                  还没有知识条目，点击右上角「新增知识」或「一键导入示例」试试吧～
                </div>
                <div v-for="item in kbList" :key="item.id" class="p-4 hover:bg-gray-50">
                  <div class="flex items-center justify-between mb-2">
                    <div class="flex items-center gap-2">
                      <span class="inline-block px-2 py-0.5 text-xs rounded bg-orange-50 text-orange-700">
                        {{ item.category || '未分类' }}
                      </span>
                      <span class="text-xs text-gray-400">#{{ item.id }}</span>
                    </div>
                    <div class="flex items-center gap-2">
                      <span class="text-xs text-gray-400">{{ formatKbTime(item.createdAt) }}</span>
                      <button
                        v-if="kbEditingId !== item.id"
                        type="button"
                        class="px-2 py-1 rounded text-xs bg-gray-100 text-gray-600 hover:bg-gray-200"
                        @click="startEditKb(item)"
                      >
                        编辑
                      </button>
                    </div>
                  </div>

                  <template v-if="kbEditingId === item.id">
                    <div class="grid grid-cols-1 md:grid-cols-2 gap-2 mb-2">
                      <select v-model="kbEditCategory" class="p-2 border border-gray-300 rounded text-sm">
                        <option v-for="c in kbCategoryOptions" :key="c" :value="c">{{ c }}</option>
                      </select>
                      <input
                        v-model="kbEditMetadata"
                        type="text"
                        class="p-2 border border-gray-300 rounded text-sm"
                        placeholder='metadata（JSON 字符串，可选）'
                      />
                    </div>
                    <textarea
                      v-model="kbEditContent"
                      rows="4"
                      class="w-full p-2 border border-gray-300 rounded text-sm resize-y"
                    ></textarea>
                    <div class="mt-2 flex items-center gap-2">
                      <button
                        type="button"
                        class="px-3 py-1 rounded text-xs bg-orange-600 text-white hover:bg-orange-700 disabled:opacity-60"
                        :disabled="kbUpdating"
                        @click="saveEditKb(item.id)"
                      >
                        {{ kbUpdating ? '保存中...' : '保存并重算向量' }}
                      </button>
                      <button
                        type="button"
                        class="px-3 py-1 rounded text-xs bg-gray-100 text-gray-600 hover:bg-gray-200"
                        :disabled="kbUpdating"
                        @click="cancelEditKb"
                      >
                        取消
                      </button>
                    </div>
                  </template>

                  <template v-else>
                    <div class="text-sm text-gray-700 whitespace-pre-wrap">{{ item.content }}</div>
                    <div v-if="item.metadata" class="mt-2 text-xs text-gray-400 break-all">
                      metadata: {{ formatKbMetadata(item.metadata) }}
                    </div>
                  </template>
                </div>
              </div>

              <!-- 分页 -->
              <div class="flex items-center justify-center gap-3 mt-4" v-if="kbTotal > kbPageSize">
                <button
                  type="button"
                  class="px-3 py-1 rounded border text-sm disabled:opacity-40"
                  :disabled="kbCurrent <= 1 || kbLoading"
                  @click="fetchKbList(kbCurrent - 1)"
                >
                  上一页
                </button>
                <span class="text-sm text-gray-500">第 {{ kbCurrent }} / {{ kbPages }} 页</span>
                <button
                  type="button"
                  class="px-3 py-1 rounded border text-sm disabled:opacity-40"
                  :disabled="kbCurrent >= kbPages || kbLoading"
                  @click="fetchKbList(kbCurrent + 1)"
                >
                  下一页
                </button>
              </div>
            </div>
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
          <div class="flex flex-wrap items-center gap-3 mb-2">
            <button
              type="button"
              :class="['px-3 py-1 rounded text-sm', enableSearch ? 'bg-green-500 text-white' : 'bg-gray-100 text-gray-600']"
              @click="enableSearch = !enableSearch"
            >
              {{ enableSearch ? '已联网' : '联网搜索' }}
            </button>
            <label class="flex items-center gap-2 px-3 py-1 rounded text-sm cursor-pointer select-none"
              :class="enableKbRag ? 'bg-orange-500 text-white' : 'bg-gray-100 text-gray-600'">
              <input v-model="enableKbRag" type="checkbox" class="rounded border-gray-300" @click.stop />
              <span>知识库 RAG</span>
            </label>
            <div v-if="enableKbRag" class="flex items-center gap-2">
              <span class="text-xs text-gray-500">分类</span>
              <select v-model="chatKbCategory" class="text-sm border border-gray-300 rounded-lg px-2 py-1 bg-white max-w-[9rem]">
                <option value="">全部</option>
                <option v-for="c in kbCategoryOptions" :key="'ckbc-' + c" :value="c">{{ c }}</option>
              </select>
            </div>
            <button
              type="button"
              class="px-3 py-1 rounded text-sm bg-orange-100 text-orange-700 hover:bg-orange-200"
              @click="openKbTab"
              title="打开知识库管理"
            >
              管理知识库
            </button>
          </div>
          <ChatInputBox v-model="message" @sendMessage="sendMessage" :loading="chatSending" @stopGeneration="stopGeneration" />
        </div>
      </div>
    </template>
  </Layout>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
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
/** 智能对话：是否注入知识库双路 RAG */
const enableKbRag = ref(false)
const chatKbCategory = ref('')
/** 简历优化：是否注入知识库双路 RAG */
const enableResumeKbRag = ref(false)
const resumeKbCategory = ref('')
let abortController = null
const chatSending = ref(false)

const targetPosition = ref('Java 后端')
const resumeText = ref('')
const additionalRequirements = ref('')
const uploadedFile = ref('')
const optimizing = ref(false)
const fileInput = ref(null)

// ====== 知识库（resume_knowledge_base）======
// 预设的分类选项（也可在此扩展）
const kbCategoryOptions = ['简历通用', '项目描述', '面试', '后端', '前端', '算法', '职业发展', '未分类']

// 新增表单相关
const kbFormOpen = ref(false)
const kbSubmitting = ref(false)
const kbCategory = ref('简历通用')
const kbCustomText = ref('')
const kbTags = ref('')

// 列表相关
const kbFilterCategory = ref('')
const kbList = ref([])
const kbTotal = ref(0)
const kbCurrent = ref(1)
const kbPageSize = ref(10)
const kbLoading = ref(false)
const kbPages = computed(() => {
  const total = kbTotal.value || 0
  const size = kbPageSize.value || 10
  return Math.max(1, Math.ceil(total / size))
})

// 编辑知识（保存时调用 /resume-kb/update，后端会重算 embedding）
const kbEditingId = ref(null)
const kbEditCategory = ref('未分类')
const kbEditContent = ref('')
const kbEditMetadata = ref('')
const kbUpdating = ref(false)

// 回填向量
const kbRefilling = ref(false)

// 向量检索
const kbSearchQuery = ref('')
const kbSearchCategory = ref('')
const kbSearchTopK = ref(5)
const kbSearching = ref(false)
const kbSearchExecuted = ref(false)
const kbSearchResults = ref([])

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
        additionalRequirements: additionalRequirements.value,
        knowledgeRag: enableResumeKbRag.value,
        kbCategory: resumeKbCategory.value || undefined,
        kbTopK: 5
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
      {
        message: text,
        chatId: uuid,
        networkSearch: enableSearch.value,
        knowledgeRag: enableKbRag.value,
        kbCategory: chatKbCategory.value || undefined,
        kbTopK: 5
      },
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
  // 会话列表已在 Sidebar.vue onMounted 中拉取，此处勿重复请求 /chat/list
  scrollToBottom()
})

onUnmounted(() => {
  // 页面切换/组件卸载时，主动取消未完成的 SSE，避免后台继续生成
  stopGeneration()
})

// 打开知识库 Tab（从任意页跳转过来都会首次加载一次列表）
const openKbTab = () => {
  ui.setTab('kb')
  if (kbList.value.length === 0) {
    fetchKbList(1)
  }
}

// 一键导入内置示例（成功后刷新列表）
const importKbSamples = async () => {
  kbSubmitting.value = true
  try {
    const res = await knowledgeApi.importSamples()
    if (!res.success) {
      antMessage.error(res.message || '导入失败')
      return
    }
    antMessage.success(`已导入示例数据：${res.data?.imported ?? 0} 条`)
    ui.setTab('kb')
    fetchKbList(1)
  } catch (e) {
    console.error(e)
    antMessage.error(e.message || '导入失败')
  } finally {
    kbSubmitting.value = false
  }
}

// 把用户在 textarea 中粘贴的多条内容拆分为 items
// 规则：以【空行】作为段落分隔，每一段落作为一条知识入库
const splitKbContents = (raw) => {
  const text = String(raw || '').trim()
  if (!text) return []
  return text
    .split(/\n\s*\n+/)
    .map((s) => s.trim())
    .filter(Boolean)
}

// 把标签字符串转为 metadata JSON（后端接 JsonNode，这里直接传对象字面值）
const buildKbMetadata = () => {
  const tags = String(kbTags.value || '')
    .split(/[,，]/)
    .map((s) => s.trim())
    .filter(Boolean)
  const meta = { 来源: '前端手动新增' }
  if (tags.length > 0) meta['标签'] = tags
  return meta
}

// 保存到知识库（支持一次多条）
const submitKbCustom = async () => {
  const contents = splitKbContents(kbCustomText.value)
  if (contents.length === 0) {
    antMessage.warning('请先输入要导入的内容')
    return
  }
  const metadata = buildKbMetadata()
  const items = contents.map((c) => ({
    content: c,
    category: kbCategory.value,
    metadata
  }))

  kbSubmitting.value = true
  try {
    const res = await knowledgeApi.importBatch({ items })
    if (!res.success) {
      antMessage.error(res.message || '导入失败')
      return
    }
    antMessage.success(`新增成功：${res.data?.imported ?? 0} 条`)
    kbCustomText.value = ''
    kbTags.value = ''
    kbFormOpen.value = false
    fetchKbList(1)
  } catch (e) {
    console.error(e)
    antMessage.error(e.message || '导入失败')
  } finally {
    kbSubmitting.value = false
  }
}

const resetKbForm = () => {
  kbCustomText.value = ''
  kbTags.value = ''
}

const startEditKb = (item) => {
  kbEditingId.value = item.id
  kbEditCategory.value = item.category || '未分类'
  kbEditContent.value = item.content || ''
  kbEditMetadata.value = typeof item.metadata === 'string'
    ? item.metadata
    : (item.metadata ? JSON.stringify(item.metadata) : '')
}

const cancelEditKb = () => {
  kbEditingId.value = null
  kbEditCategory.value = '未分类'
  kbEditContent.value = ''
  kbEditMetadata.value = ''
}

const saveEditKb = async (id) => {
  const content = String(kbEditContent.value || '').trim()
  if (!content) {
    antMessage.warning('内容不能为空')
    return
  }

  let metadata = null
  const metadataText = String(kbEditMetadata.value || '').trim()
  if (metadataText) {
    try {
      metadata = JSON.parse(metadataText)
    } catch (_) {
      antMessage.warning('metadata 必须是合法 JSON')
      return
    }
  }

  kbUpdating.value = true
  try {
    const res = await knowledgeApi.update({
      id,
      content,
      category: kbEditCategory.value,
      metadata
    })
    if (!res.success || !res.data) {
      antMessage.error(res.message || '保存失败')
      return
    }
    antMessage.success('已保存，向量已同步重算')
    cancelEditKb()
    fetchKbList(kbCurrent.value)
  } catch (e) {
    console.error(e)
    antMessage.error(e.message || '保存失败')
  } finally {
    kbUpdating.value = false
  }
}

// 分页查询知识库列表
const fetchKbList = async (page) => {
  const target = Math.max(1, page || 1)
  kbLoading.value = true
  try {
    const res = await knowledgeApi.listPage({
      current: target,
      size: kbPageSize.value,
      category: kbFilterCategory.value || ''
    })
    if (!res.success) {
      antMessage.error(res.message || '加载失败')
      return
    }
    kbList.value = res.data || []
    kbTotal.value = res.total ?? 0
    kbCurrent.value = target
  } catch (e) {
    console.error(e)
    antMessage.error(e.message || '加载失败')
  } finally {
    kbLoading.value = false
  }
}

// 时间格式化：YYYY-MM-DD HH:mm
const formatKbTime = (t) => {
  if (!t) return ''
  try {
    const d = new Date(t)
    if (Number.isNaN(d.getTime())) return String(t)
    const pad = (n) => String(n).padStart(2, '0')
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
  } catch (_) {
    return String(t)
  }
}

// metadata 可能是 JSONB 字符串或已解析对象，这里统一转成可读字符串
const formatKbMetadata = (m) => {
  if (!m) return ''
  if (typeof m === 'string') return m
  try {
    return JSON.stringify(m)
  } catch (_) {
    return String(m)
  }
}

// 回填：为历史数据（embedding 为空）批量调用 DashScope 生成向量
const refillKbEmbeddings = async () => {
  kbRefilling.value = true
  try {
    const res = await knowledgeApi.refillEmbeddings(16)
    if (!res.success) {
      antMessage.error(res.message || '回填失败')
      return
    }
    const n = res.data ?? 0
    if (n > 0) {
      antMessage.success(`已为 ${n} 条历史数据生成向量`)
    } else {
      antMessage.info('没有需要回填的记录（或 DashScope 未配置）')
    }
  } catch (e) {
    console.error(e)
    antMessage.error(e.message || '回填失败')
  } finally {
    kbRefilling.value = false
  }
}

// 向量检索：把 query 送到后端，由后端调用 DashScope 向量化后使用 pgvector 的余弦距离查询
const searchKb = async () => {
  const query = String(kbSearchQuery.value || '').trim()
  if (!query) {
    antMessage.warning('请输入检索内容')
    return
  }
  kbSearching.value = true
  kbSearchExecuted.value = true
  try {
    const res = await knowledgeApi.search({
      query,
      topK: kbSearchTopK.value,
      category: kbSearchCategory.value || ''
    })
    if (!res.success) {
      antMessage.error(res.message || '检索失败')
      kbSearchResults.value = []
      return
    }
    kbSearchResults.value = res.data || []
  } catch (e) {
    console.error(e)
    antMessage.error(e.message || '检索失败')
    kbSearchResults.value = []
  } finally {
    kbSearching.value = false
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
