import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  config: null as Record<string, unknown> | null,
}))

vi.mock('axios', () => ({
  default: {
    create: (config: Record<string, unknown>) => {
      mocks.config = config
      return { get: mocks.get, post: mocks.post }
    },
    isAxiosError: (value: unknown) => Boolean(value && typeof value === 'object' && 'response' in value),
  },
}))

import { classifyPasswordResetError, confirmPasswordReset, requestPasswordReset } from './anonymousAuthApi'

describe('anonymous authentication API client', () => {
  beforeEach(() => { mocks.get.mockReset(); mocks.post.mockReset() })

  it('is isolated, credentialed, and configured for the exact XSRF contract', () => {
    expect(mocks.config).toEqual(expect.objectContaining({
      withCredentials: true,
      withXSRFToken: true,
      xsrfCookieName: 'XSRF-TOKEN',
      xsrfHeaderName: 'X-XSRF-TOKEN',
    }))
  })

  it('primes CSRF before each mutation without copying the response token into a header', async () => {
    const responseToken = 'encoded-response-token'
    mocks.get.mockResolvedValue({ data: { token: responseToken, headerName: 'X-XSRF-TOKEN' } })
    mocks.post.mockResolvedValue({ status: 204 })
    await requestPasswordReset('admin@example.invalid')
    expect(mocks.get).toHaveBeenCalledWith('/auth/csrf')
    expect(mocks.post).toHaveBeenCalledWith('/auth/password-reset/request', { email: 'admin@example.invalid' })
    expect(mocks.post.mock.calls.flat()).not.toContain(responseToken)

    mocks.get.mockResolvedValue({ data: { token: responseToken, headerName: 'X-XSRF-TOKEN' } })
    await confirmPasswordReset('A'.repeat(43), 'New-password-42!')
    expect(mocks.get).toHaveBeenCalledTimes(2)
    expect(mocks.post).toHaveBeenLastCalledWith('/auth/password-reset/confirm', { token: 'A'.repeat(43), newPassword: 'New-password-42!' })
    expect(mocks.post).toHaveBeenCalledTimes(2)
  })

  it.each(['', '   ', '\t', ' token', 'token ', 'x'.repeat(513)])('rejects malformed CSRF token %j before POST', async (token) => {
    mocks.get.mockResolvedValue({ data: { token, headerName: 'X-XSRF-TOKEN' } })
    await expect(requestPasswordReset('admin@example.invalid')).rejects.toThrow('Authentication security token could not be obtained')
    expect(mocks.post).not.toHaveBeenCalled()
  })

  it('rejects an unexpected CSRF header and safely classifies response outcomes', async () => {
    mocks.get.mockResolvedValue({ data: { token: 'valid-token', headerName: 'Unexpected' } })
    await expect(confirmPasswordReset('A'.repeat(43), 'New-password-42!')).rejects.toThrow('Authentication security token could not be obtained')
    expect(classifyPasswordResetError({ response: { status: 400, data: { message: 'This password reset link is invalid or has expired.' } } })).toBe('invalid')
    expect(classifyPasswordResetError({ response: { status: 400, data: { message: 'Request validation failed' } } })).toBe('policy')
    expect(classifyPasswordResetError({ response: { status: 401, data: { message: 'private' } } })).toBe('unexpected')
  })
})
