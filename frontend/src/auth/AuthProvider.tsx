import { QueryClientProvider, useQueryClient } from '@tanstack/react-query'
import axios from 'axios'
import { useCallback, useEffect, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import { getCurrentUser, login, logout, obtainCsrfToken } from './authApi'
import { AuthContext } from './auth'
import type { LoginRequest } from './auth'
import { setUnauthorizedHandler } from '../services/leadApi'
import LoginPage from '../components/LoginPage'
import { createPrivateSession, removeUnscopedPrivateData } from './privateSession'
import type { PrivateSession } from './privateSession'

export default function AuthProvider({ children }: { children: ReactNode }) {
    const [session, setSession] = useState<PrivateSession | null>(null)
    const current = useRef<PrivateSession | null>(null)
    const generation = useRef(0)
    const channel = useRef<BroadcastChannel | null>(null)
    const user = session?.user
    const [initializing, setInitializing] = useState(true)
    const [loginPending, setLoginPending] = useState(false)
    const [loginError, setLoginError] = useState<string | null>(null)
    const queryClient = useQueryClient()
    const location = useLocation()
    const navigate = useNavigate()

    const clearIdentity = useCallback(() => {
        generation.current += 1
        current.current?.close()
        current.current = null
        removeUnscopedPrivateData(queryClient)
        setSession(null)
        return generation.current
    }, [queryClient])

    const expireSession = useCallback(() => {
        clearIdentity()
        setInitializing(false)
        channel.current?.postMessage('logout')
    }, [clearIdentity])

    const restore = useCallback(async () => {
        const version = clearIdentity()
        setInitializing(true)
        try {
            await obtainCsrfToken()
            if (version !== generation.current) return
            const authenticated = await getCurrentUser()
            if (version !== generation.current) return
            const restored = createPrivateSession(authenticated)
            current.current = restored
            setSession(restored)
        } catch {
            // The old identity was retired before attempting restoration.
        } finally {
            if (version === generation.current) setInitializing(false)
        }
    }, [clearIdentity])

    useEffect(() => {
        setUnauthorizedHandler(expireSession)
        return () => setUnauthorizedHandler(undefined)
    }, [expireSession])

    useEffect(() => {
        const messages = typeof BroadcastChannel === 'undefined' ? null : new BroadcastChannel('leadflow-session')
        channel.current = messages
        if (messages) messages.onmessage = event => {
            if (event.data === 'logout') {
                clearIdentity()
                setInitializing(false)
            } else if (event.data === 'changed') void restore()
        }
        const onFocus = () => { void restore() }
        const onVisible = () => { if (document.visibilityState === 'visible') void restore() }
        window.addEventListener('focus', onFocus)
        document.addEventListener('visibilitychange', onVisible)
        // Initial UI is already blocked. Start restoration asynchronously, and do not
        // revive an identity if this provider is unmounted before the task begins.
        let mounted = true
        removeUnscopedPrivateData(queryClient)
        queueMicrotask(() => { if (mounted) void restore() })
        return () => {
            mounted = false
            generation.current += 1
            current.current?.close()
            current.current = null
            removeUnscopedPrivateData(queryClient)
            window.removeEventListener('focus', onFocus)
            document.removeEventListener('visibilitychange', onVisible)
            messages?.close()
            if (channel.current === messages) channel.current = null
        }
    }, [clearIdentity, queryClient, restore])

    async function handleLogin(request: LoginRequest) {
        const version = clearIdentity()
        setLoginPending(true)
        setLoginError(null)
        try {
            await obtainCsrfToken()
            if (version !== generation.current) return
            const authenticated = await login(request)
            await obtainCsrfToken()
            if (version !== generation.current) return
            const authenticatedSession = createPrivateSession(authenticated)
            current.current = authenticatedSession
            setSession(authenticatedSession)
            channel.current?.postMessage('changed')
            const destination = location.state && typeof location.state === 'object'
                && 'from' in location.state && typeof location.state.from === 'string'
                && location.state.from.startsWith('/') && !location.state.from.startsWith('//')
                ? location.state.from : '/'
            navigate(destination, { replace: true, state: null })
        } catch (error) {
            if (version !== generation.current) return
            setLoginError(axios.isAxiosError(error) && error.response?.status === 401
                ? 'Invalid email or password.'
                : 'Unable to connect securely. Please try again.')
        } finally {
            setLoginPending(false)
        }
    }

    async function signOut() {
        clearIdentity()
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

    return <QueryClientProvider key={session!.namespace[3]} client={session!.client}>
        <AuthContext.Provider value={{ user, signOut }}>{children}</AuthContext.Provider>
    </QueryClientProvider>
}
