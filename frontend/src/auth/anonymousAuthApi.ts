import axios from 'axios'

interface CsrfResponse {
  token: string
  headerName: string
}

interface PasswordResetErrorBody {
  message?: unknown
}

const EXPECTED_CSRF_HEADER = 'X-XSRF-TOKEN'
const INVALID_LINK_MESSAGE = 'This password reset link is invalid or has expired.'

const anonymousAuthApi = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/v1',
  headers: { 'Content-Type': 'application/json' },
  withCredentials: true,
  withXSRFToken: true,
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: EXPECTED_CSRF_HEADER,
})

function isCsrfResponse(value: unknown): value is CsrfResponse {
  if (typeof value !== 'object' || value === null) return false
  const response = value as Record<string, unknown>
  const token = response.token
  return typeof token === 'string'
    && token.length > 0
    && token.length <= 512
    && token.trim() === token
    && response.headerName === EXPECTED_CSRF_HEADER
}

async function primeCsrfCookie(): Promise<void> {
  const response = await anonymousAuthApi.get<unknown>('/auth/csrf')
  if (!isCsrfResponse(response.data)) {
    throw new Error('Authentication security token could not be obtained')
  }
}

export async function requestPasswordReset(email: string): Promise<void> {
  await primeCsrfCookie()
  await anonymousAuthApi.post('/auth/password-reset/request', { email })
}

export async function confirmPasswordReset(token: string, newPassword: string): Promise<void> {
  await primeCsrfCookie()
  await anonymousAuthApi.post('/auth/password-reset/confirm', { token, newPassword })
}

export function classifyPasswordResetError(error: unknown): 'invalid' | 'policy' | 'unexpected' {
  if (!axios.isAxiosError<PasswordResetErrorBody>(error) || !error.response) return 'unexpected'
  if (error.response.status !== 400) return 'unexpected'
  return error.response.data?.message === INVALID_LINK_MESSAGE ? 'invalid' : 'policy'
}
