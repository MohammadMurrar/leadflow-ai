import { lazy, StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter } from 'react-router-dom'
import './index.css'
import AuthProvider from './auth/AuthProvider.tsx'
import RouteLoadBoundary from './components/RouteLoadBoundary.tsx'

export const AuthenticatedApp = lazy(() => import('./App.tsx'))

const queryClient = new QueryClient({
    defaultOptions: {
        queries: {
            staleTime: 30_000,
            retry: 1,
            refetchOnWindowFocus: false,
        },
    },
})

createRoot(document.getElementById('root')!).render(
    <StrictMode>
        <QueryClientProvider client={queryClient}>
            <BrowserRouter>
                <AuthProvider>
                    <RouteLoadBoundary mode="full">
                        <AuthenticatedApp />
                    </RouteLoadBoundary>
                </AuthProvider>
            </BrowserRouter>
        </QueryClientProvider>
    </StrictMode>,
)
