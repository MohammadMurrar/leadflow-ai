import { AlertTriangle, Bot, CheckCircle2, Clock3, Search, Sparkles, X } from 'lucide-react'
import type { DashboardStats } from '../types/dashboard'
import type { Lead, QualificationState } from '../types/lead'
import type { PageResponse } from '../types/page'
import LeadRowActions from './LeadRowActions'
import type { LeadSort } from './LeadsPage'

interface AiQualificationPageProps {
    page: PageResponse<Lead> | null
    leads: Lead[]
    summary: DashboardStats | undefined
    searchInput: string
    qualificationState: QualificationState | ''
    sort: LeadSort
    isLoading: boolean
    isFetching: boolean
    isPlaceholderData: boolean
    isError: boolean
    isSummaryLoading: boolean
    isSummaryError: boolean
    onSearchChange: (value: string) => void
    onStateChange: (state: QualificationState | '') => void
    onSortChange: (sort: LeadSort) => void
    onPageChange: (page: number) => void
    onRetry: () => void
    onSummaryRetry: () => void
}

function humanize(value: string) {
    return value.toLowerCase().replaceAll('_', ' ').replace(/\b\w/g, (letter) => letter.toUpperCase())
}

function formatDate(value: string) {
    const date = new Date(value)
    return Number.isNaN(date.getTime()) ? 'Not provided' : new Intl.DateTimeFormat(undefined, {
        dateStyle: 'medium', timeStyle: 'short',
    }).format(date)
}

function getInitials(fullName: string) {
    return fullName.trim().split(/\s+/).slice(0, 2).map((part) => part[0]).join('').toUpperCase()
}

export default function AiQualificationPage({
    page, leads, summary, searchInput, qualificationState, sort,
    isLoading, isFetching, isPlaceholderData, isError,
    isSummaryLoading, isSummaryError, onSearchChange, onStateChange,
    onSortChange, onPageChange, onRetry, onSummaryRetry,
}: AiQualificationPageProps) {
    const processing = (summary?.statusCounts.NEW ?? 0) + (summary?.statusCounts.QUALIFYING ?? 0)
    const failed = summary?.statusCounts.AUTOMATION_FAILED ?? 0
    const hasFilters = searchInput.trim().length > 0 || qualificationState !== ''
    const currentPage = page?.number ?? 0
    const totalPages = page?.totalPages ?? 0
    const totalElements = page?.totalElements ?? 0
    const cards = [
        { label: 'Processing', value: processing, icon: Clock3, style: 'bg-blue-50 text-blue-600' },
        { label: 'Successfully qualified', value: summary?.qualifiedLeads ?? 0, icon: CheckCircle2, style: 'bg-emerald-50 text-emerald-600' },
        { label: 'Not qualified', value: failed, icon: AlertTriangle, style: 'bg-rose-50 text-rose-600' },
        { label: 'Average AI score', value: summary ? summary.averageAiScore.toFixed(1) : '0.0', icon: Sparkles, style: 'bg-violet-50 text-violet-600' },
    ]

    return (
        <main className="px-4 py-7 sm:px-6 lg:px-8">
            <section className="mb-7">
                <p className="text-sm font-semibold text-indigo-600">AI operations</p>
                <h1 className="mt-1 text-2xl font-bold tracking-tight text-slate-950 sm:text-3xl">AI Qualification</h1>
                <p className="mt-2 text-sm text-slate-500">Monitor processing and review completed qualification results.</p>
                <p className="mt-1 text-xs font-medium text-slate-400">Summary scope: all-time qualification activity</p>
            </section>

            {isSummaryError && !summary && <div className="mb-5 flex items-center justify-between gap-3 rounded-xl border border-rose-100 bg-rose-50 p-4 text-sm text-rose-700"><span>Could not load qualification summary.</span><button type="button" onClick={onSummaryRetry} className="font-semibold underline underline-offset-2">Retry</button></div>}
            <section className="mb-6 grid gap-4 sm:grid-cols-2 xl:grid-cols-4" aria-label="All-time qualification summary" aria-busy={isSummaryLoading && !summary}>
                {cards.map((card) => <article key={card.label} className="rounded-2xl border border-slate-200/80 bg-white p-5 shadow-sm">
                    <div className={`flex h-10 w-10 items-center justify-center rounded-xl ${card.style}`}><card.icon className="h-5 w-5" /></div>
                    <p className="mt-4 text-sm font-medium text-slate-500">{card.label}</p>
                    <p className="mt-1 text-2xl font-bold text-slate-950">{!summary ? '—' : typeof card.value === 'number' ? card.value.toLocaleString() : card.value}</p>
                    {card.label === 'Average AI score' && <p className="mt-1 text-xs text-slate-400">Only leads with a real score</p>}
                </article>)}
            </section>

            <section className="overflow-hidden rounded-2xl border border-slate-200/80 bg-white shadow-sm">
                <div className="border-b border-slate-100 p-5 sm:p-6">
                    <div className="flex flex-col gap-4 xl:flex-row xl:items-center xl:justify-between">
                        <div><h2 className="font-bold text-slate-950">Qualification queue</h2><p className="mt-1 text-sm text-slate-400">Server-filtered processing, completed, and failed qualification records.</p></div>
                        <div className="flex flex-col gap-3 sm:flex-row sm:flex-wrap">
                            <label className="relative sm:w-64"><span className="sr-only">Search qualification queue</span><Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" /><input type="search" maxLength={100} value={searchInput} onChange={(event) => onSearchChange(event.target.value)} placeholder="Search qualification records..." className="h-10 w-full rounded-xl border border-slate-200 bg-slate-50 pl-9 pr-9 text-sm outline-none focus:border-indigo-300 focus:ring-4 focus:ring-indigo-100" />{searchInput && <button type="button" aria-label="Clear qualification search" onClick={() => onSearchChange('')} className="absolute right-2 top-1/2 -translate-y-1/2 rounded-md p-1 text-slate-400 hover:bg-slate-200"><X className="h-3.5 w-3.5" /></button>}</label>
                            <label><span className="sr-only">Filter by qualification state</span><select value={qualificationState} onChange={(event) => onStateChange(event.target.value as QualificationState | '')} className="h-10 rounded-xl border border-slate-200 bg-white px-3 text-sm text-slate-600 outline-none focus:border-indigo-300 focus:ring-4 focus:ring-indigo-100"><option value="">All qualification states</option><option value="PROCESSING">Processing</option><option value="SUCCESSFULLY_QUALIFIED">Successfully qualified</option><option value="FAILED">Failed</option></select></label>
                            <label><span className="sr-only">Sort qualification queue</span><select value={sort} onChange={(event) => onSortChange(event.target.value as LeadSort)} className="h-10 rounded-xl border border-slate-200 bg-white px-3 text-sm text-slate-600 outline-none focus:border-indigo-300 focus:ring-4 focus:ring-indigo-100"><option value="createdAt,desc">Newest first</option><option value="createdAt,asc">Oldest first</option><option value="qualificationScore,desc">Highest AI score</option></select></label>
                            {hasFilters && <button type="button" onClick={() => { onSearchChange(''); onStateChange('') }} className="h-10 text-sm font-semibold text-indigo-600 hover:text-indigo-700">Clear filters</button>}
                        </div>
                    </div>
                    <span className="mt-3 block h-4 text-xs font-medium text-indigo-600" role="status" aria-live="polite">{isFetching && !isLoading && isPlaceholderData ? 'Updating qualification results...' : ''}</span>
                </div>

                {isLoading && <div className="px-6 py-16 text-center"><Clock3 className="mx-auto mb-3 h-6 w-6 animate-spin text-indigo-600" /><p className="text-sm text-slate-500">Loading qualification records...</p></div>}
                {isError && !page && <div className="mx-6 my-5 flex items-center justify-between gap-3 rounded-xl border border-rose-100 bg-rose-50 p-4 text-sm text-rose-700"><span>Could not load qualification records.</span><button type="button" onClick={onRetry} className="font-semibold underline underline-offset-2">Retry</button></div>}
                {!isLoading && (!isError || page) && leads.length === 0 && <div className="px-6 py-16 text-center"><Bot className="mx-auto mb-3 h-8 w-8 text-slate-300" /><p className="font-medium text-slate-700">{qualificationState === 'PROCESSING' ? 'No processing leads' : qualificationState === 'FAILED' ? 'No failed automations' : hasFilters ? 'No qualification records match these controls' : 'No qualification records yet'}</p><p className="mt-1 text-sm text-slate-400">{hasFilters ? 'Try a different search or qualification state.' : 'New leads will appear here when qualification begins.'}</p></div>}

                {!isLoading && leads.length > 0 && <div className="overflow-x-auto"><table className="w-full min-w-[980px] text-left"><thead><tr className="border-b border-slate-100 bg-slate-50/60 text-xs font-semibold uppercase tracking-wider text-slate-400"><th className="px-6 py-4">Lead</th><th className="px-6 py-4">Requested service</th><th className="px-6 py-4">Status</th><th className="px-6 py-4">AI score</th><th className="px-6 py-4">Priority</th><th className="px-6 py-4">Created</th><th className="px-6 py-4">Updated</th><th className="px-6 py-4"><span className="sr-only">Actions</span></th></tr></thead><tbody className="divide-y divide-slate-100">{leads.map((lead) => <tr key={lead.id} className="hover:bg-slate-50/70"><td className="px-6 py-4"><div className="flex items-center gap-3"><div className="flex h-10 w-10 items-center justify-center rounded-xl bg-indigo-50 text-xs font-bold text-indigo-700">{getInitials(lead.fullName)}</div><div><p className="text-sm font-semibold text-slate-800">{lead.fullName}</p><p className="text-xs text-slate-400">{lead.company || lead.email}</p></div></div></td><td className="px-6 py-4 text-sm text-slate-600">{lead.requestedService}</td><td className="px-6 py-4"><span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${lead.status === 'AUTOMATION_FAILED' ? 'bg-rose-50 text-rose-700' : lead.status === 'QUALIFYING' || lead.status === 'NEW' ? 'bg-blue-50 text-blue-700' : 'bg-emerald-50 text-emerald-700'}`}>{humanize(lead.status)}</span>{lead.status === 'AUTOMATION_FAILED' && <p className="mt-2 max-w-52 text-xs text-slate-500">Automated qualification did not complete.</p>}</td><td className="px-6 py-4 text-sm font-semibold text-slate-700">{lead.qualificationScore ?? 'Analyzing'}</td><td className="px-6 py-4 text-sm text-slate-600">{lead.priority && lead.priority !== 'UNASSESSED' ? humanize(lead.priority) : 'Not available'}</td><td className="px-6 py-4 text-sm text-slate-500">{formatDate(lead.createdAt)}</td><td className="px-6 py-4 text-sm text-slate-500">{formatDate(lead.updatedAt)}</td><td className="px-6 py-4"><LeadRowActions lead={lead} /></td></tr>)}</tbody></table></div>}

                {!isLoading && (totalElements > 0 || hasFilters) && <div className="flex flex-col gap-3 border-t border-slate-100 px-5 py-4 text-sm sm:flex-row sm:items-center sm:justify-between"><p className="text-slate-500">{totalElements.toLocaleString()} {totalElements === 1 ? 'record' : 'records'}</p><div className="flex items-center gap-3"><button type="button" disabled={!page || page.first} onClick={() => onPageChange(currentPage - 1)} className="h-9 rounded-lg border border-slate-200 px-3 font-medium text-slate-600 disabled:opacity-40">Previous</button><span className="min-w-24 text-center text-slate-500">Page {totalPages === 0 ? 0 : currentPage + 1} of {totalPages}</span><button type="button" disabled={!page || page.last || totalPages === 0} onClick={() => onPageChange(currentPage + 1)} className="h-9 rounded-lg border border-slate-200 px-3 font-medium text-slate-600 disabled:opacity-40">Next</button></div></div>}
            </section>
        </main>
    )
}
