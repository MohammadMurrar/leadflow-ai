import {
    Bell,
    CheckCheck,
    CircleCheck,
    Inbox,
    Info,
    LoaderCircle,
    OctagonAlert,
    RefreshCw,
    TriangleAlert,
} from 'lucide-react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'
import {
    getNotifications,
    getUnreadNotificationCount,
    markAllNotificationsAsRead,
    markNotificationAsRead,
} from '../services/notificationApi'
import type {
    Notification,
    NotificationSeverity,
    UnreadNotificationCountResponse,
} from '../types/notification'
import type { PageResponse } from '../types/page'

const severityStyles: Record<NotificationSeverity, string> = {
    INFO: 'bg-blue-50 text-blue-600 ring-blue-100',
    SUCCESS: 'bg-emerald-50 text-emerald-600 ring-emerald-100',
    WARNING: 'bg-amber-50 text-amber-600 ring-amber-100',
    ERROR: 'bg-rose-50 text-rose-600 ring-rose-100',
}

const severityIcons = {
    INFO: Info,
    SUCCESS: CircleCheck,
    WARNING: TriangleAlert,
    ERROR: OctagonAlert,
} satisfies Record<NotificationSeverity, typeof Info>

const relativeTimeFormatter = new Intl.RelativeTimeFormat('en', { numeric: 'auto' })

function formatRelativeTime(value: string) {
    const timestamp = new Date(value).getTime()
    if (!Number.isFinite(timestamp)) return 'Unknown time'

    const seconds = Math.round((timestamp - Date.now()) / 1_000)
    const ranges = [
        { limit: 60, divisor: 1, unit: 'second' },
        { limit: 3_600, divisor: 60, unit: 'minute' },
        { limit: 86_400, divisor: 3_600, unit: 'hour' },
        { limit: 604_800, divisor: 86_400, unit: 'day' },
        { limit: 2_629_800, divisor: 604_800, unit: 'week' },
        { limit: 31_557_600, divisor: 2_629_800, unit: 'month' },
        { limit: Infinity, divisor: 31_557_600, unit: 'year' },
    ] as const
    const absoluteSeconds = Math.abs(seconds)
    const range = ranges.find((candidate) => absoluteSeconds < candidate.limit) ?? ranges.at(-1)!

    return relativeTimeFormatter.format(
        Math.round(seconds / range.divisor),
        range.unit,
    )
}

export default function NotificationBell() {
    const [open, setOpen] = useState(false)
    const [actionError, setActionError] = useState<string | null>(null)
    const [pendingNotificationId, setPendingNotificationId] = useState<string | null>(null)
    const containerRef = useRef<HTMLDivElement>(null)
    const bellRef = useRef<HTMLButtonElement>(null)
    const queryClient = useQueryClient()

    const countQuery = useQuery({
        queryKey: ['notifications', 'unread-count'],
        queryFn: getUnreadNotificationCount,
    })

    const notificationsQuery = useQuery({
        queryKey: ['notifications', 'inbox', 0, 10],
        queryFn: () => getNotifications(0, 10),
        enabled: open,
    })

    const markOneMutation = useMutation({
        mutationFn: markNotificationAsRead,
        onSuccess: (_, id) => {
            queryClient.setQueryData(
                ['notifications', 'inbox', 0, 10],
                (current: PageResponse<Notification> | undefined) => current
                    ? {
                        ...current,
                        content: current.content.map((notification) =>
                            notification.id === id
                                ? { ...notification, read: true, readAt: new Date().toISOString() }
                                : notification,
                        ),
                    }
                    : current,
            )
            queryClient.setQueryData(
                ['notifications', 'unread-count'],
                (current: UnreadNotificationCountResponse | undefined) => current
                    ? { unreadCount: Math.max(0, current.unreadCount - 1) }
                    : current,
            )
            setActionError(null)
        },
        onError: () => setActionError('Could not mark this notification as read. Please try again.'),
        onSettled: () => setPendingNotificationId(null),
    })

    const markAllMutation = useMutation({
        mutationFn: markAllNotificationsAsRead,
        onSuccess: () => {
            queryClient.setQueryData(
                ['notifications', 'inbox', 0, 10],
                (current: PageResponse<Notification> | undefined) => current
                    ? {
                        ...current,
                        content: current.content.map((notification) => ({
                            ...notification,
                            read: true,
                            readAt: notification.readAt ?? new Date().toISOString(),
                        })),
                    }
                    : current,
            )
            queryClient.setQueryData(
                ['notifications', 'unread-count'],
                { unreadCount: 0 },
            )
            setActionError(null)
        },
        onError: () => setActionError('Could not mark all notifications as read. Please try again.'),
    })

    useEffect(() => {
        if (!open) return

        const handlePointerDown = (event: PointerEvent) => {
            if (!containerRef.current?.contains(event.target as Node)) setOpen(false)
        }
        const handleKeyDown = (event: KeyboardEvent) => {
            if (event.key === 'Escape') {
                setOpen(false)
                bellRef.current?.focus()
            }
        }

        document.addEventListener('pointerdown', handlePointerDown)
        document.addEventListener('keydown', handleKeyDown)
        return () => {
            document.removeEventListener('pointerdown', handlePointerDown)
            document.removeEventListener('keydown', handleKeyDown)
        }
    }, [open])

    const unreadCount = countQuery.data?.unreadCount ?? 0
    const notifications = notificationsQuery.data?.content ?? []
    const badgeText = unreadCount > 99 ? '99+' : String(unreadCount)
    const bellLabel = unreadCount === 0
        ? 'Notifications, no unread notifications'
        : `Notifications, ${unreadCount} unread`

    function handleNotificationClick(notification: Notification) {
        if (notification.read || pendingNotificationId !== null) return
        setPendingNotificationId(notification.id)
        setActionError(null)
        markOneMutation.mutate(notification.id)
    }

    return (
        <div ref={containerRef} className="relative">
            <button
                ref={bellRef}
                type="button"
                aria-label={bellLabel}
                aria-expanded={open}
                aria-controls="notification-inbox"
                onClick={() => {
                    setOpen((current) => !current)
                    setActionError(null)
                }}
                className="relative rounded-xl border border-slate-200 bg-white p-2.5 text-slate-500 transition hover:bg-slate-50 hover:text-slate-800 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-indigo-100"
            >
                <Bell className="h-5 w-5" />
                {unreadCount > 0 && (
                    <span className="absolute -right-2 -top-2 flex min-h-5 min-w-5 items-center justify-center rounded-full border-2 border-white bg-indigo-600 px-1 text-[10px] font-bold leading-none text-white">
                        {badgeText}
                    </span>
                )}
            </button>

            {open && (
                <section
                    id="notification-inbox"
                    aria-label="Notification inbox"
                    className="fixed inset-x-3 top-[4.75rem] z-50 overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-2xl shadow-slate-900/15 sm:absolute sm:inset-x-auto sm:right-0 sm:top-[calc(100%+0.75rem)] sm:w-[24rem]"
                >
                    <div className="flex items-center justify-between border-b border-slate-100 px-4 py-3.5">
                        <div>
                            <h2 className="font-bold text-slate-950">Notifications</h2>
                            <p className="mt-0.5 text-xs text-slate-400">
                                {unreadCount === 0 ? 'You are all caught up' : `${unreadCount} unread`}
                            </p>
                        </div>
                        {unreadCount > 0 && (
                            <button
                                type="button"
                                disabled={markAllMutation.isPending}
                                onClick={() => {
                                    setActionError(null)
                                    markAllMutation.mutate()
                                }}
                                className="flex items-center gap-1.5 rounded-lg px-2 py-1.5 text-xs font-semibold text-indigo-600 transition hover:bg-indigo-50 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-indigo-100 disabled:cursor-not-allowed disabled:opacity-60"
                            >
                                {markAllMutation.isPending
                                    ? <LoaderCircle className="h-3.5 w-3.5 animate-spin" />
                                    : <CheckCheck className="h-3.5 w-3.5" />}
                                Mark all as read
                            </button>
                        )}
                    </div>

                    {actionError && (
                        <div role="alert" className="border-b border-rose-100 bg-rose-50 px-4 py-2.5 text-xs text-rose-700">
                            {actionError}
                        </div>
                    )}

                    {notificationsQuery.isLoading && (
                        <div className="flex min-h-56 flex-col items-center justify-center px-6 text-center">
                            <LoaderCircle className="mb-3 h-6 w-6 animate-spin text-indigo-600" />
                            <p className="text-sm font-medium text-slate-600">Loading notifications...</p>
                        </div>
                    )}

                    {notificationsQuery.isError && notifications.length === 0 && (
                        <div role="alert" className="flex min-h-56 flex-col items-center justify-center px-6 text-center">
                            <OctagonAlert className="mb-3 h-7 w-7 text-rose-500" />
                            <p className="font-semibold text-slate-800">Notifications are unavailable</p>
                            <p className="mt-1 text-sm text-slate-500">Please check your connection and try again.</p>
                            <button
                                type="button"
                                onClick={() => notificationsQuery.refetch()}
                                className="mt-4 flex items-center gap-2 rounded-lg bg-slate-900 px-3 py-2 text-xs font-semibold text-white focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-slate-200"
                            >
                                <RefreshCw className="h-3.5 w-3.5" />
                                Retry
                            </button>
                        </div>
                    )}

                    {!notificationsQuery.isLoading && !notificationsQuery.isError && notifications.length === 0 && (
                        <div className="flex min-h-56 flex-col items-center justify-center px-6 text-center">
                            <div className="mb-3 flex h-11 w-11 items-center justify-center rounded-full bg-slate-100 text-slate-400">
                                <Inbox className="h-5 w-5" />
                            </div>
                            <p className="font-semibold text-slate-800">No notifications yet</p>
                            <p className="mt-1 text-sm text-slate-400">New lead activity will appear here.</p>
                        </div>
                    )}

                    {notifications.length > 0 && (
                        <>
                            {notificationsQuery.isError && (
                                <div role="alert" className="flex items-center justify-between gap-3 border-b border-amber-100 bg-amber-50 px-4 py-2 text-xs text-amber-800">
                                    <span>Could not refresh. Showing saved notifications.</span>
                                    <button type="button" onClick={() => notificationsQuery.refetch()} className="font-semibold underline underline-offset-2">
                                        Retry
                                    </button>
                                </div>
                            )}
                            <div className="max-h-[min(28rem,calc(100vh-10rem))] overflow-y-auto">
                                {notifications.map((notification) => {
                                    const SeverityIcon = severityIcons[notification.severity]
                                    const isPending = pendingNotificationId === notification.id
                                    return (
                                        <button
                                            key={notification.id}
                                            type="button"
                                            disabled={isPending}
                                            onClick={() => handleNotificationClick(notification)}
                                            className={`flex w-full gap-3 border-b border-slate-100 px-4 py-3.5 text-left transition last:border-b-0 focus-visible:relative focus-visible:z-10 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-indigo-500 disabled:cursor-wait ${
                                                notification.read ? 'bg-white hover:bg-slate-50' : 'bg-indigo-50/45 hover:bg-indigo-50/80'
                                            }`}
                                        >
                                            <div className={`mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-xl ring-1 ${severityStyles[notification.severity]}`}>
                                                {isPending
                                                    ? <LoaderCircle className="h-4 w-4 animate-spin" />
                                                    : <SeverityIcon className="h-4 w-4" />}
                                            </div>
                                            <div className="min-w-0 flex-1">
                                                <div className="flex items-start gap-2">
                                                    <p className={`flex-1 text-sm ${notification.read ? 'font-medium text-slate-700' : 'font-bold text-slate-900'}`}>
                                                        {notification.title}
                                                    </p>
                                                    {!notification.read && (
                                                        <span className="mt-1.5 h-2 w-2 shrink-0 rounded-full bg-indigo-600" aria-label="Unread" />
                                                    )}
                                                </div>
                                                <p className={`mt-1 text-xs leading-5 ${notification.read ? 'text-slate-400' : 'text-slate-600'}`}>
                                                    {notification.message}
                                                </p>
                                                <time dateTime={notification.createdAt} className="mt-1.5 block text-[11px] font-medium text-slate-400">
                                                    {formatRelativeTime(notification.createdAt)}
                                                </time>
                                            </div>
                                        </button>
                                    )
                                })}
                            </div>
                            {(notificationsQuery.data?.totalPages ?? 0) > 1 && (
                                <p className="border-t border-slate-100 bg-slate-50 px-4 py-2.5 text-center text-xs text-slate-400">
                                    Showing the 10 most recent notifications.
                                </p>
                            )}
                        </>
                    )}
                </section>
            )}
        </div>
    )
}
