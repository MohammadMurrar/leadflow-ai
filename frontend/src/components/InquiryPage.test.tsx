import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { PublicInquiryConfiguration } from '../types/publicInquiry'
import InquiryPage from './InquiryPage'

const api = vi.hoisted(() => ({
  configuration: vi.fn(),
  submit: vi.fn(),
}))

vi.mock('../services/publicInquiryApi', () => ({
  getPublicInquiryConfiguration: api.configuration,
  submitPublicInquiry: api.submit,
}))

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((done) => { resolve = done })
  return { promise, resolve }
}

function configuration(workspaceName: string): PublicInquiryConfiguration {
  return {
    workspaceName,
    description: `${workspaceName} description`,
    publicBrandName: workspaceName,
    publicTagline: null,
    publicLogoPath: null,
    currency: 'USD' as const,
    responseTimeText: 'One business day',
    privacyPolicyUrl: null,
    privacyNoticeText: null,
    privacyNoticeVersion: null,
    services: [{ id: '11111111-1111-4111-8111-111111111111', name: `${workspaceName} service` }],
  }
}

function view(workspaceSlug: string | null) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return {
    client,
    element: (
      <QueryClientProvider client={client}>
        <InquiryPage key={workspaceSlug ?? 'unavailable'} workspaceSlug={workspaceSlug} />
      </QueryClientProvider>
    ),
  }
}

describe('slug-selected public inquiry page', () => {
  beforeEach(() => {
    api.configuration.mockReset()
    api.submit.mockReset()
  })

  it('clears stale tenant state and ignores a late response after the slug changes', async () => {
    const a = deferred<ReturnType<typeof configuration>>()
    api.configuration.mockImplementation((slug: string) =>
      slug === 'leadflow-ai' ? a.promise : Promise.resolve(configuration('Workspace B')))
    const first = view('leadflow-ai')
    const rendered = render(first.element)

    expect(await screen.findByText('Loading inquiry details…')).toBeVisible()
    const second = view('acme-consulting')
    rendered.rerender(second.element)
    expect(await screen.findByText('Workspace B')).toBeVisible()
    expect(screen.getByRole('option', { name: 'Workspace B service' })).toBeInTheDocument()

    a.resolve(configuration('Workspace A'))
    await Promise.resolve()
    expect(screen.queryByText('Workspace A')).not.toBeInTheDocument()
    expect(screen.queryByRole('option', { name: 'Workspace A service' })).not.toBeInTheDocument()
  })

  it('shows a generic unavailable state without requesting configuration for an invalid slug', async () => {
    const invalid = view(null)
    render(invalid.element)

    expect(await screen.findByText('Inquiry details are unavailable')).toBeVisible()
    expect(api.configuration).not.toHaveBeenCalled()
    expect(screen.getByRole('button', { name: 'Send inquiry' })).toBeDisabled()
    expect(screen.queryByRole('button', { name: 'Retry' })).not.toBeInTheDocument()
  })

  it('renders public workspace branding and service names as text', async () => {
    const hostileText = '<img src=x onerror="alert(1)"><script>alert(2)</script>'
    const response = configuration(hostileText)
    response.publicTagline = hostileText
    api.configuration.mockResolvedValue(response)
    const rendered = render(view('leadflow-ai').element)

    expect(await screen.findAllByText(hostileText)).not.toHaveLength(0)
    expect(rendered.container.querySelector('script')).not.toBeInTheDocument()
    expect(rendered.container.querySelector('img')).not.toBeInTheDocument()
  })
})
