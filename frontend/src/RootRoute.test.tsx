import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ReactNode } from 'react'
import { MemoryRouter, useLocation, useNavigate } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { captureResetToken, clearResetToken } from './auth/resetTokenVault'
import RootRoute from './RootRoute'

vi.mock('./auth/AuthProvider', () => ({
  default: ({ children }: { children: ReactNode }) => <div data-testid="protected-provider">{children}</div>,
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

  it.each(['/login', '/', '/unknown'])('keeps %s behind the existing authentication provider', async (path) => {
    renderRoot(path)
    expect(await screen.findByTestId('protected-provider')).toBeInTheDocument()
  })
})
