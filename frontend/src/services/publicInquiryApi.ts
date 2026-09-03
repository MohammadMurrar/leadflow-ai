import axios from 'axios'
import type {
    PublicInquiryConfiguration,
    PublicLeadRequest,
    PublicLeadSubmissionResponse,
} from '../types/publicInquiry'

interface CsrfResponse {
    token: string
    headerName: string
}

const EXPECTED_CSRF_HEADER = 'X-XSRF-TOKEN'

const publicApi = axios.create({
    baseURL: import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/v1',
    headers: {
        'Content-Type': 'application/json',
    },
    withCredentials: true,
    withXSRFToken: true,
    xsrfCookieName: 'XSRF-TOKEN',
    xsrfHeaderName: EXPECTED_CSRF_HEADER,
})

function isCsrfResponse(value: unknown): value is CsrfResponse {
    if (typeof value !== 'object' || value === null) return false
    const response = value as Record<string, unknown>
    const token = response.token
    return typeof token === 'string'
        && token.length > 0
        && token.length <= 512
        && token.trim() === token
        && response.headerName === EXPECTED_CSRF_HEADER
}

function workspacePath(workspaceSlug: string | undefined, suffix: 'inquiry-config' | 'leads') {
    return workspaceSlug === undefined
        ? `/public/${suffix}`
        : `/public/workspaces/${encodeURIComponent(workspaceSlug)}/${suffix}`
}

export async function getPublicInquiryConfiguration(
    workspaceSlug?: string,
    signal?: AbortSignal,
): Promise<PublicInquiryConfiguration> {
    const response = await publicApi.get<PublicInquiryConfiguration>(
        workspacePath(workspaceSlug, 'inquiry-config'),
        { signal },
    )
    return response.data
}

export async function submitPublicInquiry(
    request: PublicLeadRequest,
    workspaceSlug?: string,
): Promise<PublicLeadSubmissionResponse> {
    const csrfResponse = await publicApi.get<unknown>('/auth/csrf')
    if (!isCsrfResponse(csrfResponse.data)) {
        throw new Error('Public inquiry security token could not be obtained')
    }

    const response = await publicApi.post<PublicLeadSubmissionResponse>(
        workspacePath(workspaceSlug, 'leads'),
        request,
    )
    return response.data
}
