<template>
  <div class="relative">
    <textarea
      :value="modelValue"
      @input="$emit('update:modelValue', $event.target.value)"
      @keydown.enter.exact.prevent="handleEnter"
      placeholder="输入消息..."
      rows="1"
      class="w-full border border-gray-300 rounded-xl px-4 py-3 pr-12 focus:ring-2 focus:ring-green-500 focus:border-green-500 resize-none"
      :disabled="loading"
    ></textarea>
    <button
      v-if="!loading"
      @click="$emit('sendMessage')"
      :disabled="!modelValue?.trim()"
      class="absolute right-2 bottom-2 p-2 rounded-lg bg-gradient-to-r from-green-500 to-teal-600 text-white disabled:opacity-50 disabled:cursor-not-allowed hover:from-green-600 hover:to-teal-700 transition-all"
    >
      <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 19l9 2-9-18-9 18 9-2zm0 0v-8" />
      </svg>
    </button>
    <button
      v-else
      @click="$emit('stopGeneration')"
      class="absolute right-2 bottom-2 p-2 rounded-lg bg-red-500 text-white hover:bg-red-600 transition-all"
    >
      <svg class="w-5 h-5" viewBox="0 0 24 24" fill="currentColor">
        <rect x="6" y="6" width="12" height="12" rx="1" />
      </svg>
    </button>
  </div>
</template>

<script setup>
const props = defineProps({
  modelValue: String,
  loading: Boolean
})

const emit = defineEmits(['update:modelValue', 'sendMessage', 'stopGeneration'])

const handleEnter = () => {
  // 按下回车键发送消息
  emit('sendMessage')
}
</script>
