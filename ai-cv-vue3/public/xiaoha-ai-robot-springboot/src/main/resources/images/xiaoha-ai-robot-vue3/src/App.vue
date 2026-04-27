<template>
  <a-config-provider :locale="locale">
    <router-view></router-view>
  </a-config-provider>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import zhCN from 'ant-design-vue/es/locale/zh_CN';
import dayjs from 'dayjs';
import 'dayjs/locale/zh-cn';
import { useRouter, useRoute } from 'vue-router';
import { useChatStore } from '@/stores/chatStore';

// 设置时间类组件为中文
dayjs.locale('zh-cn');

// 设置 Ant Design Vue 组件库默认语言为中文
const locale = ref(zhCN)

const router = useRouter();
const route = useRoute();
const chatStore = useChatStore();

// 监听路由变化，更新 context
const updateContextFromRoute = () => {
  const context = route.meta?.context || 'general';
  chatStore.updateCurrentContext(context);
};

onMounted(() => {
  // 初始化时更新
  updateContextFromRoute();
  
  // 监听路由变化
  router.afterEach(() => {
    updateContextFromRoute();
  });
});
</script>

<style scoped>
</style>
