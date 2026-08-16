import { useQueryClient } from '@tanstack/react-query'
import axios from 'axios'
import { useCallback, useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import { getCurrentUser, login, logout, obtainCsrfToken } from './authApi'
import { AuthContext } from './auth'
import type { AuthenticatedUser, LoginRequest } from './auth'
import { setUnauthorizedHandler } from '../services/leadApi'
import LoginPage from '../components/LoginPage'

export default function AuthProvider({ children }: { children: ReactNode }) {
    const [user, setUser] = useState<AuthenticatedUser | null>(null)
    const [initializing, setInitializing] = useState(true)
    const [loginPending, setLoginPending] = useState(false)
    const [loginError, setLoginError] = useState<string | null>(null)
    const queryClient = useQueryClient()
    const location = useLocation()
    const navigate = useNavigate()

    const expireSession = useCallback(() => {
        setUser(null)
        queryClient.clear()
    }, [queryClient])

    useEffect(() => {
        setUnauthorizedHandler(expireSession)
        return () => setUnauthorizedHandler(undefined)
    }, [expireSession])

    useEffect(() => {
        let active = true
        async function initialize() {
            try {
                await obtainCsrfToken()
                const currentUser = await getCurrentUser()
                if (active) setUser(currentUser)
            } catch (error) {
                if (active && (!axios.isAxiosError(error) || error.response?.status !== 401)) {
                    setLoginError('Unable to connect securely. Please try again.')
                }
            } finally {
                if (active) setInitializing(false)
            }
        }
        void initialize()
        return () => { active = false }
    }, [])

    async function handleLogin(request: LoginRequest) {
        setLoginPending(true)
        setLoginError(null)
        try {
            await obtainCsrfToken()
            const authenticated = await login(request)
            await obtainCsrfToken()
            setUser(authenticated)
            const destination = location.state && typeof location.state === 'object'
                && 'from' in location.state && typeof location.state.from === 'string'
                && location.state.from.startsWith('/') && !location.state.from.startsWith('//')
                ? location.state.from : '/'
            navigate(destination, { replace: true, state: null })
        } catch (error) {
            setLoginError(axios.isAxiosError(error) && error.response?.status === 401
                ? 'Invalid email or password.'
                : 'Sign in failed. Please try again.')
        } finally {
            setLoginPending(false)
        }
    }

    async function signOut() {
        try {
            await logout()
        } finally {
            expireSession()
            try { await obtainCsrfToken() } catch { /* Login page can retry securely. */ }
            navigate('/login', { replace: true })
        }
    }

    if (initializing) {
        return <div className="flex min-h-screen items-center justify-center bg-slate-50 text-sm font-medium text-slate-500" role="status">Checking your secure session…</div>
    }

    if (!user) {
        if (location.pathname !== '/login') {
            return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />
        }
        return <LoginPage onSubmit={handleLogin} isPending={loginPending} error={loginError} />
    }

    if (location.pathname === '/login') return <Navigate to="/" replace />

    return <AuthContext.Provider value={{ user, signOut }}>{children}</AuthContext.Provider>
}
