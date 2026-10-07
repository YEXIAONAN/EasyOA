/// <reference types="vite/client" />

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<Record<string, unknown>, Record<string, unknown>, unknown>
  export default component
}

interface ImportMetaEnv {
  /** API 基础路径（默认 /api，由 Vite / Nginx 反向代理） */
  readonly VITE_API_BASE?: string
  /** 产品名称（用于页面标题） */
  readonly VITE_APP_NAME?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}