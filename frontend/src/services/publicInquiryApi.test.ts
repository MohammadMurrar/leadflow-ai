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
  },
}))

import { getPublicInquiryConfiguration, submitPublicInquiry } from './publicInquiryApi'
import type { PublicLeadRequest } from '../types/publicInquiry'

const inquiry: PublicLeadRequest = {
  fullName: 'Synthetic Inquiry',
  email: 'synthetic@example.invalid',
  phone: null,
  company: null,
  serviceId: '11111111-1111-4111-8111-111111111111',
  estimatedBudget: null,
  desiredStartDate: null,
  message: 'A sufficiently detailed synthetic inquiry message.',
  website: '',
}

describe('public inquiry API client CSRF contract', () => {
  beforeEach(() => {
    mocks.get.mockReset()
    mocks.post.mockReset()
  })

  it('uses the isolated credentialed Axios cookie-to-header configuration', () => {
    expect(mocks.config).toEqual(expect.objectContaining({
      withCredentials: true,
      withXSRFToken: true,
      xsrfCookieName: 'XSRF-TOKEN',
      xsrfHeaderName: 'X-XSRF-TOKEN',
    }))
  })

  it('primes the cookie before one POST and never copies the encoded response token', async () => {
    const responseToken = 'encoded-response-token'
    mocks.get.mockResolvedValue({
      data: { token: responseToken, headerName: 'X-XSRF-TOKEN' },
    })
    mocks.post.mockResolvedValue({ data: { message: 'Acknowledged' } })

    await submitPublicInquiry(inquiry)

    expect(mocks.get).toHaveBeenCalledTimes(1)
    expect(mocks.get).toHaveBeenCalledWith('/auth/csrf')
    expect(mocks.post).toHaveBeenCalledTimes(1)
    expect(mocks.post).toHaveBeenCalledWith('/public/leads', inquiry)
    expect(mocks.post.mock.calls.flat()).not.toContain(responseToken)
  })

  it('binds configuration and submission to the same canonical slug path', async () => {
    mocks.get
      .mockResolvedValueOnce({ data: { workspaceName: 'Acme', services: [] } })
      .mockResolvedValueOnce({ data: { token: 'valid-token', headerName: 'X-XSRF-TOKEN' } })
    mocks.post.mockResolvedValue({ data: { message: 'Acknowledged' } })

    await getPublicInquiryConfiguration('acme-consulting')
    await submitPublicInquiry(inquiry, 'acme-consulting')

    expect(mocks.get).toHaveBeenNthCalledWith(1,
      '/public/workspaces/acme-consulting/inquiry-config', { signal: undefined })
    expect(mocks.post).toHaveBeenCalledWith('/public/workspaces/acme-consulting/leads', inquiry)
  })

  it.each([
    { token: '', headerName: 'X-XSRF-TOKEN' },
    { token: 'encoded-response-token', headerName: 'Unexpected' },
  ])('does not POST when CSRF priming metadata is invalid', async (data) => {
    mocks.get.mockResolvedValue({ data })

    await expect(submitPublicInquiry(inquiry))
      .rejects.toThrow('Public inquiry security token could not be obtained')
    expect(mocks.post).not.toHaveBeenCalled()
  })
})
