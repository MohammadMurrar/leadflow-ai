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

export async function getPublicInquiryConfiguration(): Promise<PublicInquiryConfiguration> {
    const response = await publicApi.get<PublicInquiryConfiguration>('/public/inquiry-config')
    return response.data
}

export async function submitPublicInquiry(
    request: PublicLeadRequest,
): Promise<PublicLeadSubmissionResponse> {
    const csrfResponse = await publicApi.get<unknown>('/auth/csrf')
    if (!isCsrfResponse(csrfResponse.data)) {
        throw new Error('Public inquiry security token could not be obtained')
    }

    const response = await publicApi.post<PublicLeadSubmissionResponse>(
        '/public/leads',
        request,
        { headers: { [EXPECTED_CSRF_HEADER]: csrfResponse.data.token } },
    )
    return response.data
}
