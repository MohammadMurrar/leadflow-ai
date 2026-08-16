import type { DashboardRange } from './dashboard'
import type { QualificationState } from './lead'

export type DefaultLeadSort =
    | 'createdAt,desc'
    | 'createdAt,asc'
    | 'qualificationScore,desc'
    | 'estimatedBudget,desc'

export type LeadPageSize = 10 | 20 | 50

export interface WorkspaceSettings {
    version: number
    workspaceName: string
    contactEmail: string | null
    description: string | null
    updatedAt: string
}

export interface UpdateWorkspaceSettingsRequest {
    version: number
    workspaceName: string
    contactEmail: string | null
    description: string | null
}

export interface AutomationStatus {
    dispatcherEnabled: boolean
    legacyCallbackEnabled: boolean
    retryEnabled: boolean
    attemptTrackingAvailable: boolean
}

export interface InterfacePreferences {
    schemaVersion: 1
    defaultAnalyticsRange: DashboardRange
    defaultLeadPageSize: LeadPageSize
    defaultLeadSort: DefaultLeadSort
    defaultQualificationState: QualificationState | ''
}
