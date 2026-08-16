export type LeadStatus =
    | 'NEW'
    | 'QUALIFYING'
    | 'QUALIFIED'
    | 'CONTACTED'
    | 'WON'
    | 'LOST'
    | 'AUTOMATION_FAILED'

export type QualificationState =
    | 'PROCESSING'
    | 'SUCCESSFULLY_QUALIFIED'
    | 'FAILED'

export type LeadPriority =
    | 'UNASSESSED'
    | 'LOW'
    | 'MEDIUM'
    | 'HIGH'

export interface Lead {
    id: string
    version: number
    fullName: string
    email: string
    phone: string | null
    company: string | null
    requestedService: string
    estimatedBudget: number | null
    desiredStartDate: string | null
    message: string | null
    source: string | null
    status: LeadStatus
    priority: LeadPriority | null
    qualificationScore: number | null
    category: string | null
    aiSummary: string | null
    recommendedReply: string | null
    createdAt: string
    updatedAt: string
}

interface CreateLeadBase {
    fullName: string
    email: string
    phone: string | null
    company: string | null
    estimatedBudget: number | null
    desiredStartDate: string | null
    message: string
    source: string | null
}

export type CreateLeadRequest = CreateLeadBase & (
    | { serviceId: string; requestedService?: never }
    | { serviceId?: never; requestedService: string }
)

export type QualificationAttemptStatus =
    | 'PENDING'
    | 'PROCESSING'
    | 'SUCCEEDED'
    | 'FAILED'
    | 'TIMED_OUT'

export type QualificationFailureCode =
    | 'WEBHOOK_DELIVERY_FAILED'
    | 'WORKFLOW_FAILED'
    | 'AI_PROVIDER_ERROR'
    | 'INVALID_AI_RESPONSE'
    | 'CALLBACK_REJECTED'
    | 'TIMEOUT'
    | 'UNKNOWN'

export interface QualificationAttempt {
    id: string
    attemptNumber: number
    status: QualificationAttemptStatus
    failureCode: QualificationFailureCode | null
    failureMessage: string | null
    dispatchedAt: string | null
    startedAt: string | null
    completedAt: string | null
    failedAt: string | null
    timedOutAt: string | null
    createdAt: string
    updatedAt: string
}

export interface QualificationOutcome {
    lead: Lead
    attempt: QualificationAttempt
}
