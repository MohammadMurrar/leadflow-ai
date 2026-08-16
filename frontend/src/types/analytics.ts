import type { DashboardRange } from './dashboard'
import type { LeadStatus } from './lead'

export type PerformanceGranularity = 'DAILY' | 'MONTHLY'

export interface AnalyticsPerformancePoint {
    periodStart: string
    totalLeads: number
    qualifiedLeads: number
}

export interface AnalyticsBreakdownItem {
    name: string
    count: number
    percentage: number
}

export interface AnalyticsServicePerformance {
    service: string
    leadCount: number
    qualifiedLeads: number
    qualificationRate: number
    pipelineValue: number
    averageAiScore: number
}

export interface AnalyticsResponse {
    range: DashboardRange
    totalLeads: number
    qualifiedLeads: number
    qualificationRate: number
    pipelineValue: number
    averageAiScore: number
    performanceGranularity: PerformanceGranularity
    performance: AnalyticsPerformancePoint[]
    statusCounts: Record<LeadStatus, number>
    priorityBreakdown: AnalyticsBreakdownItem[]
    categoryBreakdown: AnalyticsBreakdownItem[]
    topServices: AnalyticsServicePerformance[]
}
