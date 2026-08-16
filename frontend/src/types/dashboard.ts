import type { LeadStatus } from './lead'

export type DashboardRange = '7' | '30' | '90' | 'all'

export interface DashboardPerformancePoint {
    date: string
    totalLeads: number
    qualifiedLeads: number
}

export interface DashboardStats {
    totalLeads: number
    qualifiedLeads: number
    qualificationRate: number
    pipelineValue: number
    averageAiScore: number
    statusCounts: Record<LeadStatus, number>
    performance: DashboardPerformancePoint[]
}
