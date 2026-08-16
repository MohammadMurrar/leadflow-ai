import { useEffect, useMemo, useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import axios from 'axios'
import { AlertTriangle, Bot, CheckCircle2, RefreshCw, RotateCcw, Save, Settings2, X } from 'lucide-react'
import { getAutomationStatus, getWorkspaceSettings, updateWorkspaceSettings } from '../services/settingsApi'
import type { InterfacePreferences, WorkspaceSettings } from '../types/settings'
import {
    resetInterfacePreferences,
    saveInterfacePreferences,
} from '../utils/interfacePreferences'

interface SettingsPageProps {
    preferences: InterfacePreferences
    onPreferencesSaved: (preferences: InterfacePreferences) => void
}

type WorkspaceForm = { workspaceName: string; contactEmail: string; description: string }

function formFrom(settings: WorkspaceSettings): WorkspaceForm {
    return {
        workspaceName: settings.workspaceName,
        contactEmail: settings.contactEmail ?? '',
        description: settings.description ?? '',
    }
}

function safeMessage(error: unknown, fallback: string) {
    if (!axios.isAxiosError(error) || !error.response) return 'Could not reach the server. Try again.'
    return fallback
}

function validEmail(value: string) {
    return !value.trim() || /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value.trim())
}

export default function SettingsPage({ preferences, onPreferencesSaved }: SettingsPageProps) {
    const queryClient = useQueryClient()
    const [workspaceForm, setWorkspaceForm] = useState<WorkspaceForm | null>(null)
    const [workspaceMessage, setWorkspaceMessage] = useState<string | null>(null)
    const [workspaceError, setWorkspaceError] = useState<string | null>(null)
    const [preferenceDraft, setPreferenceDraft] = useState(preferences)
    const [preferenceMessage, setPreferenceMessage] = useState<string | null>(null)
    const [resetOpen, setResetOpen] = useState(false)
    const resetTriggerRef = useRef<HTMLButtonElement>(null)
    const resetDialogRef = useRef<HTMLDivElement>(null)

    const workspaceQuery = useQuery({
        queryKey: ['settings', 'workspace'],
        queryFn: getWorkspaceSettings,
    })
    const automationQuery = useQuery({
        queryKey: ['settings', 'automation-status'],
        queryFn: getAutomationStatus,
        refetchInterval: false,
    })

    const displayedWorkspaceForm = workspaceForm ?? (workspaceQuery.data ? formFrom(workspaceQuery.data) : null)
    const normalizedWorkspace = useMemo(() => displayedWorkspaceForm ? {
        workspaceName: displayedWorkspaceForm.workspaceName.trim().replace(/\s+/g, ' '),
        contactEmail: displayedWorkspaceForm.contactEmail.trim(),
        description: displayedWorkspaceForm.description.trim(),
    } : null, [displayedWorkspaceForm])
    const workspaceDirty = Boolean(workspaceQuery.data && normalizedWorkspace && (
        normalizedWorkspace.workspaceName !== workspaceQuery.data.workspaceName
        || normalizedWorkspace.contactEmail !== (workspaceQuery.data.contactEmail ?? '')
        || normalizedWorkspace.description !== (workspaceQuery.data.description ?? '')
    ))
    const workspaceValid = Boolean(normalizedWorkspace?.workspaceName
        && normalizedWorkspace.workspaceName.length <= 120
        && normalizedWorkspace.contactEmail.length <= 180
        && validEmail(normalizedWorkspace.contactEmail)
        && normalizedWorkspace.description.length <= 500)
    const preferencesDirty = JSON.stringify(preferenceDraft) !== JSON.stringify(preferences)

    useEffect(() => {
        if (!workspaceDirty && !preferencesDirty) return
        const warn = (event: BeforeUnloadEvent) => event.preventDefault()
        window.addEventListener('beforeunload', warn)
        return () => window.removeEventListener('beforeunload', warn)
    }, [workspaceDirty, preferencesDirty])

    useEffect(() => {
        if (!resetOpen) return
        resetDialogRef.current?.focus()
        const keydown = (event: KeyboardEvent) => {
            if (event.key === 'Escape') {
                setResetOpen(false)
                requestAnimationFrame(() => resetTriggerRef.current?.focus())
            }
        }
        document.addEventListener('keydown', keydown)
        return () => document.removeEventListener('keydown', keydown)
    }, [resetOpen])

    const workspaceMutation = useMutation({
        mutationFn: () => updateWorkspaceSettings({
            version: workspaceQuery.data!.version,
            workspaceName: normalizedWorkspace!.workspaceName,
            contactEmail: normalizedWorkspace!.contactEmail || null,
            description: normalizedWorkspace!.description || null,
        }),
        onMutate: () => { setWorkspaceError(null); setWorkspaceMessage(null) },
        onSuccess: (updated) => {
            queryClient.setQueryData(['settings', 'workspace'], updated)
            setWorkspaceForm(null)
            setWorkspaceMessage('Workspace profile saved.')
        },
        onError: async (error) => {
            if (axios.isAxiosError(error) && error.response?.status === 409) {
                const current = await workspaceQuery.refetch()
                if (current.data) setWorkspaceForm(null)
                setWorkspaceError('Workspace settings changed elsewhere. The current saved version has been loaded.')
                return
            }
            setWorkspaceError(safeMessage(error, 'Workspace profile could not be saved.'))
        },
    })

    const savePreferences = () => {
        setPreferenceMessage(null)
        if (!saveInterfacePreferences(preferenceDraft)) {
            setPreferenceMessage('Browser storage is unavailable. Defaults were not saved.')
            return
        }
        onPreferencesSaved(preferenceDraft)
        setPreferenceMessage('Interface defaults saved for this browser. They apply on your next relevant page visit.')
    }

    const confirmReset = () => {
        const defaults = resetInterfacePreferences()
        setPreferenceDraft(defaults)
        onPreferencesSaved(defaults)
        setPreferenceMessage('Interface defaults reset. They apply on your next relevant page visit.')
        setResetOpen(false)
        requestAnimationFrame(() => resetTriggerRef.current?.focus())
    }

    return <main className="min-w-0 px-4 py-7 sm:px-6 lg:px-8">
        <section className="mb-7"><p className="text-sm font-semibold text-indigo-600">Workspace configuration</p><h1 className="mt-1 text-2xl font-bold tracking-tight text-slate-950 sm:text-3xl">Settings</h1><p className="mt-2 max-w-3xl text-sm text-slate-500">Manage shared workspace identity, browser-local interface defaults, and view safe automation configuration.</p></section>

        <div className="grid gap-6 xl:grid-cols-2">
            <section className="rounded-2xl border border-slate-200/80 bg-white p-5 shadow-sm sm:p-6" aria-busy={workspaceQuery.isFetching}>
                <div className="flex items-start gap-3"><div className="rounded-xl bg-indigo-50 p-2.5 text-indigo-600"><Settings2 className="h-5 w-5" /></div><div><h2 className="font-bold text-slate-950">Workspace profile</h2><p className="mt-1 text-sm text-slate-500">Saved to this workspace and visible wherever workspace identity is shown.</p></div></div>
                {workspaceQuery.isLoading && !displayedWorkspaceForm && <p role="status" className="mt-6 text-sm text-slate-500">Loading workspace profile...</p>}
                {workspaceQuery.isError && !displayedWorkspaceForm && <div role="alert" className="mt-6 rounded-xl bg-rose-50 p-4 text-sm text-rose-700">Workspace profile could not be loaded. <button type="button" onClick={() => workspaceQuery.refetch()} className="font-semibold underline">Retry</button></div>}
                {displayedWorkspaceForm && <form className="mt-6 space-y-4" onSubmit={(event) => { event.preventDefault(); if (workspaceDirty && workspaceValid && !workspaceMutation.isPending) workspaceMutation.mutate() }}>
                    <label className="block text-sm font-medium text-slate-700">Workspace name <span className="text-rose-500">*</span><input value={displayedWorkspaceForm.workspaceName} maxLength={240} onChange={(event) => { setWorkspaceForm({ ...displayedWorkspaceForm, workspaceName: event.target.value }); setWorkspaceMessage(null) }} aria-invalid={!normalizedWorkspace?.workspaceName || normalizedWorkspace.workspaceName.length > 120} className="mt-2 h-11 w-full rounded-xl border border-slate-200 px-3 outline-none focus:ring-4 focus:ring-indigo-100" /></label>
                    <label className="block text-sm font-medium text-slate-700">Contact email <span className="text-slate-400">(optional)</span><input type="email" value={displayedWorkspaceForm.contactEmail} maxLength={240} onChange={(event) => { setWorkspaceForm({ ...displayedWorkspaceForm, contactEmail: event.target.value }); setWorkspaceMessage(null) }} aria-describedby="contact-email-help" aria-invalid={!validEmail(displayedWorkspaceForm.contactEmail) || displayedWorkspaceForm.contactEmail.trim().length > 180} className="mt-2 h-11 w-full rounded-xl border border-slate-200 px-3 outline-none focus:ring-4 focus:ring-indigo-100" /><span id="contact-email-help" className="mt-1 block text-xs text-slate-400">Reference information only. LeadFlow does not send email from this address.</span></label>
                    <label className="block text-sm font-medium text-slate-700">Description <span className="text-slate-400">(optional)</span><textarea rows={4} value={displayedWorkspaceForm.description} maxLength={600} onChange={(event) => { setWorkspaceForm({ ...displayedWorkspaceForm, description: event.target.value }); setWorkspaceMessage(null) }} aria-invalid={displayedWorkspaceForm.description.trim().length > 500} className="mt-2 w-full resize-none rounded-xl border border-slate-200 px-3 py-2 outline-none focus:ring-4 focus:ring-indigo-100" /></label>
                    {workspaceError && <p role="alert" className="rounded-xl bg-rose-50 p-3 text-sm text-rose-700">{workspaceError}</p>}
                    {workspaceMessage && <p role="status" className="flex items-center gap-2 text-sm text-emerald-700"><CheckCircle2 className="h-4 w-4" />{workspaceMessage}</p>}
                    <div className="flex items-center justify-between gap-3"><span className="text-xs text-indigo-600" role="status">{workspaceQuery.isFetching && !workspaceMutation.isPending ? 'Refreshing profile...' : ''}</span><button type="submit" disabled={!workspaceDirty || !workspaceValid || workspaceMutation.isPending} className="flex h-10 items-center gap-2 rounded-xl bg-indigo-600 px-4 text-sm font-semibold text-white disabled:opacity-50"><Save className="h-4 w-4" />{workspaceMutation.isPending ? 'Saving...' : 'Save profile'}</button></div>
                </form>}
            </section>

            <section className="rounded-2xl border border-slate-200/80 bg-white p-5 shadow-sm sm:p-6">
                <div className="flex items-start gap-3"><div className="rounded-xl bg-violet-50 p-2.5 text-violet-600"><Settings2 className="h-5 w-5" /></div><div><h2 className="font-bold text-slate-950">Interface defaults</h2><p className="mt-1 text-sm text-slate-500">Saved in this browser. Changes apply on the next relevant page visit, not to a page already open.</p></div></div>
                <div className="mt-6 grid gap-4 sm:grid-cols-2">
                    <label className="text-sm font-medium text-slate-700">Dashboard and Analytics range<select value={preferenceDraft.defaultAnalyticsRange} onChange={(event) => setPreferenceDraft({ ...preferenceDraft, defaultAnalyticsRange: event.target.value as InterfacePreferences['defaultAnalyticsRange'] })} className="mt-2 h-11 w-full rounded-xl border border-slate-200 bg-white px-3"><option value="7">Last 7 days</option><option value="30">Last 30 days</option><option value="90">Last 90 days</option><option value="all">All time</option></select></label>
                    <label className="text-sm font-medium text-slate-700">Lead page size<select value={preferenceDraft.defaultLeadPageSize} onChange={(event) => setPreferenceDraft({ ...preferenceDraft, defaultLeadPageSize: Number(event.target.value) as InterfacePreferences['defaultLeadPageSize'] })} className="mt-2 h-11 w-full rounded-xl border border-slate-200 bg-white px-3"><option value="10">10 rows</option><option value="20">20 rows</option><option value="50">50 rows</option></select></label>
                    <label className="text-sm font-medium text-slate-700">Leads sorting<select value={preferenceDraft.defaultLeadSort} onChange={(event) => setPreferenceDraft({ ...preferenceDraft, defaultLeadSort: event.target.value as InterfacePreferences['defaultLeadSort'] })} className="mt-2 h-11 w-full rounded-xl border border-slate-200 bg-white px-3"><option value="createdAt,desc">Newest first</option><option value="createdAt,asc">Oldest first</option><option value="qualificationScore,desc">Highest AI score</option><option value="estimatedBudget,desc">Highest value</option></select></label>
                    <label className="text-sm font-medium text-slate-700">AI Qualification view<select value={preferenceDraft.defaultQualificationState} onChange={(event) => setPreferenceDraft({ ...preferenceDraft, defaultQualificationState: event.target.value as InterfacePreferences['defaultQualificationState'] })} className="mt-2 h-11 w-full rounded-xl border border-slate-200 bg-white px-3"><option value="">All</option><option value="PROCESSING">Processing</option><option value="SUCCESSFULLY_QUALIFIED">Successfully qualified</option><option value="FAILED">Failed</option></select></label>
                </div>
                {preferenceMessage && <p role="status" className={`mt-4 text-sm ${preferenceMessage.includes('unavailable') ? 'text-rose-700' : 'text-emerald-700'}`}>{preferenceMessage}</p>}
                <div className="mt-6 flex flex-wrap justify-end gap-3"><button ref={resetTriggerRef} type="button" onClick={() => setResetOpen(true)} className="flex h-10 items-center gap-2 rounded-xl border border-slate-200 px-4 text-sm font-semibold text-slate-600"><RotateCcw className="h-4 w-4" />Reset defaults</button><button type="button" onClick={savePreferences} disabled={!preferencesDirty} className="flex h-10 items-center gap-2 rounded-xl bg-indigo-600 px-4 text-sm font-semibold text-white disabled:opacity-50"><Save className="h-4 w-4" />Save defaults</button></div>
            </section>

            <section className="rounded-2xl border border-slate-200/80 bg-white p-5 shadow-sm sm:p-6 xl:col-span-2">
                <div className="flex flex-wrap items-start justify-between gap-4"><div className="flex items-start gap-3"><div className="rounded-xl bg-emerald-50 p-2.5 text-emerald-600"><Bot className="h-5 w-5" /></div><div><h2 className="font-bold text-slate-950">Automation configuration</h2><p className="mt-1 text-sm text-slate-500">Read-only backend configuration. This does not report live n8n, Gemini, database, or end-to-end health.</p></div></div><button type="button" onClick={() => automationQuery.refetch()} disabled={automationQuery.isFetching} className="flex h-10 items-center gap-2 rounded-xl border border-slate-200 px-4 text-sm font-semibold text-slate-600 disabled:opacity-50"><RefreshCw className={`h-4 w-4 ${automationQuery.isFetching ? 'animate-spin' : ''}`} />Refresh</button></div>
                {automationQuery.isLoading && <p role="status" className="mt-6 text-sm text-slate-500">Loading automation configuration...</p>}
                {automationQuery.isError && <div role="alert" className="mt-6 flex items-center gap-2 rounded-xl bg-rose-50 p-4 text-sm text-rose-700"><AlertTriangle className="h-4 w-4" />Automation configuration is unavailable.</div>}
                {automationQuery.data && <dl className="mt-6 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">{[
                    ['Reliable dispatch', automationQuery.data.dispatcherEnabled ? 'Enabled' : 'Disabled'],
                    ['Legacy callback', automationQuery.data.legacyCallbackEnabled ? 'Enabled' : 'Disabled'],
                    ['Safe qualification retry', automationQuery.data.retryEnabled ? 'Enabled' : 'Disabled'],
                    ['Attempt tracking', automationQuery.data.attemptTrackingAvailable ? 'Available' : 'Unavailable'],
                ].map(([label, value]) => <div key={label} className="rounded-xl bg-slate-50 p-4"><dt className="text-xs font-semibold uppercase tracking-wide text-slate-400">{label}</dt><dd className="mt-2 font-semibold text-slate-800">{value}</dd></div>)}</dl>}
            </section>
        </div>

        {resetOpen && <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/50 p-4"><div ref={resetDialogRef} tabIndex={-1} role="alertdialog" aria-modal="true" aria-labelledby="reset-preferences-title" className="w-full max-w-md rounded-2xl bg-white p-6 shadow-2xl outline-none"><div className="flex items-start justify-between"><h2 id="reset-preferences-title" className="text-lg font-bold text-slate-950">Reset interface defaults?</h2><button type="button" aria-label="Close reset confirmation" onClick={() => { setResetOpen(false); requestAnimationFrame(() => resetTriggerRef.current?.focus()) }}><X className="h-5 w-5" /></button></div><p className="mt-3 text-sm leading-6 text-slate-600">This clears the saved browser preferences and restores the built-in defaults. Server workspace settings are not affected.</p><div className="mt-6 flex justify-end gap-3"><button type="button" onClick={() => { setResetOpen(false); requestAnimationFrame(() => resetTriggerRef.current?.focus()) }} className="h-10 rounded-xl border border-slate-200 px-4 text-sm font-semibold">Cancel</button><button type="button" onClick={confirmReset} className="h-10 rounded-xl bg-rose-600 px-4 text-sm font-semibold text-white">Reset defaults</button></div></div></div>}
    </main>
}
