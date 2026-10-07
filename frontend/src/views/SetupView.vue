<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

import { ApiError } from '@/api/errors'
import { authApi } from '@/api/modules/auth'
import EasyButton from '@/components/easy/EasyButton.vue'
import EasyInput from '@/components/easy/EasyInput.vue'
import { useAuthStore } from '@/stores/auth'
import { useNotificationStore } from '@/stores/notification'

/**
 * 首次初始化：创建组织名称 + 第一个 ROOT 账号。
 * 初始化完成后 /setup 永久关闭，且任何账号都不得重新初始化系统。
 */
const router = useRouter()
const auth = useAuthStore()
const notification = useNotificationStore()

const form = reactive({
  organizationName: 'Easy Studio',
  username: '',
  displayName: '',
  password: '',
  confirmPassword: '',
})

const submitting = ref(false)
const formError = ref<string | null>(null)

const passwordHint = computed(() => '至少 10 位，需同时包含字母与数字，且不得包含用户名')

onMounted(async () => {
  void authApi.csrf().catch(() => undefined)
  try {
    const status = await authApi.setupStatus()
    if (!status.required) {
      // 已初始化：/setup 不再可用
      void router.replace({ name: 'login' })
      return
    }
    if (status.organizationName) {
      form.organizationName = status.organizationName
    }
  } catch {
    // 状态查询失败时保持页面可用，提交阶段仍会由后端二次校验
  }
})

function validate(): string | null {
  if (!form.organizationName.trim()) return '请输入组织名称'
  if (!/^[a-zA-Z][a-zA-Z0-9_]{2,31}$/.test(form.username.trim())) {
    return '用户名需以字母开头，仅含字母 / 数字 / 下划线，长度 3~32'
  }
  if (!form.displayName.trim()) return '请输入显示名称'
  if (form.password.length < 10) return '密码长度至少 10 位'
  if (!/[a-zA-Z]/.test(form.password) || !/[0-9]/.test(form.password)) {
    return '密码必须同时包含字母与数字'
  }
  if (form.password !== form.confirmPassword) return '两次输入的密码不一致'
  return null
}

async function submit(): Promise<void> {
  formError.value = validate()
  if (formError.value) return

  submitting.value = true
  try {
    const result = await authApi.initializeSetup({
      organizationName: form.organizationName.trim(),
      username: form.username.trim(),
      displayName: form.displayName.trim(),
      password: form.password,
    })
    // 初始化完成后自动登录并进入工作台
    await auth.login({ username: form.username.trim(), password: form.password })
    notification.success(`EasyOA 初始化完成，欢迎加入 ${result.organizationName}`)
    await router.replace({ name: 'workspace' })
  } catch (error) {
    if (error instanceof ApiError && error.fields?.length && error.fields[0]) {
      formError.value = error.fields[0].message
    } else {
      formError.value = error instanceof ApiError ? error.message : '初始化失败，请稍后再试'
    }
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="setup">
    <div class="setup__card">
      <header class="setup__header">
        <svg class="setup__logo" viewBox="0 0 32 32" fill="none" aria-hidden="true">
          <rect width="32" height="32" rx="9" fill="var(--easy-brand)" />
          <path
            d="M9.5 16.6l4.2 4.2 8.8-9.4"
            stroke="#fff"
            stroke-width="2.6"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
        </svg>
        <h1 class="setup__title">初始化 EasyOA</h1>
        <p class="setup__desc">
          这是系统的第一次启动。请创建组织与第一个 ROOT 账号，完成后初始化入口将永久关闭。
        </p>
      </header>

      <form class="setup__form" @submit.prevent="submit">
        <EasyInput
          v-model="form.organizationName"
          label="组织名称"
          required
          placeholder="例如：Easy Studio"
          hint="将展示在系统与工作台中，可后续在系统设置中调整"
        />
        <EasyInput
          v-model="form.username"
          label="ROOT 用户名"
          required
          placeholder="字母开头，3~32 位"
          autocomplete="username"
          hint="ROOT 是系统最高权限账号，创建后用户名不可修改"
        />
        <EasyInput
          v-model="form.displayName"
          label="显示名称"
          required
          placeholder="例如：Waiting"
        />
        <EasyInput
          v-model="form.password"
          label="登录密码"
          required
          type="password"
          show-password
          autocomplete="new-password"
          :hint="passwordHint"
        />
        <EasyInput
          v-model="form.confirmPassword"
          label="确认密码"
          required
          type="password"
          show-password
          autocomplete="new-password"
        />

        <p v-if="formError" class="setup__error" role="alert">{{ formError }}</p>

        <EasyButton variant="primary" size="lg" block native-type="submit" :loading="submitting">
          创建组织并进入 EasyOA
        </EasyButton>
      </form>

      <p class="setup__note">
        ROOT 的高危操作（审计清理、安全策略变更、数据销毁等）在执行时还需要重新验证密码与 TOTP。
      </p>
    </div>
  </div>
</template>

<style scoped>
.setup {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--easy-space-10) var(--easy-space-4);
  background: var(--easy-bg);
}

.setup__card {
  width: 100%;
  max-width: 480px;
  background: var(--easy-surface);
  border: 1px solid var(--easy-border);
  border-radius: var(--easy-radius-xl);
  box-shadow: var(--easy-shadow-sm);
  padding: var(--easy-space-8);
}

.setup__header {
  margin-bottom: var(--easy-space-6);
}

.setup__logo {
  width: 40px;
  height: 40px;
  margin-bottom: var(--easy-space-4);
}

.setup__title {
  font-size: var(--easy-text-2xl);
  font-weight: 600;
  letter-spacing: -0.02em;
}

.setup__desc {
  margin-top: var(--easy-space-2);
  color: var(--easy-text-3);
  font-size: var(--easy-text-sm);
  line-height: var(--easy-leading-relaxed);
}

.setup__form {
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-4);
}

.setup__error {
  padding: var(--easy-space-2) var(--easy-space-3);
  border: 1px solid var(--easy-danger);
  border-radius: var(--easy-radius-md);
  background: var(--easy-danger-bg);
  color: var(--easy-danger);
  font-size: var(--easy-text-xs);
}

.setup__note {
  margin-top: var(--easy-space-6);
  padding-top: var(--easy-space-4);
  border-top: 1px dashed var(--easy-border-strong);
  color: var(--easy-text-3);
  font-size: var(--easy-text-xs);
  line-height: var(--easy-leading-relaxed);
}
</style>