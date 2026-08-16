import api from './leadApi'
import type { AnalyticsResponse } from '../types/analytics'
import type { DashboardRange } from '../types/dashboard'

export async function getAnalytics(range: DashboardRange = '30'): Promise<AnalyticsResponse> {
    const response = await api.get<AnalyticsResponse>('/analytics', {
        params: { range },
    })
    return response.data
}
