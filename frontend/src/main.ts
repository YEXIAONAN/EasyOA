import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'

import App from './App.vue'
import router from './router'
import { onUnauthenticated } from '@/api/client'
import { useAuthStore } from '@/stores/auth'

import 'element-plus/dist/index.css'
import './styles/tokens.css'
import './styles/element.css'
import './styles/base.css'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)
app.use(ElementPlus, { locale: zhCn })

/**
 * 全局 401 处理：后端判定会话失效时，清理本地状态并回到登录页，
 * 同时保留 redirect，登录后回到原本想访问的页面。
 */
onUnauthenticated(() => {
  const auth = useAuthStore(pinia)
  auth.clearSession()
  const current = router.currentRoute.value
  if (current.name !== 'login' && current.name !== 'setup') {
    void router.replace({ name: 'login', query: { redirect: current.fullPath } })
  }
})

app.mount('#app')