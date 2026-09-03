import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import axios from 'axios'
import { Check, Copy, Eye, LoaderCircle, MoreHorizontal, Phone, X } from 'lucide-react'
import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { getLead, getQualificationAttempts, retryQualification, updateLeadStatus } from '../services/leadApi'
import type { Lead, LeadStatus } from '../types/lead'
import type { SupportedCurrency } from '../types/currency'
import { formatMoney } from '../utils/money'

let closeActiveMenu: (() => void) | null = null

interface LeadRowActionsProps {
    lead: Lead
    currency: SupportedCurrency
}

interface MenuPosition {
    left: number
    top: number
}

function humanize(value: string | null) {
    if (!value?.trim()) return 'Not provided'
    return value.toLowerCase().replaceAll('_', ' ').replace(/\b\w/g, (letter) => letter.toUpperCase())
}

function display(value: string | number | null) {
    if (value === null || (typeof value === 'string' && !value.trim())) return 'Not provided'
    return String(value)
}

function formatDate(value: string | null, dateOnly = false) {
    if (!value) return 'Not provided'
    const date = new Date(dateOnly ? `${value}T00:00:00` : value)
    if (Number.isNaN(date.getTime())) return 'Not provided'
    return new Intl.DateTimeFormat(undefined, dateOnly
        ? { dateStyle: 'medium' }
        : { dateStyle: 'medium', timeStyle: 'short' }).format(date)
}

function Detail({ label, value, multiline = false }: { label: string; value: string; multiline?: boolean }) {
    return (
        <div className={multiline ? 'sm:col-span-2' : ''}>
            <dt className="text-xs font-semibold uppercase tracking-wide text-slate-400">{label}</dt>
            <dd className={`mt-1 text-sm text-slate-700 ${multiline ? 'whitespace-pre-wrap leading-6' : 'break-words'}`}>{value}</dd>
        </div>
    )
}

export default function LeadRowActions({ lead, currency }: LeadRowActionsProps) {
    const buttonRef = useRef<HTMLButtonElement>(null)
    const menuRef = useRef<HTMLDivElement>(null)
    const modalRef = useRef<HTMLDivElement>(null)
    const confirmationRef = useRef<HTMLDivElement>(null)
    const confirmationTriggerRef = useRef<HTMLButtonElement | null>(null)
    const [menuOpen, setMenuOpen] = useState(false)
    const [detailsOpen, setDetailsOpen] = useState(false)
    const [position, setPosition] = useState<MenuPosition>({ left: 0, top: 0 })
    const [confirmation, setConfirmation] = useState<string | null>(null)
    const [targetStatus, setTargetStatus] = useState<'WON' | 'LOST' | null>(null)
    const [transitionError, setTransitionError] = useState<string | null>(null)
    const [retryConfirmationOpen, setRetryConfirmationOpen] = useState(false)
    const [retryError, setRetryError] = useState<string | null>(null)
    const [lastTarget, setLastTarget] = useState<LeadStatus | null>(null)
    const queryClient = useQueryClient()

    const detailsQuery = useQuery({
        queryKey: ['lead-details', lead.id],
        queryFn: () => getLead(lead.id),
        enabled: detailsOpen,
        staleTime: 30_000,
        refetchInterval: false,
    })

    const attemptsQuery = useQuery({
        queryKey: ['qualification-attempts', lead.id],
        queryFn: () => getQualificationAttempts(lead.id),
        enabled: detailsOpen,
        staleTime: 30_000,
        refetchInterval: false,
    })

    const statusMutation = useMutation({
        mutationFn: (status: LeadStatus) => {
            if (!detailsQuery.data) throw new Error('Lead details are not available')
            return updateLeadStatus(lead.id, status, detailsQuery.data.version)
        },
        onMutate: (status) => {
            setLastTarget(status)
            setTransitionError(null)
        },
        onSuccess: async (updatedLead) => {
            queryClient.setQueryData(['lead-details', lead.id], updatedLead)
            setTargetStatus(null)
            setConfirmation(`Lead marked as ${humanize(updatedLead.status).toLowerCase()}`)
            window.requestAnimationFrame(() => modalRef.current?.focus())
            await Promise.all([
                queryClient.invalidateQueries({ queryKey: ['leads'] }),
                queryClient.invalidateQueries({ queryKey: ['dashboard-stats'] }),
                queryClient.invalidateQueries({ queryKey: ['analytics'] }),
            ])
        },
        onError: async (error) => {
            const stale = axios.isAxiosError(error)
                && error.response?.status === 409
                && typeof error.response.data === 'object'
                && error.response.data !== null
                && 'message' in error.response.data
                && typeof error.response.data.message === 'string'
                && error.response.data.message.includes('changed elsewhere')
            setTargetStatus(null)
            setTransitionError(stale
                ? 'This lead changed elsewhere. Current details have been refreshed.'
                : 'Could not update the lead status. Please try again.')
            if (stale) await detailsQuery.refetch()
        },
    })

    const retryMutation = useMutation({
        mutationFn: () => {
            if (!detailsQuery.data) throw new Error('Lead details are not available')
            return retryQualification(lead.id, detailsQuery.data.version)
        },
        onMutate: () => setRetryError(null),
        onSuccess: async (outcome) => {
            queryClient.setQueryData(['lead-details', lead.id], outcome.lead)
            queryClient.setQueryData(['qualification-attempts', lead.id], (current: typeof attemptsQuery.data) => {
                const existing = current ?? []
                return [outcome.attempt, ...existing.filter((attempt) => attempt.id !== outcome.attempt.id)]
            })
            setRetryConfirmationOpen(false)
            setConfirmation('Qualification retry started')
            window.requestAnimationFrame(() => modalRef.current?.focus())
            await Promise.all([
                queryClient.invalidateQueries({ queryKey: ['leads'] }),
                queryClient.invalidateQueries({ queryKey: ['dashboard-stats'] }),
                queryClient.invalidateQueries({ queryKey: ['analytics'] }),
            ])
        },
        onError: async (error) => {
            const responseMessage = axios.isAxiosError(error)
                && typeof error.response?.data === 'object'
                && error.response.data !== null
                && 'message' in error.response.data
                && typeof error.response.data.message === 'string'
                ? error.response.data.message : ''
            const stale = error && axios.isAxiosError(error) && error.response?.status === 409
                && responseMessage.includes('changed elsewhere')
            if (stale) {
                setRetryError('This lead changed elsewhere. Current details have been refreshed.')
                await Promise.all([detailsQuery.refetch(), attemptsQuery.refetch()])
            } else if (axios.isAxiosError(error) && !error.response) {
                setRetryError('Could not reach the server. Check the connection and try again.')
            } else if (responseMessage.includes('not enabled')) {
                setRetryError('Qualification retry is not currently available.')
            } else if (responseMessage.includes('already active')) {
                setRetryError('A qualification attempt is already active for this lead.')
            } else if (error && axios.isAxiosError(error) && error.response?.status === 409) {
                setRetryError('This lead is no longer eligible for qualification retry. Current details have been refreshed.')
                await Promise.all([detailsQuery.refetch(), attemptsQuery.refetch()])
            } else {
                setRetryError('Could not start qualification retry. Please try again.')
            }
        },
    })

    const closeMenu = useCallback((restoreFocus = true) => {
        setMenuOpen(false)
        if (restoreFocus) window.requestAnimationFrame(() => buttonRef.current?.focus())
    }, [])

    const updatePosition = useCallback(() => {
        const button = buttonRef.current
        if (!button) return
        const rect = button.getBoundingClientRect()
        const width = 176
        const height = 132
        setPosition({
            left: Math.max(8, Math.min(rect.right - width, window.innerWidth - width - 8)),
            top: rect.bottom + height <= window.innerHeight - 8 ? rect.bottom + 6 : Math.max(8, rect.top - height - 6),
        })
    }, [])

    const toggleMenu = () => {
        if (menuOpen) {
            closeMenu()
            return
        }
        closeActiveMenu?.()
        closeActiveMenu = closeMenu
        updatePosition()
        setMenuOpen(true)
    }

    useLayoutEffect(() => {
        if (!menuOpen) return
        updatePosition()
        menuRef.current?.querySelector<HTMLButtonElement>('[role="menuitem"]')?.focus()
    }, [menuOpen, updatePosition])

    useEffect(() => {
        if (!menuOpen) return
        const handlePointerDown = (event: PointerEvent) => {
            const target = event.target as Node
            if (!menuRef.current?.contains(target) && !buttonRef.current?.contains(target)) closeMenu(false)
        }
        const handleKeyDown = (event: KeyboardEvent) => {
            if (event.key === 'Escape') closeMenu()
        }
        const handleViewportChange = () => updatePosition()
        document.addEventListener('pointerdown', handlePointerDown)
        document.addEventListener('keydown', handleKeyDown)
        window.addEventListener('resize', handleViewportChange)
        window.addEventListener('scroll', handleViewportChange, true)
        return () => {
            document.removeEventListener('pointerdown', handlePointerDown)
            document.removeEventListener('keydown', handleKeyDown)
            window.removeEventListener('resize', handleViewportChange)
            window.removeEventListener('scroll', handleViewportChange, true)
        }
    }, [closeMenu, menuOpen, updatePosition])

    useEffect(() => {
        if (!detailsOpen) return
        const previousOverflow = document.body.style.overflow
        document.body.style.overflow = 'hidden'
        modalRef.current?.focus()
        const handleKeyDown = (event: KeyboardEvent) => {
            if (targetStatus || retryConfirmationOpen) return
            if (event.key === 'Escape') {
                setDetailsOpen(false)
                window.requestAnimationFrame(() => buttonRef.current?.focus())
                return
            }
            if (event.key !== 'Tab' || !modalRef.current) return
            const focusable = Array.from(modalRef.current.querySelectorAll<HTMLElement>('button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'))
            if (focusable.length === 0) return
            const first = focusable[0]
            const last = focusable[focusable.length - 1]
            if (!focusable.includes(document.activeElement as HTMLElement)) {
                event.preventDefault()
                first.focus()
            } else if (event.shiftKey && document.activeElement === first) {
                event.preventDefault()
                last.focus()
            } else if (!event.shiftKey && document.activeElement === last) {
                event.preventDefault()
                first.focus()
            }
        }
        document.addEventListener('keydown', handleKeyDown)
        return () => {
            document.body.style.overflow = previousOverflow
            document.removeEventListener('keydown', handleKeyDown)
        }
    }, [detailsOpen, retryConfirmationOpen, targetStatus])

    useEffect(() => {
        if (!targetStatus) return
        confirmationRef.current?.focus()
        const handleKeyDown = (event: KeyboardEvent) => {
            if (event.key === 'Escape' && !statusMutation.isPending) {
                setTargetStatus(null)
                window.requestAnimationFrame(() => confirmationTriggerRef.current?.focus())
                return
            }
            if (event.key !== 'Tab' || !confirmationRef.current) return
            const focusable = Array.from(confirmationRef.current.querySelectorAll<HTMLElement>('button:not([disabled]), [tabindex]:not([tabindex="-1"])'))
            if (focusable.length === 0) return
            const first = focusable[0]
            const last = focusable[focusable.length - 1]
            if (!focusable.includes(document.activeElement as HTMLElement)) {
                event.preventDefault()
                first.focus()
            } else if (event.shiftKey && document.activeElement === first) {
                event.preventDefault()
                last.focus()
            } else if (!event.shiftKey && document.activeElement === last) {
                event.preventDefault()
                first.focus()
            }
        }
        document.addEventListener('keydown', handleKeyDown)
        return () => document.removeEventListener('keydown', handleKeyDown)
    }, [statusMutation.isPending, targetStatus])

    useEffect(() => {
        if (!retryConfirmationOpen) return
        confirmationRef.current?.focus()
        const handleKeyDown = (event: KeyboardEvent) => {
            if (event.key === 'Escape' && !retryMutation.isPending) {
                setRetryConfirmationOpen(false)
                window.requestAnimationFrame(() => confirmationTriggerRef.current?.focus())
                return
            }
            if (event.key !== 'Tab' || !confirmationRef.current) return
            const focusable = Array.from(confirmationRef.current.querySelectorAll<HTMLElement>('button:not([disabled]), [tabindex]:not([tabindex="-1"])'))
            if (focusable.length === 0) return
            const first = focusable[0]
            const last = focusable[focusable.length - 1]
            if (!focusable.includes(document.activeElement as HTMLElement)) {
                event.preventDefault(); first.focus()
            } else if (event.shiftKey && document.activeElement === first) {
                event.preventDefault(); last.focus()
            } else if (!event.shiftKey && document.activeElement === last) {
                event.preventDefault(); first.focus()
            }
        }
        document.addEventListener('keydown', handleKeyDown)
        return () => document.removeEventListener('keydown', handleKeyDown)
    }, [retryConfirmationOpen, retryMutation.isPending])

    useEffect(() => {
        if (!confirmation) return
        const timer = window.setTimeout(() => setConfirmation(null), 2_000)
        return () => window.clearTimeout(timer)
    }, [confirmation])

    const copy = async (value: string | null, label: 'Email' | 'Phone') => {
        closeMenu(false)
        if (!value?.trim()) return
        try {
            await navigator.clipboard.writeText(value)
            setConfirmation(`${label} copied`)
        } catch {
            setConfirmation(`Could not copy ${label.toLowerCase()}`)
        } finally {
            buttonRef.current?.focus()
        }
    }

    const openDetails = () => {
        closeMenu(false)
        setDetailsOpen(true)
    }

    const closeDetails = () => {
        setDetailsOpen(false)
        window.requestAnimationFrame(() => buttonRef.current?.focus())
    }

    const details = detailsQuery.data

    const transition = (status: LeadStatus) => {
        if (status === 'WON' || status === 'LOST') {
            setTargetStatus(status)
        } else {
            statusMutation.mutate(status)
        }
    }

    const statusActions: LeadStatus[] = details?.status === 'QUALIFIED'
        ? ['CONTACTED', 'WON', 'LOST']
        : details?.status === 'CONTACTED'
            ? ['WON', 'LOST']
            : []

    return (
        <>
            <button
                ref={buttonRef}
                type="button"
                aria-label={`Actions for ${lead.fullName}`}
                aria-haspopup="menu"
                aria-expanded={menuOpen}
                onClick={toggleMenu}
                className="rounded-lg p-2 text-slate-400 outline-none transition hover:bg-slate-100 hover:text-slate-700 focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2"
            >
                <MoreHorizontal className="h-5 w-5" />
            </button>

            {menuOpen && createPortal(
                <div ref={menuRef} role="menu" aria-label={`Actions for ${lead.fullName}`} style={position} className="fixed z-[70] w-44 rounded-xl border border-slate-200 bg-white p-1.5 shadow-xl">
                    <button type="button" role="menuitem" onClick={openDetails} className="flex w-full items-center gap-2 rounded-lg px-3 py-2 text-left text-sm text-slate-700 outline-none hover:bg-slate-50 focus:bg-indigo-50 focus:text-indigo-700"><Eye className="h-4 w-4" />View details</button>
                    <button type="button" role="menuitem" disabled={!lead.email.trim()} aria-disabled={!lead.email.trim()} onClick={() => void copy(lead.email, 'Email')} className="flex w-full items-center gap-2 rounded-lg px-3 py-2 text-left text-sm text-slate-700 outline-none hover:bg-slate-50 focus:bg-indigo-50 focus:text-indigo-700 disabled:cursor-not-allowed disabled:opacity-40"><Copy className="h-4 w-4" />Copy email</button>
                    <button type="button" role="menuitem" disabled={!lead.phone?.trim()} aria-disabled={!lead.phone?.trim()} onClick={() => void copy(lead.phone, 'Phone')} className="flex w-full items-center gap-2 rounded-lg px-3 py-2 text-left text-sm text-slate-700 outline-none hover:bg-slate-50 focus:bg-indigo-50 focus:text-indigo-700 disabled:cursor-not-allowed disabled:opacity-40"><Phone className="h-4 w-4" />Copy phone</button>
                </div>, document.body)}

            {detailsOpen && createPortal(
                <div className="fixed inset-0 z-[80] flex items-center justify-center bg-slate-950/45 p-4" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) closeDetails() }}>
                    <div ref={modalRef} role="dialog" aria-modal="true" aria-labelledby={`lead-details-${lead.id}`} tabIndex={-1} className="max-h-[90vh] w-full max-w-3xl overflow-y-auto rounded-2xl bg-white shadow-2xl outline-none">
                        <div className="sticky top-0 z-10 flex items-start justify-between gap-4 border-b border-slate-100 bg-white px-5 py-4 sm:px-6">
                            <div><h2 id={`lead-details-${lead.id}`} className="text-lg font-bold text-slate-950">Lead details</h2><p className="mt-1 text-sm text-slate-500">Current information for {lead.fullName}</p></div>
                            <button type="button" onClick={closeDetails} aria-label="Close lead details" className="rounded-lg p-2 text-slate-400 outline-none hover:bg-slate-100 hover:text-slate-700 focus-visible:ring-2 focus-visible:ring-indigo-500"><X className="h-5 w-5" /></button>
                        </div>
                        <div className="p-5 sm:p-6">
                            {detailsQuery.isLoading && <div className="flex min-h-48 items-center justify-center gap-3 text-sm text-slate-500" role="status"><LoaderCircle className="h-5 w-5 animate-spin text-indigo-600" />Loading current lead details...</div>}
                            {detailsQuery.isError && !details && <div className="rounded-xl border border-rose-100 bg-rose-50 p-4 text-sm text-rose-700"><p>Could not load lead details. Please try again.</p><button type="button" onClick={() => void detailsQuery.refetch()} className="mt-3 font-semibold underline underline-offset-2">Retry</button></div>}
                            {details && <dl className="grid grid-cols-1 gap-x-8 gap-y-5 sm:grid-cols-2">
                                <Detail label="Full name" value={display(details.fullName)} /><Detail label="Email" value={display(details.email)} />
                                <Detail label="Phone" value={display(details.phone)} /><Detail label="Company" value={display(details.company)} />
                                <Detail label="Requested service" value={display(details.requestedService)} /><Detail label="Estimated budget" value={formatMoney(details.estimatedBudget, currency, 'Not provided')} />
                                <Detail label="Desired start date" value={formatDate(details.desiredStartDate, true)} /><Detail label="Source" value={display(details.source)} />
                                <Detail label="Status" value={humanize(details.status)} /><Detail label="Priority" value={humanize(details.priority)} />
                                <Detail label="Qualification score" value={display(details.qualificationScore)} /><Detail label="Category" value={display(details.category)} />
                                <Detail label="Message" value={display(details.message)} multiline /><Detail label="AI summary" value={display(details.aiSummary)} multiline />
                                <Detail label="Recommended reply" value={display(details.recommendedReply)} multiline />
                                <Detail label="Created" value={formatDate(details.createdAt)} /><Detail label="Updated" value={formatDate(details.updatedAt)} />
                            </dl>}
                            {details && <section className="mt-7 border-t border-slate-100 pt-6" aria-labelledby={`lifecycle-${lead.id}`}>
                                <h3 id={`lifecycle-${lead.id}`} className="font-bold text-slate-900">Sales lifecycle</h3>
                                <div className="mt-3 rounded-xl bg-slate-50 p-4">
                                    <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">Current status</p>
                                    <p className="mt-1 font-semibold text-slate-800">{humanize(details.status)}</p>
                                    {(details.status === 'WON' || details.status === 'LOST') && <p className="mt-2 text-sm text-slate-500">This is a terminal state and cannot be reopened.</p>}
                                    {(details.status === 'NEW' || details.status === 'QUALIFYING') && <p className="mt-2 text-sm text-slate-500">Manual lifecycle actions become available after successful qualification.</p>}
                                    {details.status === 'AUTOMATION_FAILED' && <p className="mt-2 text-sm text-slate-500">Manual lifecycle actions are unavailable because qualification did not complete.</p>}
                                </div>
                                {details.status === 'AUTOMATION_FAILED' && <div className="mt-4">
                                    <button ref={(element) => { if (element) confirmationTriggerRef.current = element }} type="button" disabled={retryMutation.isPending} onClick={() => { setRetryError(null); setRetryConfirmationOpen(true) }} className="rounded-xl bg-indigo-600 px-4 py-2 text-sm font-semibold text-white outline-none hover:bg-indigo-700 focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-60">Retry qualification</button>
                                    {retryError && <div role="alert" className="mt-3 rounded-xl border border-rose-100 bg-rose-50 p-3 text-sm text-rose-700"><p>{retryError}</p><button type="button" onClick={() => setRetryConfirmationOpen(true)} className="mt-2 font-semibold underline underline-offset-2">Try again</button></div>}
                                </div>}
                                {transitionError && <div role="alert" className="mt-3 rounded-xl border border-rose-100 bg-rose-50 p-3 text-sm text-rose-700"><p>{transitionError}</p>{lastTarget && <button type="button" disabled={statusMutation.isPending} onClick={() => transition(lastTarget)} className="mt-2 font-semibold underline underline-offset-2 disabled:opacity-50">Retry</button>}</div>}
                                {statusActions.length > 0 && <div className="mt-4 flex flex-wrap gap-2" aria-label="Available next actions">
                                    {statusActions.map((status) => <button key={status} type="button" disabled={statusMutation.isPending} onClick={(event) => { if (status === 'WON' || status === 'LOST') confirmationTriggerRef.current = event.currentTarget; transition(status) }} className={`rounded-xl px-4 py-2 text-sm font-semibold outline-none transition focus-visible:ring-2 focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50 ${status === 'CONTACTED' ? 'bg-indigo-600 text-white hover:bg-indigo-700 focus-visible:ring-indigo-500' : status === 'WON' ? 'bg-emerald-600 text-white hover:bg-emerald-700 focus-visible:ring-emerald-500' : 'border border-rose-200 bg-white text-rose-700 hover:bg-rose-50 focus-visible:ring-rose-500'}`}>{statusMutation.isPending && lastTarget === status ? 'Updating...' : status === 'CONTACTED' ? 'Mark as contacted' : status === 'WON' ? 'Mark as won' : 'Mark as lost'}</button>)}
                                </div>}
                            </section>}
                            {details && <section className="mt-7 border-t border-slate-100 pt-6" aria-labelledby={`attempt-history-${lead.id}`}>
                                <h3 id={`attempt-history-${lead.id}`} className="font-bold text-slate-900">Qualification attempts</h3>
                                {attemptsQuery.isLoading && <p role="status" className="mt-3 text-sm text-slate-500">Loading qualification attempts...</p>}
                                {attemptsQuery.isError && <div role="alert" className="mt-3 rounded-xl border border-rose-100 bg-rose-50 p-3 text-sm text-rose-700"><p>Qualification attempts could not be loaded.</p><button type="button" onClick={() => attemptsQuery.refetch()} className="mt-2 font-semibold underline underline-offset-2">Retry</button></div>}
                                {attemptsQuery.data?.length === 0 && <p className="mt-3 text-sm text-slate-500">No qualification attempts have been recorded for this lead.</p>}
                                {attemptsQuery.data && attemptsQuery.data.length > 0 && <ol className="mt-3 space-y-3">
                                    {attemptsQuery.data.map((attempt) => <li key={attempt.id} className="rounded-xl bg-slate-50 p-4">
                                        <div className="flex flex-wrap items-center justify-between gap-2"><p className="text-sm font-semibold text-slate-800">Attempt {attempt.attemptNumber}</p><span className="text-xs font-semibold text-slate-600">{humanize(attempt.status)}</span></div>
                                        <p className="mt-1 text-xs text-slate-500">Created {formatDate(attempt.createdAt)}</p>
                                        {(attempt.status === 'FAILED' || attempt.status === 'TIMED_OUT') && <p className="mt-2 text-sm text-slate-600">{attempt.failureMessage ?? 'Automated qualification did not complete.'}</p>}
                                    </li>)}
                                </ol>}
                            </section>}
                        </div>
                    </div>
                </div>, document.body)}

            {targetStatus && createPortal(
                <div className="fixed inset-0 z-[100] flex items-center justify-center bg-slate-950/55 p-4" role="presentation">
                    <div ref={confirmationRef} role="alertdialog" aria-modal="true" aria-labelledby={`confirm-status-${lead.id}`} aria-describedby={`confirm-status-description-${lead.id}`} tabIndex={-1} className="w-full max-w-md rounded-2xl bg-white p-6 shadow-2xl outline-none">
                        <h2 id={`confirm-status-${lead.id}`} className="text-lg font-bold text-slate-950">Mark {details?.fullName ?? lead.fullName} as {targetStatus === 'WON' ? 'won' : 'lost'}?</h2>
                        <p id={`confirm-status-description-${lead.id}`} className="mt-2 text-sm leading-6 text-slate-500">This moves the lead to the terminal {targetStatus === 'WON' ? 'Won' : 'Lost'} state. It cannot be reopened in this workflow.</p>
                        <div className="mt-6 flex justify-end gap-3">
                            <button type="button" disabled={statusMutation.isPending} onClick={() => { setTargetStatus(null); window.requestAnimationFrame(() => confirmationTriggerRef.current?.focus()) }} className="rounded-xl border border-slate-200 px-4 py-2 text-sm font-semibold text-slate-600 outline-none hover:bg-slate-50 focus-visible:ring-2 focus-visible:ring-indigo-500 disabled:opacity-50">Cancel</button>
                            <button type="button" disabled={statusMutation.isPending} onClick={() => { if (!statusMutation.isPending) statusMutation.mutate(targetStatus) }} className={`rounded-xl px-4 py-2 text-sm font-semibold text-white outline-none focus-visible:ring-2 focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-60 ${targetStatus === 'WON' ? 'bg-emerald-600 hover:bg-emerald-700 focus-visible:ring-emerald-500' : 'bg-rose-600 hover:bg-rose-700 focus-visible:ring-rose-500'}`}>{statusMutation.isPending ? 'Updating...' : `Confirm ${targetStatus === 'WON' ? 'won' : 'lost'}`}</button>
                        </div>
                    </div>
                </div>, document.body)}

            {retryConfirmationOpen && createPortal(
                <div className="fixed inset-0 z-[100] flex items-center justify-center bg-slate-950/55 p-4" role="presentation">
                    <div ref={confirmationRef} role="alertdialog" aria-modal="true" aria-labelledby={`confirm-retry-${lead.id}`} aria-describedby={`confirm-retry-description-${lead.id}`} tabIndex={-1} className="w-full max-w-md rounded-2xl bg-white p-6 shadow-2xl outline-none">
                        <h2 id={`confirm-retry-${lead.id}`} className="text-lg font-bold text-slate-950">Retry AI qualification for {details?.fullName ?? lead.fullName}?</h2>
                        <p id={`confirm-retry-description-${lead.id}`} className="mt-2 text-sm leading-6 text-slate-500">A new AI qualification attempt will begin. Any previous attempt history remains unchanged.</p>
                        <div className="mt-6 flex justify-end gap-3">
                            <button type="button" disabled={retryMutation.isPending} onClick={() => { setRetryConfirmationOpen(false); window.requestAnimationFrame(() => confirmationTriggerRef.current?.focus()) }} className="rounded-xl border border-slate-200 px-4 py-2 text-sm font-semibold text-slate-600 outline-none hover:bg-slate-50 focus-visible:ring-2 focus-visible:ring-indigo-500 disabled:opacity-50">Cancel</button>
                            <button type="button" disabled={retryMutation.isPending} onClick={() => { if (!retryMutation.isPending) retryMutation.mutate() }} className="rounded-xl bg-indigo-600 px-4 py-2 text-sm font-semibold text-white outline-none hover:bg-indigo-700 focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-60">{retryMutation.isPending ? 'Starting retry...' : 'Start qualification retry'}</button>
                        </div>
                    </div>
                </div>, document.body)}

            {confirmation && createPortal(<div role="status" className="fixed bottom-5 right-5 z-[90] flex items-center gap-2 rounded-xl bg-slate-900 px-4 py-3 text-sm font-medium text-white shadow-xl"><Check className="h-4 w-4 text-emerald-400" />{confirmation}</div>, document.body)}
        </>
    )
}
