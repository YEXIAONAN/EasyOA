<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { ApiError } from '@/api/errors'
import { authApi } from '@/api/modules/auth'
import EasyButton from '@/components/easy/EasyButton.vue'
import EasyInput from '@/components/easy/EasyInput.vue'
import { useAuthStore } from '@/stores/auth'
import { useNotificationStore } from '@/stores/notification'

/**
 * 登录页。认证完全依赖后端 Session Cookie，前端不保存任何 Token。
 */
const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const notification = useNotificationStore()

const form = reactive({ username: '', password: '' })
const submitting = ref(false)
const formError = ref<string | null>(null)

const isDev = import.meta.env.DEV

onMounted(() => {
  // 仅在缺少 CSRF Cookie 时引导一次，避免多余请求与页面跳转造成的中断噪声
  if (!document.cookie.includes('XSRF-TOKEN')) {
    void authApi.csrf().catch(() => undefined)
  }
})

/** 仅允许站内跳转，避免开放重定向 */
function safeRedirect(): string | null {
  const redirect = route.query.redirect
  if (typeof redirect !== 'string') return null
  if (!redirect.startsWith('/') || redirect.startsWith('//')) return null
  return redirect
}

async function submit(): Promise<void> {
  formError.value = null
  if (!form.username.trim() || !form.password) {
    formError.value = '请输入用户名与密码'
    return
  }
  submitting.value = true
  try {
    await auth.login({ username: form.username.trim(), password: form.password })
    notification.success(`欢迎回来，${auth.displayName}`)
    const redirect = safeRedirect()
    await router.replace(redirect ?? { name: 'workspace' })
  } catch (error) {
    formError.value = error instanceof ApiError ? error.message : '登录失败，请稍后再试'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="auth">
    <section class="auth__brand">
      <div class="auth__brand-inner">
        <div class="auth__logo-row">
          <svg class="auth__logo" viewBox="0 0 32 32" fill="none" aria-hidden="true">
            <rect width="32" height="32" rx="9" fill="var(--easy-brand)" />
            <path
              d="M9.5 16.6l4.2 4.2 8.8-9.4"
              stroke="#fff"
              stroke-width="2.6"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
          </svg>
          <div>
            <div class="auth__product">EasyOA</div>
            <div class="auth__product-sub">企业协同办公平台</div>
          </div>
        </div>

        <h2 class="auth__headline">让团队专注在真正重要的事情上</h2>
        <p class="auth__subline">
          项目协作 → 任务执行 → 团队沟通 → 审批流转 → 组织管理 → 安全审计
        </p>

        <ul class="auth__features">
          <li>项目与任务闭环，进度一目了然</li>
          <li>模板化审批，流程可追溯</li>
          <li>完整的审计与安全事件记录</li>
        </ul>

        <p class="auth__footnote">私有化部署 · 数据始终留在你自己的服务器上</p>
      </div>
    </section>

    <section class="auth__panel">
      <div class="auth__card">
        <header class="auth__card-header">
          <h1 class="auth__title">登录</h1>
          <p class="auth__desc">使用你的 EasyOA 账号继续</p>
        </header>

        <form class="auth__form" @submit.prevent="submit">
          <EasyInput
            v-model="form.username"
            label="用户名"
            placeholder="请输入用户名"
            autocomplete="username"
            :disabled="submitting"
          />
          <EasyInput
            v-model="form.password"
            label="密码"
            type="password"
            show-password
            placeholder="请输入密码"
            autocomplete="current-password"
            :disabled="submitting"
          />

          <p v-if="formError" class="auth__error" role="alert">{{ formError }}</p>

          <EasyButton
            variant="primary"
            size="lg"
            block
            native-type="submit"
            :loading="submitting"
          >
            登录
          </EasyButton>
        </form>

        <p v-if="isDev" class="auth__dev-hint">
          开发环境默认账号：<span class="easy-mono">root / admin / member</span>，初始密码见 README
        </p>
      </div>
    </section>
  </div>
</template>

<style scoped>
.auth {
  display: grid;
  grid-template-columns: minmax(360px, 1fr) minmax(420px, 1fr);
  min-height: 100vh;
  background: var(--easy-surface);
}

.auth__brand {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--easy-space-12) var(--easy-space-10);
  background: var(--easy-brand-subtle);
  border-right: 1px solid var(--easy-brand-subtle-border);
}

.auth__brand-inner {
  max-width: 420px;
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-5);
}

.auth__logo-row {
  display: flex;
  align-items: center;
  gap: var(--easy-space-3);
}

.auth__logo {
  width: 40px;
  height: 40px;
}

.auth__product {
  font-size: var(--easy-text-xl);
  font-weight: 700;
  letter-spacing: -0.02em;
}

.auth__product-sub {
  font-size: var(--easy-text-xs);
  color: var(--easy-text-2);
}

.auth__headline {
  font-size: 28px;
  font-weight: 600;
  line-height: 1.35;
  letter-spacing: -0.02em;
  color: var(--easy-text-1);
}

.auth__subline {
  color: var(--easy-text-2);
  font-size: var(--easy-text-sm);
  line-height: var(--easy-leading-relaxed);
}

.auth__features {
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-2);
  font-size: var(--easy-text-sm);
  color: var(--easy-text-2);
}

.auth__features li {
  display: flex;
  align-items: center;
  gap: var(--easy-space-2);
}

.auth__features li::before {
  content: '';
  width: 5px;
  height: 5px;
  border-radius: var(--easy-radius-full);
  background: var(--easy-brand);
  flex: none;
}

.auth__footnote {
  font-size: var(--easy-text-xs);
  color: var(--easy-text-3);
}

.auth__panel {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--easy-space-10) var(--easy-space-8);
  background: var(--easy-bg);
}

.auth__card {
  width: 100%;
  max-width: 380px;
  background: var(--easy-surface);
  border: 1px solid var(--easy-border);
  border-radius: var(--easy-radius-xl);
  box-shadow: var(--easy-shadow-sm);
  padding: var(--easy-space-8);
}

.auth__card-header {
  margin-bottom: var(--easy-space-6);
}

.auth__title {
  font-size: var(--easy-text-2xl);
  font-weight: 600;
  letter-spacing: -0.02em;
}

.auth__desc {
  margin-top: var(--easy-space-1);
  color: var(--easy-text-3);
  font-size: var(--easy-text-sm);
}

.auth__form {
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-4);
}

.auth__error {
  padding: var(--easy-space-2) var(--easy-space-3);
  border: 1px solid var(--easy-danger);
  border-radius: var(--easy-radius-md);
  background: var(--easy-danger-bg);
  color: var(--easy-danger);
  font-size: var(--easy-text-xs);
}

.auth__dev-hint {
  margin-top: var(--easy-space-5);
  padding-top: var(--easy-space-4);
  border-top: 1px dashed var(--easy-border-strong);
  color: var(--easy-text-3);
  font-size: var(--easy-text-xs);
}

@media (max-width: 1023px) {
  .auth {
    grid-template-columns: 1fr;
  }

  .auth__brand {
    display: none;
  }
}
</style>