export type ThemePreference = 'system' | 'light' | 'dark'

export const THEME_STORAGE_KEY = 'murravo.theme'

const authenticatedAppRoutes = new Set(['/', '/leads', '/ai-qualification', '/analytics', '/services', '/settings'])

export function isAuthenticatedAppRoute(pathname: string): boolean {
  return authenticatedAppRoutes.has(pathname)
}

export function readThemePreference(): ThemePreference {
  try {
    const value = window.localStorage.getItem(THEME_STORAGE_KEY)
    return value === 'light' || value === 'dark' ? value : 'system'
  } catch {
    return 'system'
  }
}

export function resolveTheme(preference: ThemePreference, systemDark: boolean): 'light' | 'dark' {
  return preference === 'system' ? (systemDark ? 'dark' : 'light') : preference
}
