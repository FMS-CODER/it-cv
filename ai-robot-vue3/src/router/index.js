import { createRouter, createWebHashHistory, createWebHistory } from 'vue-router'

const routes = [
    {
        path: '/',
        name: 'Index',
        component: () => import('@/views/Index.vue'),
        meta: {
            title: '智能简历',
            context: 'resume'
        }
    }
]

const router = createRouter({
    history: createWebHashHistory(),
    routes, 
})

export default router
