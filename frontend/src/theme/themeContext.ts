import { createContext, useContext } from 'react'
import type { ThemePreference } from './theme'

export type ThemeContextValue = {
  preference: ThemePreference
  resolved: 'light' | 'dark'
  setPreference: (value: ThemePreference) => void
}

export type ThemeView = 'public' | 'authentication' | 'authenticated-app'

export const ThemeContext = createContext<ThemeContextValue | null>(null)
export const ThemeViewContext = createContext<(view: ThemeView) => void>(() => undefined)

export function useTheme() {
  const theme = useContext(ThemeContext)
  if (!theme) throw new Error('ThemeProvider is required')
  return theme
}

export function useThemeView() {
  return useContext(ThemeViewContext)
}
