export interface PublicService {
    id: string
    name: string
}

export interface PublicInquiryConfiguration {
    workspaceName: string
    description: string | null
    services: PublicService[]
}

export interface PublicLeadRequest {
    fullName: string
    email: string
    phone: string | null
    company: string | null
    serviceId: string
    estimatedBudget: number | null
    desiredStartDate: string | null
    message: string
    website: string
}

export interface PublicLeadSubmissionResponse {
    message: string
}

export interface PublicInquiryApiError {
    timestamp?: string
    status: number
    message: string
    details: string[]
}
