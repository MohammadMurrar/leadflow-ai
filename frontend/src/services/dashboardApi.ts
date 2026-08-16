import api from './leadApi'
import type { DashboardRange, DashboardStats } from '../types/dashboard'

export async function getDashboardStats(range: DashboardRange = '30'): Promise<DashboardStats> {
    const response = await api.get<DashboardStats>('/dashboard/stats', {
        params: { range },
    })
    return response.data
}
