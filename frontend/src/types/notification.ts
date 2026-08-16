export type NotificationType =
    | 'NEW_LEAD'
    | 'LEAD_QUALIFIED'
    | 'HIGH_PRIORITY_LEAD'
    | 'AUTOMATION_FAILED'

export type NotificationSeverity =
    | 'INFO'
    | 'SUCCESS'
    | 'WARNING'
    | 'ERROR'

export interface Notification {
    id: string
    type: NotificationType
    severity: NotificationSeverity
    title: string
    message: string
    leadId: string | null
    read: boolean
    readAt: string | null
    createdAt: string
}

export interface UnreadNotificationCountResponse {
    unreadCount: number
}
