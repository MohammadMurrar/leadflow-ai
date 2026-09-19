import { useEffect, useLayoutEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { useLocation } from 'react-router-dom'
import { isAuthenticatedAppRoute, readThemePreference, resolveTheme, THEME_STORAGE_KEY } from './theme'
import type { ThemePreference } from './theme'
import { ThemeContext, ThemeViewContext } from './themeContext'
import type { ThemeView } from './themeContext'

export function ThemeProvider({ children }: { children: ReactNode }) {
  const { pathname } = useLocation()
  const [preference, setPreference] = useState(readThemePreference)
  const [systemDark, setSystemDark] = useState(() => window.matchMedia?.('(prefers-color-scheme: dark)').matches ?? false)
  const [view, setView] = useState<ThemeView>('public')
  const resolved = resolveTheme(preference, systemDark)
  const displayedTheme = view === 'authenticated-app' && isAuthenticatedAppRoute(pathname) ? resolved : 'light'

  useEffect(() => {
    const media = window.matchMedia?.('(prefers-color-scheme: dark)')
    if (!media) return
    const onChange = (event: MediaQueryListEvent) => setSystemDark(event.matches)
    media.addEventListener('change', onChange)
    return () => media.removeEventListener('change', onChange)
  }, [])

  useEffect(() => {
    const onStorage = (event: StorageEvent) => {
      if (event.key === THEME_STORAGE_KEY || event.key === null) setPreference(readThemePreference())
    }
    window.addEventListener('storage', onStorage)
    return () => window.removeEventListener('storage', onStorage)
  }, [])

  useLayoutEffect(() => {
    document.documentElement.dataset.theme = displayedTheme
    document.documentElement.style.colorScheme = displayedTheme
  }, [displayedTheme])

  function changePreference(value: ThemePreference) {
    setPreference(value)
    try {
      if (value === 'system') window.localStorage.removeItem(THEME_STORAGE_KEY)
      else window.localStorage.setItem(THEME_STORAGE_KEY, value)
    } catch {
      // Presentation remains usable when storage is unavailable.
    }
  }

  return <ThemeContext.Provider value={{ preference, resolved, setPreference: changePreference }}>
    <ThemeViewContext.Provider value={setView}>{children}</ThemeViewContext.Provider>
  </ThemeContext.Provider>
}
