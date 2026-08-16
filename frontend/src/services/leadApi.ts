import axios from 'axios'
import type { CreateLeadRequest, Lead, LeadStatus, QualificationAttempt, QualificationOutcome, QualificationState } from '../types/lead'
import type { PageResponse } from '../types/page'

const api = axios.create({
    baseURL: import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/v1',
    headers: {
        'Content-Type': 'application/json',
    },
    withCredentials: true,
    withXSRFToken: true,
    xsrfCookieName: 'XSRF-TOKEN',
    xsrfHeaderName: 'X-XSRF-TOKEN',
})

let unauthorizedHandler: (() => void) | undefined

export function setUnauthorizedHandler(handler: (() => void) | undefined) {
    unauthorizedHandler = handler
}

api.interceptors.response.use(
    (response) => response,
    (error: unknown) => {
        if (axios.isAxiosError(error) && error.response?.status === 401
            && !error.config?.url?.startsWith('/auth/login')
            && !error.config?.url?.startsWith('/auth/me')) {
            unauthorizedHandler?.()
        }
        return Promise.reject(error)
    },
)

export interface GetLeadsParams {
    page?: number
    size?: number
    status?: LeadStatus
    qualificationState?: QualificationState
    sort?: string
    search?: string
}

export async function getLeads(
    params: GetLeadsParams = {},
): Promise<PageResponse<Lead>> {
    const response = await api.get<PageResponse<Lead>>('/leads', {
        params: {
            page: params.page ?? 0,
            size: params.size ?? 20,
            sort: params.sort ?? 'createdAt,desc',
            status: params.status,
            qualificationState: params.qualificationState,
            search: params.search,
        },
    })

    return response.data
}

export async function getLead(id: string): Promise<Lead> {
    const response = await api.get<Lead>(`/leads/${id}`)
    return response.data
}

export async function createLead(
    request: CreateLeadRequest,
): Promise<Lead> {
    const response = await api.post<Lead>('/leads', request)
    return response.data
}

export async function updateLeadStatus(
    id: string,
    status: LeadStatus,
    version: number,
): Promise<Lead> {
    const response = await api.patch<Lead>(`/leads/${id}/status`, {
        status,
        version,
    })

    return response.data
}

export async function getQualificationAttempts(id: string): Promise<QualificationAttempt[]> {
    const response = await api.get<QualificationAttempt[]>(`/leads/${encodeURIComponent(id)}/qualification-attempts`)
    return response.data
}

export async function retryQualification(id: string, version: number): Promise<QualificationOutcome> {
    const response = await api.post<QualificationOutcome>(
        `/leads/${encodeURIComponent(id)}/qualification-retry`,
        { version },
    )
    return response.data
}

export default api
