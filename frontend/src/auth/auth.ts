import { createContext, useContext } from 'react'

export type UserRole = 'ADMIN'

export interface AuthenticatedUser {
    id: string
    workspaceId: string
    email: string
    displayName: string
    role: UserRole
}

export interface LoginRequest {
    email: string
    password: string
}

export interface CsrfResponse {
    token: string
    headerName: 'X-XSRF-TOKEN'
}

export interface AuthContextValue {
    user: AuthenticatedUser
    signOut: () => Promise<void>
}

export const AuthContext = createContext<AuthContextValue | null>(null)

export function useAuth(): AuthContextValue {
    const value = useContext(AuthContext)
    if (!value) throw new Error('useAuth must be used inside AuthProvider')
    return value
}
