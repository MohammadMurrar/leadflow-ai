import { lazy, useEffect } from 'react'
import { useLocation } from 'react-router-dom'
import AuthProvider from './auth/AuthProvider'
import { clearResetToken } from './auth/resetTokenVault'
import RouteLoadBoundary from './components/RouteLoadBoundary'
import { getInquiryWorkspaceSlug } from './publicInquiryRoute'
import { HomePage, NotFoundPage, PrivacyPage } from './components/PublicPages'

export const AuthenticatedApp = lazy(() => import('./App'))
const InquiryPage = lazy(() => import('./components/InquiryPage'))
const ForgotPasswordPage = lazy(() => import('./components/ForgotPasswordPage'))
const ResetPasswordPage = lazy(() => import('./components/ResetPasswordPage'))
export default function RootRoute() {
  const location = useLocation()

  useEffect(() => {
    if (location.pathname !== '/reset-password') clearResetToken()
  }, [location.pathname])

  useEffect(() => {
    const titles: Record<string, string> = {
      '/': 'Murravo',
      '/privacy': 'Privacy | Murravo',
      '/login': 'Sign in | Murravo',
      '/forgot-password': 'Reset request | Murravo',
      '/reset-password': 'Reset password | Murravo',
      '/leads': 'Leads | Murravo',
      '/ai-qualification': 'AI Qualification | Murravo',
      '/analytics': 'Analytics | Murravo',
      '/services': 'Services | Murravo',
      '/settings': 'Settings | Murravo',
    }
    document.title = location.pathname.startsWith('/inquiry') ? 'Inquiry | Murravo'
      : titles[location.pathname] ?? 'Page not found | Murravo'
  }, [location.pathname])

  const inquiryWorkspaceSlug = getInquiryWorkspaceSlug(location.pathname)
  if (location.pathname === '/inquiry' || location.pathname.startsWith('/inquiry/')) {
    return <RouteLoadBoundary mode="full" resetKey={location.pathname}>
      <InquiryPage key={location.pathname} workspaceSlug={inquiryWorkspaceSlug} />
    </RouteLoadBoundary>
  }

  if (location.pathname === '/forgot-password') {
    return <RouteLoadBoundary mode="full" resetKey={location.pathname}><ForgotPasswordPage /></RouteLoadBoundary>
  }

  if (location.pathname === '/reset-password') {
    return <RouteLoadBoundary mode="full" resetKey={location.pathname}><ResetPasswordPage key={location.key} /></RouteLoadBoundary>
  }

  if (location.pathname === '/privacy') return <PrivacyPage />
  if (!['/', '/login', '/leads', '/ai-qualification', '/analytics', '/services', '/settings'].includes(location.pathname)) return <NotFoundPage />

  return <AuthProvider publicHome={location.pathname === '/' ? <HomePage /> : undefined}>
    <RouteLoadBoundary mode="full" resetKey={location.pathname}><AuthenticatedApp /></RouteLoadBoundary>
  </AuthProvider>
}
