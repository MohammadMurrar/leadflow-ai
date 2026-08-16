export interface ServiceOffering {
    id: string
    version: number
    name: string
    description: string | null
    active: boolean
    createdAt: string
    updatedAt: string
}

export interface ServiceOption {
    id: string
    name: string
}

export interface CreateServiceRequest {
    name: string
    description: string | null
}

export interface UpdateServiceRequest extends CreateServiceRequest {
    version: number
}

export type ServiceSort = 'name,asc' | 'name,desc' | 'createdAt,desc' | 'createdAt,asc'
