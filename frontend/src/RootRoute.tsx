import { lazy, useEffect } from 'react'
import { useLocation } from 'react-router-dom'
import AuthProvider from './auth/AuthProvider'
import { clearResetToken } from './auth/resetTokenVault'
import RouteLoadBoundary from './components/RouteLoadBoundary'
import { getInquiryWorkspaceSlug } from './publicInquiryRoute'

export const AuthenticatedApp = lazy(() => import('./App'))
const InquiryPage = lazy(() => import('./components/InquiryPage'))
const ForgotPasswordPage = lazy(() => import('./components/ForgotPasswordPage'))
const ResetPasswordPage = lazy(() => import('./components/ResetPasswordPage'))
export default function RootRoute() {
  const location = useLocation()

  useEffect(() => {
    if (location.pathname !== '/reset-password') clearResetToken()
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

  return <AuthProvider><RouteLoadBoundary mode="full" resetKey={location.pathname}><AuthenticatedApp /></RouteLoadBoundary></AuthProvider>
}
