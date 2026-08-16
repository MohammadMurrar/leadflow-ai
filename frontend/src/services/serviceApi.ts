import api from './leadApi'
import type { PageResponse } from '../types/page'
import type {
    CreateServiceRequest,
    ServiceOffering,
    ServiceOption,
    ServiceSort,
    UpdateServiceRequest,
} from '../types/service'

export interface GetServicesParams {
    page?: number
    size?: number
    search?: string
    active?: boolean
    sort?: ServiceSort
}

export async function getServices(params: GetServicesParams = {}): Promise<PageResponse<ServiceOffering>> {
    const response = await api.get<PageResponse<ServiceOffering>>('/services', {
        params: {
            page: params.page ?? 0,
            size: params.size ?? 10,
            search: params.search,
            active: params.active,
            sort: params.sort ?? 'name,asc',
        },
    })
    return response.data
}

export async function getActiveServices(search?: string, page = 0, size = 200): Promise<PageResponse<ServiceOption>> {
    const response = await api.get<PageResponse<ServiceOption>>('/services/active', {
        params: { search, page, size },
    })
    return response.data
}

export async function createService(request: CreateServiceRequest): Promise<ServiceOffering> {
    const response = await api.post<ServiceOffering>('/services', request)
    return response.data
}

export async function updateService(id: string, request: UpdateServiceRequest): Promise<ServiceOffering> {
    const response = await api.put<ServiceOffering>(`/services/${encodeURIComponent(id)}`, request)
    return response.data
}

export async function deactivateService(id: string, version: number): Promise<ServiceOffering> {
    const response = await api.post<ServiceOffering>(`/services/${encodeURIComponent(id)}/deactivate`, { version })
    return response.data
}

export async function reactivateService(id: string, version: number): Promise<ServiceOffering> {
    const response = await api.post<ServiceOffering>(`/services/${encodeURIComponent(id)}/reactivate`, { version })
    return response.data
}
