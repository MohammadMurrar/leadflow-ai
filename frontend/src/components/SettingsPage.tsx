import { useEffect, useMemo, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import axios from 'axios'
import { AlertTriangle, Bot, CheckCircle2, RefreshCw, RotateCcw, Save, Settings2, X } from 'lucide-react'
import { getAutomationStatus, getWorkspaceSettings, updateWorkspaceSettings } from '../services/settingsApi'
import type { InterfacePreferences, WorkspaceSettings } from '../types/settings'
import { SUPPORTED_CURRENCIES, isSupportedCurrency, type SupportedCurrency } from '../types/currency'
import { useTheme } from '../theme/themeContext'
import {
    resetInterfacePreferences,
    saveInterfacePreferences,
} from '../utils/interfacePreferences'

interface SettingsPageProps {
    preferences: InterfacePreferences
    onPreferencesSaved: (preferences: InterfacePreferences) => void
}

type WorkspaceForm = {
    workspaceName: string
    contactEmail: string
    description: string
    publicBrandName: string
    publicTagline: string
    publicLogoPath: string
    timeZone: string
    currency: SupportedCurrency
    responseTimeText: string
    privacyPolicyUrl: string
    privacyNoticeText: string
    privacyNoticeVersion: string
    notificationRecipientsText: string
}

function formFrom(settings: WorkspaceSettings): WorkspaceForm {
    return {
        workspaceName: settings.workspaceName,
        contactEmail: settings.contactEmail ?? '',
        description: settings.description ?? '',
        publicBrandName: settings.publicBrandName ?? '',
        publicTagline: settings.publicTagline ?? '',
        publicLogoPath: settings.publicLogoPath ?? '',
        timeZone: settings.timeZone,
        currency: settings.currency,
        responseTimeText: settings.responseTimeText,
        privacyPolicyUrl: settings.privacyPolicyUrl ?? '',
        privacyNoticeText: settings.privacyNoticeText ?? '',
        privacyNoticeVersion: settings.privacyNoticeVersion ?? '',
        notificationRecipientsText: settings.notificationRecipients.join('\n'),
    }
}

function safeMessage(error: unknown, fallback: string) {
    if (!axios.isAxiosError(error) || !error.response) return 'Could not reach the server. Try again.'
    return fallback
}

function validEmail(value: string) {
    return !value.trim() || /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value.trim())
}

function validLogoPath(value: string) {
    if (!value) return true
    if (!value.startsWith('/') || value.startsWith('//') || value.includes('\\')
        || Array.from(value).some((character) => character.charCodeAt(0) <= 31 || character.charCodeAt(0) === 127)) return false
    const lower = value.toLowerCase()
    if (lower.includes('%2e') || lower.includes('%2f') || lower.includes('%5c')) return false
    return !value.split('/').some((segment) => segment === '.' || segment === '..')
        && !value.includes('?') && !value.includes('#')
}

function validPrivacyUrl(value: string) {
    if (!value) return true
    try {
        const url = new URL(value)
        return url.protocol === 'https:' && Boolean(url.hostname) && !url.username && !url.password
    } catch {
        return false
    }
}

function validTimeZone(value: string) {
    if (!value) return false
    try {
        new Intl.DateTimeFormat('en', { timeZone: value }).format()
        return true
    } catch {
        return false
    }
}

function validCurrency(value: string) {
    return isSupportedCurrency(value)
}

function normalizedRecipients(value: string) {
    return value.split(/\r?\n/).map((email) => email.trim().toLowerCase()).filter(Boolean)
}

export default function SettingsPage({ preferences, onPreferencesSaved }: SettingsPageProps) {
    const { preference: themePreference, setPreference: setThemePreference } = useTheme()
    const queryClient = useQueryClient()
    const [workspaceForm, setWorkspaceForm] = useState<WorkspaceForm | null>(null)
    const [workspaceMessage, setWorkspaceMessage] = useState<string | null>(null)
    const [workspaceError, setWorkspaceError] = useState<string | null>(null)
    const [preferenceDraft, setPreferenceDraft] = useState(preferences)
    const [preferenceMessage, setPreferenceMessage] = useState<string | null>(null)
    const [resetOpen, setResetOpen] = useState(false)
    const resetTriggerRef = useRef<HTMLButtonElement>(null)
    const resetDialogRef = useRef<HTMLDivElement>(null)
    const workspaceSubmissionLockRef = useRef(false)

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
        publicBrandName: displayedWorkspaceForm.publicBrandName.trim(),
        publicTagline: displayedWorkspaceForm.publicTagline.trim(),
        publicLogoPath: displayedWorkspaceForm.publicLogoPath.trim(),
        timeZone: displayedWorkspaceForm.timeZone.trim(),
        currency: displayedWorkspaceForm.currency.trim().toUpperCase(),
        responseTimeText: displayedWorkspaceForm.responseTimeText.trim(),
        privacyPolicyUrl: displayedWorkspaceForm.privacyPolicyUrl.trim(),
        privacyNoticeText: displayedWorkspaceForm.privacyNoticeText.trim(),
        privacyNoticeVersion: displayedWorkspaceForm.privacyNoticeVersion.trim(),
        notificationRecipients: normalizedRecipients(displayedWorkspaceForm.notificationRecipientsText),
    } : null, [displayedWorkspaceForm])
    const workspaceDirty = Boolean(workspaceQuery.data && normalizedWorkspace && (
        normalizedWorkspace.workspaceName !== workspaceQuery.data.workspaceName
        || normalizedWorkspace.contactEmail !== (workspaceQuery.data.contactEmail ?? '')
        || normalizedWorkspace.description !== (workspaceQuery.data.description ?? '')
        || normalizedWorkspace.publicBrandName !== (workspaceQuery.data.publicBrandName ?? '')
        || normalizedWorkspace.publicTagline !== (workspaceQuery.data.publicTagline ?? '')
        || normalizedWorkspace.publicLogoPath !== (workspaceQuery.data.publicLogoPath ?? '')
        || normalizedWorkspace.timeZone !== workspaceQuery.data.timeZone
        || normalizedWorkspace.currency !== workspaceQuery.data.currency
        || normalizedWorkspace.responseTimeText !== workspaceQuery.data.responseTimeText
        || normalizedWorkspace.privacyPolicyUrl !== (workspaceQuery.data.privacyPolicyUrl ?? '')
        || normalizedWorkspace.privacyNoticeText !== (workspaceQuery.data.privacyNoticeText ?? '')
        || normalizedWorkspace.privacyNoticeVersion !== (workspaceQuery.data.privacyNoticeVersion ?? '')
        || normalizedWorkspace.notificationRecipients.join('\n') !== workspaceQuery.data.notificationRecipients.join('\n')
    ))
    const recipientsUnique = Boolean(normalizedWorkspace
        && new Set(normalizedWorkspace.notificationRecipients).size === normalizedWorkspace.notificationRecipients.length)
    const workspaceValidation = useMemo(() => {
        if (!normalizedWorkspace) return null
        const privacyPairMissing = Boolean(normalizedWorkspace.privacyPolicyUrl)
            !== Boolean(normalizedWorkspace.privacyNoticeText)
        const malformedRecipient = normalizedWorkspace.notificationRecipients.some(
            (email) => email.length > 254 || !validEmail(email),
        )
        return {
            publicBrandName: normalizedWorkspace.publicBrandName.length > 120
                ? 'Public brand name must contain 120 characters or fewer.' : null,
            publicTagline: normalizedWorkspace.publicTagline.length > 240
                ? 'Public tagline must contain 240 characters or fewer.' : null,
            publicLogoPath: normalizedWorkspace.publicLogoPath.length > 500
                ? 'Logo path must contain 500 characters or fewer.'
                : !validLogoPath(normalizedWorkspace.publicLogoPath)
                    ? 'Logo path must be a safe same-origin path beginning with one slash.' : null,
            timeZone: !normalizedWorkspace.timeZone
                ? 'Time zone is required.'
                : normalizedWorkspace.timeZone.length > 64 || !validTimeZone(normalizedWorkspace.timeZone)
                    ? 'Enter a valid IANA time zone, such as UTC or Asia/Jerusalem.' : null,
            currency: !normalizedWorkspace.currency
                ? 'Currency is required.'
                : !validCurrency(normalizedWorkspace.currency)
                    ? 'Enter a supported three-letter ISO currency code, such as USD.' : null,
            responseTimeText: !normalizedWorkspace.responseTimeText
                ? 'Response-time promise is required.'
                : normalizedWorkspace.responseTimeText.length > 240
                    ? 'Response-time promise must contain 240 characters or fewer.' : null,
            privacyPolicyUrl: normalizedWorkspace.privacyPolicyUrl.length > 2048
                ? 'Privacy-policy URL must contain 2048 characters or fewer.'
                : !validPrivacyUrl(normalizedWorkspace.privacyPolicyUrl)
                    ? 'Privacy-policy URL must be an absolute HTTPS URL without credentials.'
                    : privacyPairMissing ? 'Privacy-policy URL and notice text must be supplied together.' : null,
            privacyNoticeText: normalizedWorkspace.privacyNoticeText.length > 1000
                ? 'Privacy notice must contain 1000 characters or fewer.'
                : privacyPairMissing ? 'Privacy-policy URL and notice text must be supplied together.' : null,
            privacyNoticeVersion: normalizedWorkspace.privacyNoticeVersion.length > 64
                ? 'Privacy notice version must contain 64 characters or fewer.'
                : normalizedWorkspace.privacyNoticeVersion
                    && (!normalizedWorkspace.privacyPolicyUrl || !normalizedWorkspace.privacyNoticeText)
                    ? 'Privacy notice version requires both a policy URL and notice text.' : null,
            notificationRecipients: normalizedWorkspace.notificationRecipients.length > 10
                ? 'Enter no more than 10 notification recipients.'
                : !recipientsUnique ? 'Notification recipients must be unique after normalization.'
                    : malformedRecipient ? 'Each notification recipient must be a valid email address.' : null,
        }
    }, [normalizedWorkspace, recipientsUnique])
    const workspaceValid = Boolean(normalizedWorkspace?.workspaceName
        && normalizedWorkspace.workspaceName.length <= 120
        && normalizedWorkspace.contactEmail.length <= 180
        && validEmail(normalizedWorkspace.contactEmail)
        && normalizedWorkspace.description.length <= 500
        && workspaceValidation
        && Object.values(workspaceValidation).every((message) => message === null))
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
            publicBrandName: normalizedWorkspace!.publicBrandName || null,
            publicTagline: normalizedWorkspace!.publicTagline || null,
            publicLogoPath: normalizedWorkspace!.publicLogoPath || null,
            timeZone: normalizedWorkspace!.timeZone,
            currency: normalizedWorkspace!.currency as SupportedCurrency,
            responseTimeText: normalizedWorkspace!.responseTimeText,
            privacyPolicyUrl: normalizedWorkspace!.privacyPolicyUrl || null,
            privacyNoticeText: normalizedWorkspace!.privacyNoticeText || null,
            privacyNoticeVersion: normalizedWorkspace!.privacyNoticeVersion || null,
            notificationRecipients: normalizedWorkspace!.notificationRecipients,
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
        onSettled: () => { workspaceSubmissionLockRef.current = false },
    })

    const submitWorkspace = (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault()
        if (workspaceSubmissionLockRef.current || workspaceMutation.isPending) return
        if (!workspaceDirty || !workspaceValid || !normalizedWorkspace || !workspaceQuery.data) return
        workspaceSubmissionLockRef.current = true
        try {
            workspaceMutation.mutate()
        } catch {
            workspaceSubmissionLockRef.current = false
            setWorkspaceError('Workspace profile could not be saved.')
        }
    }

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
        <section className="mb-7"><p className="text-sm font-semibold text-primary">Workspace configuration</p><h1 className="mt-1 text-2xl font-bold tracking-tight text-slate-950 sm:text-3xl">Settings</h1><p className="mt-2 max-w-3xl text-sm text-slate-500">Manage shared workspace identity, browser-local interface defaults, and view safe automation configuration.</p></section>

        <div className="grid gap-6 xl:grid-cols-2">
            <section className="rounded-2xl border border-slate-200/80 bg-white p-5 shadow-sm sm:p-6 xl:col-span-2">
                <h2 className="font-bold text-slate-950">Appearance</h2>
                <p id="appearance-help" className="mt-1 text-sm text-slate-500">System follows your device appearance setting.</p>
                <fieldset className="mt-5 flex flex-wrap gap-3" aria-describedby="appearance-help">
                    <legend className="sr-only">Appearance</legend>
                    {(['system', 'light', 'dark'] as const).map((value) => <label key={value} className="flex min-h-11 items-center gap-2 rounded-lg border border-slate-200 bg-white px-4 text-sm font-semibold text-slate-700 focus-within:ring-2 focus-within:ring-primary-500">
                        <input type="radio" name="appearance" value={value} checked={themePreference === value} onChange={() => setThemePreference(value)} className="accent-primary" />
                        {value[0].toUpperCase() + value.slice(1)}
                    </label>)}
                </fieldset>
            </section>
            <section className="rounded-2xl border border-slate-200/80 bg-white p-5 shadow-sm sm:p-6" aria-busy={workspaceQuery.isFetching}>
                <div className="flex items-start gap-3"><Settings2 className="section-inline-icon" aria-hidden="true" /><div><h2 className="font-bold text-slate-950">Workspace profile</h2><p className="mt-1 text-sm text-slate-500">Saved to this workspace and visible wherever workspace identity is shown.</p></div></div>
                {workspaceQuery.isLoading && !displayedWorkspaceForm && <p role="status" className="mt-6 text-sm text-slate-500">Loading workspace profile...</p>}
                {workspaceQuery.isError && !displayedWorkspaceForm && <div role="alert" className="mt-6 rounded-xl bg-rose-50 p-4 text-sm text-rose-700">Workspace profile could not be loaded. <button type="button" onClick={() => workspaceQuery.refetch()} className="font-semibold underline">Retry</button></div>}
                {displayedWorkspaceForm && <form className="mt-6 space-y-4" onSubmit={submitWorkspace}>
                    <label className="block text-sm font-medium text-slate-700">Workspace name <span className="text-rose-500">*</span><input value={displayedWorkspaceForm.workspaceName} maxLength={240} onChange={(event) => { setWorkspaceForm({ ...displayedWorkspaceForm, workspaceName: event.target.value }); setWorkspaceMessage(null) }} aria-invalid={!normalizedWorkspace?.workspaceName || normalizedWorkspace.workspaceName.length > 120} className="mt-2 h-11 w-full rounded-xl border border-slate-200 px-3 outline-none focus:ring-4 focus:ring-primary-100" /></label>
                    <label className="block text-sm font-medium text-slate-700">Contact email <span className="text-slate-400">(optional)</span><input type="email" value={displayedWorkspaceForm.contactEmail} maxLength={240} onChange={(event) => { setWorkspaceForm({ ...displayedWorkspaceForm, contactEmail: event.target.value }); setWorkspaceMessage(null) }} aria-describedby="contact-email-help" aria-invalid={!validEmail(displayedWorkspaceForm.contactEmail) || displayedWorkspaceForm.contactEmail.trim().length > 180} className="mt-2 h-11 w-full rounded-xl border border-slate-200 px-3 outline-none focus:ring-4 focus:ring-primary-100" /><span id="contact-email-help" className="mt-1 block text-xs text-slate-400">Reference information only. Murravo does not send email from this address.</span></label>
                    <label className="block text-sm font-medium text-slate-700">Description <span className="text-slate-400">(optional)</span><textarea rows={4} value={displayedWorkspaceForm.description} maxLength={600} onChange={(event) => { setWorkspaceForm({ ...displayedWorkspaceForm, description: event.target.value }); setWorkspaceMessage(null) }} aria-invalid={displayedWorkspaceForm.description.trim().length > 500} className="mt-2 w-full resize-none rounded-xl border border-slate-200 px-3 py-2 outline-none focus:ring-4 focus:ring-primary-100" /></label>
                    <fieldset className="space-y-4 rounded-xl border border-slate-200 p-4"><legend className="px-1 text-sm font-semibold text-slate-800">Public identity</legend>
                        <p className="text-xs text-slate-500">Plain text shown on the public inquiry configuration. Workspace name is used when public brand name is blank.</p>
                        <label className="block text-sm font-medium text-slate-700">Public brand name <span className="text-slate-400">(optional)</span><input value={displayedWorkspaceForm.publicBrandName} maxLength={120} onChange={(event) => setWorkspaceForm({ ...displayedWorkspaceForm, publicBrandName: event.target.value })} aria-invalid={Boolean(workspaceValidation?.publicBrandName)} aria-describedby={workspaceValidation?.publicBrandName ? 'public-brand-error' : undefined} className="mt-2 h-11 w-full rounded-xl border border-slate-200 px-3" />{workspaceValidation?.publicBrandName && <span id="public-brand-error" className="mt-1 block text-xs text-rose-600">{workspaceValidation.publicBrandName}</span>}</label>
                        <label className="block text-sm font-medium text-slate-700">Public tagline <span className="text-slate-400">(optional)</span><input value={displayedWorkspaceForm.publicTagline} maxLength={240} onChange={(event) => setWorkspaceForm({ ...displayedWorkspaceForm, publicTagline: event.target.value })} aria-invalid={Boolean(workspaceValidation?.publicTagline)} aria-describedby={workspaceValidation?.publicTagline ? 'public-tagline-error' : undefined} className="mt-2 h-11 w-full rounded-xl border border-slate-200 px-3" />{workspaceValidation?.publicTagline && <span id="public-tagline-error" className="mt-1 block text-xs text-rose-600">{workspaceValidation.publicTagline}</span>}</label>
                        <label className="block text-sm font-medium text-slate-700">Same-origin logo path <span className="text-slate-400">(optional)</span><input value={displayedWorkspaceForm.publicLogoPath} maxLength={500} placeholder="/assets/logo.svg" onChange={(event) => setWorkspaceForm({ ...displayedWorkspaceForm, publicLogoPath: event.target.value })} aria-describedby={`logo-path-help${workspaceValidation?.publicLogoPath ? ' logo-path-error' : ''}`} aria-invalid={Boolean(workspaceValidation?.publicLogoPath)} className="mt-2 h-11 w-full rounded-xl border border-slate-200 px-3" /><span id="logo-path-help" className="mt-1 block text-xs text-slate-400">Use a local path beginning with one slash. Remote URLs are rejected.</span>{workspaceValidation?.publicLogoPath && <span id="logo-path-error" className="mt-1 block text-xs text-rose-600">{workspaceValidation.publicLogoPath}</span>}</label>
                    </fieldset>
                    <fieldset className="grid gap-4 rounded-xl border border-slate-200 p-4 sm:grid-cols-2"><legend className="px-1 text-sm font-semibold text-slate-800">Business defaults</legend>
                        <label className="block text-sm font-medium text-slate-700">IANA time zone <span className="text-rose-500">*</span><input required value={displayedWorkspaceForm.timeZone} maxLength={64} placeholder="UTC" onChange={(event) => setWorkspaceForm({ ...displayedWorkspaceForm, timeZone: event.target.value })} aria-invalid={Boolean(workspaceValidation?.timeZone)} aria-describedby={workspaceValidation?.timeZone ? 'time-zone-error' : undefined} className="mt-2 h-11 w-full rounded-xl border border-slate-200 px-3" />{workspaceValidation?.timeZone && <span id="time-zone-error" className="mt-1 block text-xs text-rose-600">{workspaceValidation.timeZone}</span>}</label>
                        <label className="block text-sm font-medium text-slate-700">Currency <span className="text-rose-500">*</span><select required value={displayedWorkspaceForm.currency} onChange={(event) => setWorkspaceForm({ ...displayedWorkspaceForm, currency: event.target.value as SupportedCurrency })} aria-invalid={Boolean(workspaceValidation?.currency)} aria-describedby={`currency-help${workspaceValidation?.currency ? ' currency-error' : ''}`} className="mt-2 h-11 w-full rounded-xl border border-slate-200 bg-white px-3">{SUPPORTED_CURRENCIES.map(({ code, name }) => <option key={code} value={code}>{code} — {name}</option>)}</select><span id="currency-help" className="mt-1 block text-xs text-slate-400">This changes how monetary values are displayed. Existing amounts are not converted. No live exchange-rate conversion occurs; stored amounts currently support up to two decimal places.</span>{workspaceValidation?.currency && <span id="currency-error" className="mt-1 block text-xs text-rose-600">{workspaceValidation.currency}</span>}</label>
                        <label className="block text-sm font-medium text-slate-700 sm:col-span-2">Response-time promise <span className="text-rose-500">*</span><input required value={displayedWorkspaceForm.responseTimeText} maxLength={240} onChange={(event) => setWorkspaceForm({ ...displayedWorkspaceForm, responseTimeText: event.target.value })} aria-invalid={Boolean(workspaceValidation?.responseTimeText)} aria-describedby={workspaceValidation?.responseTimeText ? 'response-time-error' : undefined} className="mt-2 h-11 w-full rounded-xl border border-slate-200 px-3" />{workspaceValidation?.responseTimeText && <span id="response-time-error" className="mt-1 block text-xs text-rose-600">{workspaceValidation.responseTimeText}</span>}</label>
                    </fieldset>
                    <fieldset className="space-y-4 rounded-xl border border-slate-200 p-4"><legend className="px-1 text-sm font-semibold text-slate-800">Privacy notice</legend>
                        <p className="text-xs text-slate-500">Policy URL and notice text must be supplied together. A version is optional once both are present.</p>
                        <label className="block text-sm font-medium text-slate-700">Privacy policy HTTPS URL <span className="text-slate-400">(optional)</span><input type="url" value={displayedWorkspaceForm.privacyPolicyUrl} maxLength={2048} onChange={(event) => setWorkspaceForm({ ...displayedWorkspaceForm, privacyPolicyUrl: event.target.value })} aria-invalid={Boolean(workspaceValidation?.privacyPolicyUrl)} aria-describedby={workspaceValidation?.privacyPolicyUrl ? 'privacy-url-error' : undefined} className="mt-2 h-11 w-full rounded-xl border border-slate-200 px-3" />{workspaceValidation?.privacyPolicyUrl && <span id="privacy-url-error" className="mt-1 block text-xs text-rose-600">{workspaceValidation.privacyPolicyUrl}</span>}</label>
                        <label className="block text-sm font-medium text-slate-700">Privacy notice text <span className="text-slate-400">(optional)</span><textarea rows={4} value={displayedWorkspaceForm.privacyNoticeText} maxLength={1000} onChange={(event) => setWorkspaceForm({ ...displayedWorkspaceForm, privacyNoticeText: event.target.value })} aria-invalid={Boolean(workspaceValidation?.privacyNoticeText)} aria-describedby={workspaceValidation?.privacyNoticeText ? 'privacy-notice-error' : undefined} className="mt-2 w-full resize-y rounded-xl border border-slate-200 px-3 py-2" />{workspaceValidation?.privacyNoticeText && <span id="privacy-notice-error" className="mt-1 block text-xs text-rose-600">{workspaceValidation.privacyNoticeText}</span>}</label>
                        <label className="block text-sm font-medium text-slate-700">Privacy notice version <span className="text-slate-400">(optional)</span><input value={displayedWorkspaceForm.privacyNoticeVersion} maxLength={64} onChange={(event) => setWorkspaceForm({ ...displayedWorkspaceForm, privacyNoticeVersion: event.target.value })} aria-invalid={Boolean(workspaceValidation?.privacyNoticeVersion)} aria-describedby={workspaceValidation?.privacyNoticeVersion ? 'privacy-version-error' : undefined} className="mt-2 h-11 w-full rounded-xl border border-slate-200 px-3" />{workspaceValidation?.privacyNoticeVersion && <span id="privacy-version-error" className="mt-1 block text-xs text-rose-600">{workspaceValidation.privacyNoticeVersion}</span>}</label>
                    </fieldset>
                    <label className="block text-sm font-medium text-slate-700">Administrator notification recipients <span className="text-slate-400">(optional)</span><textarea rows={5} value={displayedWorkspaceForm.notificationRecipientsText} placeholder="one@example.com&#10;two@example.com" onChange={(event) => setWorkspaceForm({ ...displayedWorkspaceForm, notificationRecipientsText: event.target.value })} aria-describedby={`recipient-help${workspaceValidation?.notificationRecipients ? ' recipient-error' : ''}`} aria-invalid={Boolean(workspaceValidation?.notificationRecipients)} className="mt-2 w-full resize-y rounded-xl border border-slate-200 px-3 py-2" /><span id="recipient-help" className="mt-1 block text-xs text-slate-400">One email per line, up to 10. Addresses are kept private and normalized when saved.</span>{workspaceValidation?.notificationRecipients && <span id="recipient-error" className="mt-1 block text-xs text-rose-600">{workspaceValidation.notificationRecipients}</span>}</label>
                    {workspaceError && <p role="alert" className="rounded-xl bg-rose-50 p-3 text-sm text-rose-700">{workspaceError}</p>}
                    {workspaceMessage && <p role="status" className="flex items-center gap-2 text-sm text-emerald-700"><CheckCircle2 className="h-4 w-4" />{workspaceMessage}</p>}
                    <div className="flex items-center justify-between gap-3"><span className="text-xs text-primary" role="status">{workspaceQuery.isFetching && !workspaceMutation.isPending ? 'Refreshing profile...' : ''}</span><button type="submit" disabled={!workspaceDirty || !workspaceValid || workspaceMutation.isPending} className="flex h-10 items-center gap-2 rounded-xl bg-primary px-4 text-sm font-semibold text-white disabled:opacity-50"><Save className="h-4 w-4" />{workspaceMutation.isPending ? 'Saving...' : 'Save profile'}</button></div>
                </form>}
            </section>

            <section className="rounded-2xl border border-slate-200/80 bg-white p-5 shadow-sm sm:p-6">
                <div className="flex items-start gap-3"><Settings2 className="section-inline-icon" aria-hidden="true" /><div><h2 className="font-bold text-slate-950">Interface defaults</h2><p className="mt-1 text-sm text-slate-500">Saved in this browser. Changes apply on the next relevant page visit, not to a page already open.</p></div></div>
                <div className="mt-6 grid gap-4 sm:grid-cols-2">
                    <label className="text-sm font-medium text-slate-700">Dashboard and Analytics range<select value={preferenceDraft.defaultAnalyticsRange} onChange={(event) => setPreferenceDraft({ ...preferenceDraft, defaultAnalyticsRange: event.target.value as InterfacePreferences['defaultAnalyticsRange'] })} className="mt-2 h-11 w-full rounded-xl border border-slate-200 bg-white px-3"><option value="7">Last 7 days</option><option value="30">Last 30 days</option><option value="90">Last 90 days</option><option value="all">All time</option></select></label>
                    <label className="text-sm font-medium text-slate-700">Lead page size<select value={preferenceDraft.defaultLeadPageSize} onChange={(event) => setPreferenceDraft({ ...preferenceDraft, defaultLeadPageSize: Number(event.target.value) as InterfacePreferences['defaultLeadPageSize'] })} className="mt-2 h-11 w-full rounded-xl border border-slate-200 bg-white px-3"><option value="10">10 rows</option><option value="20">20 rows</option><option value="50">50 rows</option></select></label>
                    <label className="text-sm font-medium text-slate-700">Leads sorting<select value={preferenceDraft.defaultLeadSort} onChange={(event) => setPreferenceDraft({ ...preferenceDraft, defaultLeadSort: event.target.value as InterfacePreferences['defaultLeadSort'] })} className="mt-2 h-11 w-full rounded-xl border border-slate-200 bg-white px-3"><option value="createdAt,desc">Newest first</option><option value="createdAt,asc">Oldest first</option><option value="qualificationScore,desc">Highest AI score</option><option value="estimatedBudget,desc">Highest value</option></select></label>
                    <label className="text-sm font-medium text-slate-700">AI Qualification view<select value={preferenceDraft.defaultQualificationState} onChange={(event) => setPreferenceDraft({ ...preferenceDraft, defaultQualificationState: event.target.value as InterfacePreferences['defaultQualificationState'] })} className="mt-2 h-11 w-full rounded-xl border border-slate-200 bg-white px-3"><option value="">All</option><option value="PROCESSING">Processing</option><option value="SUCCESSFULLY_QUALIFIED">Successfully qualified</option><option value="FAILED">Failed</option></select></label>
                </div>
                {preferenceMessage && <p role="status" className={`mt-4 text-sm ${preferenceMessage.includes('unavailable') ? 'text-rose-700' : 'text-emerald-700'}`}>{preferenceMessage}</p>}
                <div className="mt-6 flex flex-wrap justify-end gap-3"><button ref={resetTriggerRef} type="button" onClick={() => setResetOpen(true)} className="flex h-10 items-center gap-2 rounded-xl border border-slate-200 px-4 text-sm font-semibold text-slate-600"><RotateCcw className="h-4 w-4" />Reset defaults</button><button type="button" onClick={savePreferences} disabled={!preferencesDirty} className="flex h-10 items-center gap-2 rounded-xl bg-primary px-4 text-sm font-semibold text-white disabled:opacity-50"><Save className="h-4 w-4" />Save defaults</button></div>
            </section>

            <section className="rounded-2xl border border-slate-200/80 bg-white p-5 shadow-sm sm:p-6 xl:col-span-2">
                <div className="flex flex-wrap items-start justify-between gap-4"><div className="flex items-start gap-3"><Bot className="section-inline-icon" aria-hidden="true" /><div><h2 className="font-bold text-slate-950">Automation configuration</h2><p className="mt-1 text-sm text-slate-500">Read-only backend configuration. This does not report live n8n, Gemini, database, or end-to-end health.</p></div></div><button type="button" onClick={() => automationQuery.refetch()} disabled={automationQuery.isFetching} className="flex h-10 items-center gap-2 rounded-xl border border-slate-200 px-4 text-sm font-semibold text-slate-600 disabled:opacity-50"><RefreshCw className={`h-4 w-4 ${automationQuery.isFetching ? 'animate-spin' : ''}`} />Refresh</button></div>
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
