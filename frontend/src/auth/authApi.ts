import api from '../services/leadApi'
import type { AuthenticatedUser, CsrfResponse, LoginRequest } from './auth'

export async function obtainCsrfToken(): Promise<CsrfResponse> {
    const response = await api.get<CsrfResponse>('/auth/csrf')
    return response.data
}

export async function login(request: LoginRequest): Promise<AuthenticatedUser> {
    const response = await api.post<AuthenticatedUser>('/auth/login', request)
    return response.data
}

export async function getCurrentUser(): Promise<AuthenticatedUser> {
    const response = await api.get<AuthenticatedUser>('/auth/me')
    return response.data
}

export async function logout(): Promise<void> {
    await api.post('/auth/logout')
}
