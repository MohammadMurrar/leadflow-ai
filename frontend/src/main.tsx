import { lazy, StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter } from 'react-router-dom'
import { useLocation } from 'react-router-dom'
import './index.css'
import AuthProvider from './auth/AuthProvider.tsx'
import RouteLoadBoundary from './components/RouteLoadBoundary.tsx'

export const AuthenticatedApp = lazy(() => import('./App.tsx'))
const InquiryPage = lazy(() => import('./components/InquiryPage.tsx'))

const queryClient = new QueryClient({
    defaultOptions: {
        queries: {
            staleTime: 30_000,
            retry: 1,
            refetchOnWindowFocus: false,
        },
    },
})

function RootRoute() {
    const location = useLocation()

    if (location.pathname === '/inquiry') {
        return (
            <RouteLoadBoundary mode="full" resetKey={location.pathname}>
                <InquiryPage />
            </RouteLoadBoundary>
        )
    }

    return (
        <AuthProvider>
            <RouteLoadBoundary mode="full" resetKey={location.pathname}>
                <AuthenticatedApp />
            </RouteLoadBoundary>
        </AuthProvider>
    )
}

createRoot(document.getElementById('root')!).render(
    <StrictMode>
        <QueryClientProvider client={queryClient}>
            <BrowserRouter>
                <RootRoute />
            </BrowserRouter>
        </QueryClientProvider>
    </StrictMode>,
)
