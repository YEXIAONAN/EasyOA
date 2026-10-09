import { apiRequest } from '@/api/client'

export interface AboutInfo {
  edition: string
  version: string
  license: string
  publisher: string
  sourceUrl: string
  releaseStatus: 'Official' | 'Development Build' | 'Unofficial Build'
  signatureVerified: boolean
}

export function getAboutInfo(): Promise<AboutInfo> {
  return apiRequest({ method: 'GET', url: '/system/about', silent401: true })
}
