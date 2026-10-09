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
 * 登录页。
 *
 * 认证完全依赖后端 Session Cookie，前端不保存任何 Token。
 * 页面只呈现品牌与登录表单：不展示功能宣传清单，也不输出任何开发环境账号信息
 * （演示账号仅由后端 dev profile 的种子数据提供，见 README）。
 */
const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const notification = useNotificationStore()

const form = reactive({ username: '', password: '', totpCode: '' })
const submitting = ref(false)
const formError = ref<string | null>(null)
/** 后端返回 TOTP_REQUIRED 后展示动态验证码输入（账号已绑定动态口令） */
const totpRequired = ref(false)

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
    await auth.login({
      username: form.username.trim(),
      password: form.password,
      totpCode: totpRequired.value ? form.totpCode.trim() : undefined,
    })
    notification.success(`欢迎回来，${auth.displayName}`)
    const redirect = safeRedirect()
    await router.replace(redirect ?? { name: 'workspace' })
  } catch (error) {
    if (error instanceof ApiError && error.code === 'TOTP_REQUIRED') {
      // 第一步（密码）已通过：引导用户补填动态验证码，不清空表单
      totpRequired.value = true
      formError.value = '该账号已启用动态口令，请输入 6 位验证码'
      return
    }
    if (error instanceof ApiError && error.code === 'TOTP_INVALID') {
      totpRequired.value = true
      form.totpCode = ''
      formError.value = error.message
      return
    }
    formError.value = error instanceof ApiError ? error.message : '登录失败，请稍后再试'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="auth">
    <aside class="auth__brand">
      <div class="auth__brand-inner">
        <div class="auth__wordmark">
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
          <div class="auth__wordmark-text">
            <span class="auth__product">EasyOA</span>
            <span class="auth__product-sub">企业协同办公平台</span>
          </div>
        </div>

        <p class="auth__tagline">让协作更高效，让工作更有序。</p>
      </div>
      <div class="auth__brand-ornament" aria-hidden="true" />
    </aside>

    <section class="auth__panel">
      <div class="auth__panel-inner">
        <!-- 窄屏下的品牌标识：左栏隐藏后仍保留产品识别 -->
        <div class="auth__compact-brand">
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
          <span class="auth__product">EasyOA</span>
        </div>

        <div class="auth__card">
          <header class="auth__card-header">
            <h1 class="auth__title">欢迎登录</h1>
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

            <EasyInput
              v-if="totpRequired"
              v-model="form.totpCode"
              label="动态验证码"
              placeholder="Authenticator 应用中的 6 位数字"
              inputmode="numeric"
              maxlength="6"
              autocomplete="one-time-code"
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
          <router-link class="auth__about" :to="{ name: 'about' }">关于 EasyOA · 获取源码</router-link>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.auth__about { display: inline-block; margin-top: var(--easy-space-5); color: var(--easy-text-3); font-size: var(--easy-text-sm); }
.auth {
  display: grid;
  grid-template-columns: minmax(360px, 0.9fr) minmax(440px, 1.1fr);
  min-height: 100vh;
  background: var(--easy-bg);
}

/* --- 左侧：品牌区（低对比度品牌背景 + 单一标语） --------------------------- */
.auth__brand {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--easy-space-12) var(--easy-space-10);
  background: var(--easy-brand-subtle);
  border-right: 1px solid var(--easy-brand-subtle-border);
  overflow: hidden;
}

.auth__brand-inner {
  position: relative;
  z-index: 1;
  max-width: 360px;
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-6);
}

/* 低干扰品牌装饰：极浅色块，不参与信息表达 */
.auth__brand-ornament {
  position: absolute;
  right: -120px;
  bottom: -140px;
  width: 380px;
  height: 380px;
  border-radius: 50%;
  background: var(--easy-green-100);
  opacity: 0.55;
}

.auth__wordmark {
  display: flex;
  align-items: center;
  gap: var(--easy-space-3);
}

.auth__logo {
  width: 38px;
  height: 38px;
  flex: none;
}

.auth__wordmark-text {
  display: flex;
  flex-direction: column;
  line-height: 1.25;
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

.auth__tagline {
  font-size: 26px;
  font-weight: 600;
  line-height: 1.4;
  letter-spacing: -0.02em;
  color: var(--easy-text-1);
}

/* --- 右侧：登录表单 ------------------------------------------------------- */
.auth__panel {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--easy-space-10) var(--easy-space-8);
}

.auth__panel-inner {
  width: 100%;
  max-width: 380px;
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-6);
}

.auth__compact-brand {
  display: none;
  align-items: center;
  gap: var(--easy-space-2);
}

.auth__compact-brand .auth__logo {
  width: 30px;
  height: 30px;
}

.auth__card {
  width: 100%;
  background: var(--easy-surface);
  border: 1px solid var(--easy-border);
  border-radius: var(--easy-radius-xl);
  box-shadow: var(--easy-shadow-xs);
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

/* --- 响应式：平板收窄品牌栏，移动端单栏但保留品牌标识 --------------------- */
@media (max-width: 1279px) {
  .auth {
    grid-template-columns: minmax(300px, 0.8fr) minmax(400px, 1.2fr);
  }

  .auth__brand {
    padding: var(--easy-space-10) var(--easy-space-8);
  }

  .auth__tagline {
    font-size: 22px;
  }
}

@media (max-width: 900px) {
  .auth {
    grid-template-columns: 1fr;
  }

  .auth__brand {
    display: none;
  }

  .auth__panel {
    padding: var(--easy-space-8) var(--easy-space-5);
  }

  .auth__panel-inner {
    max-width: 400px;
  }

  .auth__compact-brand {
    display: flex;
  }

  .auth__card {
    padding: var(--easy-space-6);
  }
}
</style>
