import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, useLocation } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import AuthProvider from './AuthProvider'
import { getCurrentUser, login, logout, obtainCsrfToken } from './authApi'
import { getPublicInquiryConfiguration } from '../services/publicInquiryApi'
import { ThemeProvider } from '../theme/ThemeProvider'
import { useAuth } from './auth'

vi.mock('./authApi', () => ({
  getCurrentUser: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
  obtainCsrfToken: vi.fn(),
}))
vi.mock('../services/publicInquiryApi', () => ({
  getPublicInquiryConfiguration: vi.fn().mockRejectedValue(new Error('branding unavailable')),
}))

const currentUserMock = vi.mocked(getCurrentUser)
const loginMock = vi.mocked(login)
const logoutMock = vi.mocked(logout)
const csrfMock = vi.mocked(obtainCsrfToken)
const brandingMock = vi.mocked(getPublicInquiryConfiguration)
const unauthorized = (status: number) => ({ isAxiosError: true, response: { status } })

function Destination() {
  const location = useLocation()
  return <div>Authenticated destination: {location.pathname}</div>
}

function SignOutDestination() {
  const { signOut } = useAuth()
  return <><Destination /><button onClick={() => void signOut()}>Sign out in test</button></>
}

function renderThemedProvider(path = '/', destination = <Destination />) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={queryClient}><MemoryRouter initialEntries={[path]}><ThemeProvider>
    <AuthProvider publicHome={<main>Public Murravo home</main>}>{destination}</AuthProvider>
  </ThemeProvider></MemoryRouter></QueryClientProvider>)
}

function renderProvider(initialEntry: string | { pathname: string; state: unknown } = '/login') {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={queryClient}><MemoryRouter initialEntries={[initialEntry]}><AuthProvider><Destination /></AuthProvider></MemoryRouter></QueryClientProvider>)
}

beforeEach(() => {
  vi.clearAllMocks()
  csrfMock.mockResolvedValue({ token: 'synthetic-csrf', headerName: 'X-XSRF-TOKEN' })
  currentUserMock.mockRejectedValue(unauthorized(401))
  brandingMock.mockRejectedValue(new Error('branding unavailable'))
})

afterEach(() => {
  localStorage.removeItem('murravo.theme')
  vi.unstubAllGlobals()
})

describe('real authentication provider regression', () => {
  it('shows anonymous root in light before and after restoration without changing a dark preference', async () => {
    localStorage.setItem('murravo.theme', 'dark')
    renderThemedProvider()
    expect(document.documentElement.dataset.theme).toBe('light')
    expect(document.documentElement.style.colorScheme).toBe('light')
    expect(await screen.findByText('Public Murravo home')).toBeVisible()
    expect(document.documentElement.dataset.theme).toBe('light')
    expect(localStorage.getItem('murravo.theme')).toBe('dark')
  })

  it.each(['dark', 'light'])('uses the administrator %s preference for authenticated root after restoration', async (preference) => {
    localStorage.setItem('murravo.theme', preference)
    currentUserMock.mockResolvedValue({ id: 'user-id', workspaceId: 'workspace-id', email: 'admin@example.invalid', displayName: 'Admin', role: 'ADMIN' })
    renderThemedProvider()
    expect(await screen.findByText('Authenticated destination: /')).toBeVisible()
    expect(document.documentElement.dataset.theme).toBe(preference)
    expect(document.documentElement.style.colorScheme).toBe(preference)
    expect(screen.queryByText('Public Murravo home')).not.toBeInTheDocument()
    expect(localStorage.getItem('murravo.theme')).toBe(preference)
  })

  it.each([true, false])('resolves authenticated root against System dark=%s', async (dark) => {
    vi.stubGlobal('matchMedia', vi.fn(() => ({ matches: dark, addEventListener: vi.fn(), removeEventListener: vi.fn() })))
    currentUserMock.mockResolvedValue({ id: 'user-id', workspaceId: 'workspace-id', email: 'admin@example.invalid', displayName: 'Admin', role: 'ADMIN' })
    renderThemedProvider()
    expect(await screen.findByText('Authenticated destination: /')).toBeVisible()
    expect(document.documentElement.dataset.theme).toBe(dark ? 'dark' : 'light')
    expect(localStorage.getItem('murravo.theme')).toBeNull()
  })

  it('applies saved Dark on successful login without darkening the public login view', async () => {
    localStorage.setItem('murravo.theme', 'dark')
    loginMock.mockResolvedValue({ id: 'user-id', workspaceId: 'workspace-id', email: 'admin@example.invalid', displayName: 'Admin', role: 'ADMIN' })
    const user = userEvent.setup()
    renderThemedProvider('/login')
    await screen.findByLabelText('Email address')
    expect(document.documentElement.dataset.theme).toBe('light')
    await user.type(screen.getByLabelText('Email address'), 'admin@example.invalid')
    await user.type(screen.getByLabelText('Password'), 'Synthetic-password-42!')
    await user.click(screen.getByRole('button', { name: 'Sign in securely' }))
    expect(await screen.findByText('Authenticated destination: /')).toBeVisible()
    expect(document.documentElement.dataset.theme).toBe('dark')
    expect(localStorage.getItem('murravo.theme')).toBe('dark')
  })

  it('returns to light login on logout without erasing saved Dark', async () => {
    localStorage.setItem('murravo.theme', 'dark')
    currentUserMock.mockResolvedValue({ id: 'user-id', workspaceId: 'workspace-id', email: 'admin@example.invalid', displayName: 'Admin', role: 'ADMIN' })
    logoutMock.mockResolvedValue(undefined)
    const user = userEvent.setup()
    renderThemedProvider('/settings', <SignOutDestination />)
    expect(await screen.findByText('Authenticated destination: /settings')).toBeVisible()
    expect(document.documentElement.dataset.theme).toBe('dark')
    await user.click(screen.getByRole('button', { name: 'Sign out in test' }))
    expect(await screen.findByLabelText('Email address')).toBeVisible()
    expect(document.documentElement.dataset.theme).toBe('light')
    expect(localStorage.getItem('murravo.theme')).toBe('dark')
  })

  it('returns a failed protected session restoration to light login', async () => {
    localStorage.setItem('murravo.theme', 'dark')
    renderThemedProvider('/settings')
    expect(await screen.findByLabelText('Email address')).toBeVisible()
    expect(document.documentElement.dataset.theme).toBe('light')
    expect(screen.queryByText('Authenticated destination: /settings')).not.toBeInTheDocument()
    expect(localStorage.getItem('murravo.theme')).toBe('dark')
  })

  it('shows the public home after an anonymous session check without exposing private content', async () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(<QueryClientProvider client={queryClient}><MemoryRouter initialEntries={['/']}>
      <AuthProvider publicHome={<main>Public Murravo home</main>}><Destination /></AuthProvider>
    </MemoryRouter></QueryClientProvider>)
    expect(await screen.findByText('Public Murravo home')).toBeVisible()
    expect(screen.queryByText('Authenticated destination: /')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Email address')).not.toBeInTheDocument()
  })

  it('keeps bootstrap and branding failures out of login-submission feedback', async () => {
    csrfMock.mockRejectedValueOnce(new Error('bootstrap unavailable'))
    loginMock.mockRejectedValue(unauthorized(401))
    const user = userEvent.setup()
    renderProvider()

    await screen.findByLabelText('Email address')
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(screen.queryByText('Unable to connect securely. Please try again.')).not.toBeInTheDocument()
    expect(screen.queryByText('Invalid email or password.')).not.toBeInTheDocument()

    csrfMock.mockResolvedValue({ token: 'synthetic-csrf', headerName: 'X-XSRF-TOKEN' })
    await user.type(screen.getByLabelText('Email address'), 'admin@example.invalid')
    await user.type(screen.getByLabelText('Password'), 'Synthetic-password-42!')
    await user.click(screen.getByRole('button', { name: 'Sign in securely' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid email or password.')
  })

  it('preserves the login request and successful default destination', async () => {
    loginMock.mockResolvedValue({ id: 'user-id', workspaceId: 'workspace-id', email: 'admin@example.invalid', displayName: 'Admin', role: 'ADMIN' })
    const storage = vi.spyOn(Storage.prototype, 'setItem')
    const log = vi.spyOn(console, 'log').mockImplementation(() => undefined)
    const user = userEvent.setup()
    renderProvider('/login')

    await screen.findByLabelText('Email address')
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    await user.type(screen.getByLabelText('Email address'), 'admin@example.invalid')
    await user.type(screen.getByLabelText('Password'), 'Synthetic-password-42!')
    await user.click(screen.getByRole('button', { name: 'Sign in securely' }))

    expect(loginMock).toHaveBeenCalledWith({ email: 'admin@example.invalid', password: 'Synthetic-password-42!' })
    expect(await screen.findByText('Authenticated destination: /')).toBeVisible()
    expect(storage).not.toHaveBeenCalled()
    expect(log).not.toHaveBeenCalled()
  })

  it('shows only the fixed infrastructure message for a deliberate login failure', async () => {
    loginMock.mockRejectedValue(new Error('private infrastructure detail'))
    const user = userEvent.setup()
    renderProvider()
    await user.type(await screen.findByLabelText('Email address'), 'admin@example.invalid')
    await user.type(screen.getByLabelText('Password'), 'Synthetic-password-42!')
    await user.click(screen.getByRole('button', { name: 'Sign in securely' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Unable to connect securely. Please try again.')
    expect(document.body).not.toHaveTextContent('private infrastructure detail')
  })

  it('clears stale submission feedback when the next deliberate login succeeds', async () => {
    loginMock.mockRejectedValueOnce(unauthorized(401)).mockResolvedValueOnce({ id: 'user-id', workspaceId: 'workspace-id', email: 'admin@example.invalid', displayName: 'Admin', role: 'ADMIN' })
    const user = userEvent.setup()
    renderProvider()
    await user.type(await screen.findByLabelText('Email address'), 'admin@example.invalid')
    await user.type(screen.getByLabelText('Password'), 'Synthetic-password-42!')
    await user.click(screen.getByRole('button', { name: 'Sign in securely' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid email or password.')
    await user.type(screen.getByLabelText('Password'), 'Second-password-42!')
    await user.click(screen.getByRole('button', { name: 'Sign in securely' }))
    expect(await screen.findByText('Authenticated destination: /')).toBeVisible()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('keeps invalid credentials generic and releases the lock after handled rejection', async () => {
    loginMock.mockRejectedValue(unauthorized(401))
    const user = userEvent.setup()
    renderProvider()
    const email = await screen.findByLabelText('Email address')
    await user.type(email, 'admin@example.invalid')
    await user.type(screen.getByLabelText('Password'), 'Synthetic-password-42!')
    await user.click(screen.getByRole('button', { name: 'Sign in securely' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid email or password.')

    await user.type(screen.getByLabelText('Password'), 'Second-password-42!')
    await user.click(screen.getByRole('button', { name: 'Sign in securely' }))
    await waitFor(() => expect(loginMock).toHaveBeenCalledTimes(2))
  })

  it('releases the synchronous submission lock after a synchronous boundary exception', async () => {
    loginMock.mockImplementationOnce(() => { throw new Error('synthetic failure') }).mockRejectedValueOnce(unauthorized(401))
    const user = userEvent.setup()
    renderProvider()
    await user.type(await screen.findByLabelText('Email address'), 'admin@example.invalid')
    await user.type(screen.getByLabelText('Password'), 'Synthetic-password-42!')
    await user.click(screen.getByRole('button', { name: 'Sign in securely' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Unable to connect securely. Please try again.')
    await user.type(screen.getByLabelText('Password'), 'Second-password-42!')
    await user.click(screen.getByRole('button', { name: 'Sign in securely' }))
    await waitFor(() => expect(loginMock).toHaveBeenCalledTimes(2))
  })
})
