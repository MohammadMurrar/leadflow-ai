import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ReactNode } from 'react'
import { MemoryRouter, useLocation, useNavigate } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { captureResetToken, clearResetToken } from './auth/resetTokenVault'
import RootRoute from './RootRoute'

vi.mock('./auth/AuthProvider', () => ({
  default: ({ children, publicHome }: { children: ReactNode; publicHome?: ReactNode }) => <div data-testid="protected-provider">{publicHome ?? children}</div>,
}))
vi.mock('./App', () => ({ default: () => <div>Dashboard application</div> }))
vi.mock('./components/InquiryPage', () => ({
  default: ({ workspaceSlug }: { workspaceSlug?: string | null }) => (
    <div>Inquiry page {workspaceSlug === null ? 'unavailable' : workspaceSlug ?? 'legacy'}</div>
  ),
}))
vi.mock('./components/ForgotPasswordPage', () => ({ default: () => <div>Forgot password page</div> }))
vi.mock('./components/ResetPasswordPage', async () => {
  const vault = await import('./auth/resetTokenVault')
  return { default: () => <div>Reset page {vault.captureResetToken() ? 'ready' : 'invalid'}</div> }
})

function NavigationControls() {
  const navigate = useNavigate()
  const location = useLocation()
  return <><output data-testid="location">{location.pathname}</output><button onClick={() => navigate('/login')}>Login route</button><button onClick={() => navigate('/reset-password')}>Reset route</button><button onClick={() => navigate(-1)}>Back</button><button onClick={() => navigate(1)}>Forward</button></>
}

function renderRoot(path: string) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={queryClient}><MemoryRouter initialEntries={[path]}><NavigationControls /><RootRoute /></MemoryRouter></QueryClientProvider>)
}

beforeEach(() => clearResetToken())

describe('production root routing composition', () => {
  it.each([
    ['/forgot-password', 'Forgot password page'],
    ['/inquiry', 'Inquiry page legacy'],
    ['/inquiry/leadflow-ai', 'Inquiry page leadflow-ai'],
    ['/inquiry/acme-consulting', 'Inquiry page acme-consulting'],
  ])('keeps %s anonymous', async (path, content) => {
    renderRoot(path)
    expect(await screen.findByText(content)).toBeVisible()
    expect(screen.queryByTestId('protected-provider')).not.toBeInTheDocument()
  })

  it.each(['/inquiry/', '/inquiry/acme/other', '/inquiry/Acme', '/inquiry/acme%2Fother'])(
  'shows malformed public paths as unavailable anonymous inquiry routes: %s', async (path) => {
    renderRoot(path)
    expect(await screen.findByText('Inquiry page unavailable')).toBeVisible()
    expect(screen.queryByTestId('protected-provider')).not.toBeInTheDocument()
  })

  it('keeps reset anonymous and clears its token after genuine navigation away and back', async () => {
    window.history.replaceState(null, '', `/reset-password#token=${'A'.repeat(43)}`)
    renderRoot('/reset-password')
    expect(await screen.findByText('Reset page ready')).toBeVisible()
    expect(screen.queryByTestId('protected-provider')).not.toBeInTheDocument()

    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: 'Login route' }))
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/login'))
    await user.click(screen.getByRole('button', { name: 'Back' }))
    expect(await screen.findByText('Reset page invalid')).toBeVisible()
    expect(captureResetToken()).toBeNull()
    await user.click(screen.getByRole('button', { name: 'Forward' }))
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/login'))
  })

  it.each(['/login', '/', '/leads', '/settings'])('keeps %s behind the existing authentication provider', async (path) => {
    renderRoot(path)
    expect(await screen.findByTestId('protected-provider')).toBeInTheDocument()
  })

  it('shows a public homepage at root with sign-in, contact, and privacy actions', async () => {
    renderRoot('/')
    expect(await screen.findByRole('heading', { name: /respond faster.*convert more/i, level: 1 })).toBeVisible()
    expect(screen.getAllByRole('link', { name: /sign in/i }).length).toBeGreaterThan(0)
    expect(screen.getByRole('link', { name: /talk to murravo/i })).toHaveAttribute('href', 'mailto:accounts@murravo.com?subject=Murravo%20pilot%20inquiry')
    expect(screen.getAllByRole('link', { name: 'Privacy' }).length).toBeGreaterThan(0)
    expect(screen.queryByText('Dashboard application')).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /sign up|trial/i })).not.toBeInTheDocument()
    expect(screen.getByText('From inquiry to the next decision.')).toBeVisible()
    expect(screen.getByText('Illustrative workspace data')).toBeVisible()
    expect(screen.getByText('Inquiries and qualification')).toBeVisible()
    expect(screen.getByText('Lead performance')).toBeVisible()
    expect(document.querySelector('.hero-story')).not.toBeInTheDocument()
    expect(document.querySelectorAll('[data-reveal]')).toHaveLength(0)
  })

  it('keeps the homepage meaningful without animation or JavaScript reveal enhancement', async () => {
    renderRoot('/')
    expect(await screen.findByText('A clear path through every inquiry.')).toBeVisible()
    expect(document.querySelector('.public-home')).not.toHaveClass('is-enhanced')

    const css = readFileSync(resolve('src/components/homePage.css'), 'utf8')
    expect(css).toMatch(/@media \(prefers-reduced-motion: reduce\)\s*\{[^}]*animation: none !important;[^}]*transition: none !important;/)
    expect(css).toMatch(/\.home-overview-lead, \.home-chart-series, \.home-workflow-events li \{ opacity: 1 !important; transform: none !important; \}/)
  })

  it('lands on the contact section for a direct homepage hash link', async () => {
    const scrollIntoView = vi.fn()
    const original = Element.prototype.scrollIntoView
    Element.prototype.scrollIntoView = scrollIntoView
    try {
      renderRoot('/#contact')
      await waitFor(() => expect(scrollIntoView).toHaveBeenCalledOnce())
    } finally {
      Element.prototype.scrollIntoView = original
    }
  })

  it('renders privacy and unknown paths publicly with correct titles', async () => {
    const privacy = renderRoot('/privacy')
    expect(await screen.findByRole('heading', { name: 'Privacy at Murravo' })).toBeVisible()
    expect(screen.getByText('Mohammad Murrar')).toBeVisible()
    expect(screen.getAllByText('accounts@murravo.com').length).toBeGreaterThan(0)
    for (const name of ['Information submitted through inquiry forms', 'Administrator accounts and sessions', 'Leads and qualification', 'Retention', 'Security', 'Service providers and location', 'Your choices and requests', 'Children', 'Changes']) {
      expect(screen.getByRole('heading', { name, level: 2 })).toBeVisible()
    }
    expect(screen.queryByText(/SOC 2|ISO 27001|GDPR compliant/i)).not.toBeInTheDocument()
    expect(document.title).toBe('Privacy | Murravo')
    privacy.unmount()
    renderRoot('/admin/unknown')
    expect(await screen.findByRole('heading', { name: 'Page not found' })).toBeVisible()
    expect(document.title).toBe('Page not found | Murravo')
    expect(screen.queryByTestId('protected-provider')).not.toBeInTheDocument()
  })
})
