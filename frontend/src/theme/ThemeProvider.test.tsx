import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, useLocation, useNavigate } from 'react-router-dom'
import { ThemeProvider } from './ThemeProvider'
import { useTheme, useThemeView } from './themeContext'
import { readThemePreference, THEME_STORAGE_KEY } from './theme'

let systemDark = false
let systemListener: ((event: MediaQueryListEvent) => void) | undefined

function Probe() {
  const { pathname } = useLocation()
  const navigate = useNavigate()
  const setView = useThemeView()
  const { preference, resolved, setPreference } = useTheme()
  return <>
    <output>{pathname} {preference}:{resolved}</output>
    <button onClick={() => setView('authenticated-app')}>Authenticated app</button>
    <button onClick={() => setView('authentication')}>Authentication view</button>
    <button onClick={() => setView('public')}>Public view</button>
    <button onClick={() => setPreference('light')}>Light preference</button>
    <button onClick={() => setPreference('dark')}>Dark preference</button>
    <button onClick={() => setPreference('system')}>System preference</button>
    <button onClick={() => navigate('/privacy')}>Privacy route</button>
    <button onClick={() => navigate('/login')}>Login route</button>
    <button onClick={() => navigate('/')}>Root route</button>
  </>
}

function renderTheme(path = '/') {
  return render(<MemoryRouter initialEntries={[path]}><ThemeProvider><Probe /></ThemeProvider></MemoryRouter>)
}

beforeEach(() => {
  localStorage.clear()
  systemDark = false
  systemListener = undefined
  vi.stubGlobal('matchMedia', vi.fn(() => ({
    matches: systemDark,
    addEventListener: (_: string, callback: (event: MediaQueryListEvent) => void) => { systemListener = callback },
    removeEventListener: vi.fn(),
  })))
})

describe('public-first theme boundary', () => {
  it.each(['/', '/privacy', '/login', '/forgot-password', '/reset-password', '/inquiry', '/inquiry/acme', '/missing', '/signup', '/email-verification', '/invitation/accept', '/one-time-code'])('keeps public %s light with stored Dark and preserves the preference', (path) => {
    localStorage.setItem(THEME_STORAGE_KEY, 'dark')
    const write = vi.spyOn(Storage.prototype, 'setItem')
    const remove = vi.spyOn(Storage.prototype, 'removeItem')
    renderTheme(path)
    expect(document.documentElement.dataset.theme).toBe('light')
    expect(document.documentElement.style.colorScheme).toBe('light')
    expect(screen.getByText(`${path} dark:dark`)).toBeVisible()
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe('dark')
    expect(write).not.toHaveBeenCalled()
    expect(remove).not.toHaveBeenCalled()
    write.mockRestore()
    remove.mockRestore()
  })

  it.each(['/', '/privacy', '/login', '/forgot-password', '/reset-password', '/inquiry', '/inquiry/acme', '/missing'])('keeps public %s light under System-dark', (path) => {
    systemDark = true
    renderTheme(path)
    expect(screen.getByText(`${path} system:dark`)).toBeVisible()
    expect(document.documentElement.dataset.theme).toBe('light')
    expect(document.documentElement.style.colorScheme).toBe('light')
  })

  it.each(['dark', 'light'])('applies stored %s only after an authenticated app view is reported', async (preference) => {
    localStorage.setItem(THEME_STORAGE_KEY, preference)
    const user = userEvent.setup()
    renderTheme('/')
    expect(document.documentElement.dataset.theme).toBe('light')
    await user.click(screen.getByRole('button', { name: 'Authenticated app' }))
    expect(document.documentElement.dataset.theme).toBe(preference)
    expect(document.documentElement.style.colorScheme).toBe(preference)
    await user.click(screen.getByRole('button', { name: 'Authentication view' }))
    expect(document.documentElement.dataset.theme).toBe('light')
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe(preference)
  })

  it.each([true, false])('resolves authenticated System with OS dark=%s', async (dark) => {
    systemDark = dark
    const user = userEvent.setup()
    renderTheme('/settings')
    expect(document.documentElement.dataset.theme).toBe('light')
    await user.click(screen.getByRole('button', { name: 'Authenticated app' }))
    expect(document.documentElement.dataset.theme).toBe(dark ? 'dark' : 'light')
    act(() => systemListener?.({ matches: !dark } as MediaQueryListEvent))
    expect(document.documentElement.dataset.theme).toBe(dark ? 'light' : 'dark')
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBeNull()
  })

  it('keeps public navigation and cross-tab changes light, then reuses the preference in the app', async () => {
    localStorage.setItem(THEME_STORAGE_KEY, 'dark')
    const user = userEvent.setup()
    renderTheme('/')
    await user.click(screen.getByRole('button', { name: 'Privacy route' }))
    expect(document.documentElement.dataset.theme).toBe('light')
    await user.click(screen.getByRole('button', { name: 'Login route' }))
    expect(document.documentElement.dataset.theme).toBe('light')
    localStorage.setItem(THEME_STORAGE_KEY, 'light')
    act(() => window.dispatchEvent(new StorageEvent('storage', { key: THEME_STORAGE_KEY, newValue: 'light' })))
    expect(await screen.findByText('/login light:light')).toBeVisible()
    expect(document.documentElement.dataset.theme).toBe('light')
    await user.click(screen.getByRole('button', { name: 'Root route' }))
    await user.click(screen.getByRole('button', { name: 'Authenticated app' }))
    expect(document.documentElement.dataset.theme).toBe('light')
    localStorage.setItem(THEME_STORAGE_KEY, 'dark')
    act(() => window.dispatchEvent(new StorageEvent('storage', { key: THEME_STORAGE_KEY, newValue: 'dark' })))
    expect(await screen.findByText('/ dark:dark')).toBeVisible()
    expect(document.documentElement.dataset.theme).toBe('dark')
    await user.click(screen.getByRole('button', { name: 'Privacy route' }))
    expect(document.documentElement.dataset.theme).toBe('light')
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe('dark')
  })

  it('keeps future public routes light even if an app view marker is stale', async () => {
    localStorage.setItem(THEME_STORAGE_KEY, 'dark')
    const user = userEvent.setup()
    renderTheme('/')
    await user.click(screen.getByRole('button', { name: 'Authenticated app' }))
    expect(document.documentElement.dataset.theme).toBe('dark')
    await user.click(screen.getByRole('button', { name: 'Privacy route' }))
    expect(document.documentElement.dataset.theme).toBe('light')
  })

  it('handles invalid or unavailable storage without darkening a public page', async () => {
    localStorage.setItem(THEME_STORAGE_KEY, 'invalid')
    expect(readThemePreference()).toBe('system')
    const read = vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => { throw new Error('blocked') })
    expect(readThemePreference()).toBe('system')
    renderTheme('/privacy')
    await waitFor(() => expect(document.documentElement.dataset.theme).toBe('light'))
    read.mockRestore()
  })
})
