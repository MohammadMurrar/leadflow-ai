import api from './leadApi'
import type {
    Notification,
    UnreadNotificationCountResponse,
} from '../types/notification'
import type { PageResponse } from '../types/page'

export async function getNotifications(
    page = 0,
    size = 10,
): Promise<PageResponse<Notification>> {
    const response = await api.get<PageResponse<Notification>>('/notifications', {
        params: { page, size },
    })

    return response.data
}

export async function getUnreadNotificationCount(): Promise<UnreadNotificationCountResponse> {
    const response = await api.get<UnreadNotificationCountResponse>(
        '/notifications/unread-count',
    )

    return response.data
}

export async function markNotificationAsRead(id: string): Promise<void> {
    await api.patch<void>(`/notifications/${encodeURIComponent(id)}/read`)
}

export async function markAllNotificationsAsRead(): Promise<void> {
    await api.patch<void>('/notifications/read-all')
}
