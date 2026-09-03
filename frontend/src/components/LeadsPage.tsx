import { Clock3, Search, Users, X } from 'lucide-react'
import type { Lead, LeadStatus } from '../types/lead'
import type { PageResponse } from '../types/page'
import LeadRowActions from './LeadRowActions'
import type { SupportedCurrency } from '../types/currency'
import { formatMoney } from '../utils/money'

export type LeadSort =
    | 'createdAt,desc'
    | 'createdAt,asc'
    | 'qualificationScore,desc'
    | 'estimatedBudget,desc'

interface LeadsPageProps {
    currency: SupportedCurrency
    page: PageResponse<Lead> | null
    leads: Lead[]
    searchInput: string
    status: LeadStatus | ''
    sort: LeadSort
    isLoading: boolean
    isFetching: boolean
    isPlaceholderData: boolean
    isError: boolean
    onSearchChange: (value: string) => void
    onStatusChange: (status: LeadStatus | '') => void
    onSortChange: (sort: LeadSort) => void
    onPageChange: (page: number) => void
    onRetry: () => void
}

const statuses: LeadStatus[] = [
    'NEW',
    'QUALIFYING',
    'QUALIFIED',
    'CONTACTED',
    'WON',
    'LOST',
    'AUTOMATION_FAILED',
]

function getInitials(fullName: string) {
    return fullName.trim().split(/\s+/).slice(0, 2)
        .map((name) => name.charAt(0)).join('').toUpperCase()
}

export default function LeadsPage({
    currency,
    page,
    leads,
    searchInput,
    status,
    sort,
    isLoading,
    isFetching,
    isPlaceholderData,
    isError,
    onSearchChange,
    onStatusChange,
    onSortChange,
    onPageChange,
    onRetry,
}: LeadsPageProps) {
    const hasFilters = searchInput.trim().length > 0 || status !== ''
    const currentPage = page?.number ?? 0
    const totalPages = page?.totalPages ?? 0
    const totalElements = page?.totalElements ?? 0

    return (
        <main className="px-4 py-7 sm:px-6 lg:px-8">
            <section className="mb-7">
                <h1 className="text-2xl font-bold tracking-tight text-slate-950 sm:text-3xl">Leads</h1>
                <p className="mt-2 text-sm text-slate-500">
                    Manage, search, and review every lead in your sales pipeline.
                </p>
            </section>

            <section className="overflow-hidden rounded-2xl border border-slate-200/80 bg-white shadow-sm">
                <div className="flex flex-col gap-4 border-b border-slate-100 p-5 sm:p-6">
                    <div className="flex flex-col gap-3 xl:flex-row xl:items-center xl:justify-between">
                        <div>
                            <h2 className="font-bold text-slate-950">All leads</h2>
                            <p className="mt-1 text-sm text-slate-400">Search, filter, sort, and review your complete lead list.</p>
                        </div>

                        <div className="flex flex-col gap-3 sm:flex-row sm:flex-wrap sm:items-center">
                            <label className="relative min-w-0 sm:w-64">
                                <span className="sr-only">Search leads page</span>
                                <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                                <input
                                    type="search"
                                    maxLength={100}
                                    value={searchInput}
                                    onChange={(event) => onSearchChange(event.target.value)}
                                    placeholder="Search leads..."
                                    className="h-10 w-full rounded-xl border border-slate-200 bg-slate-50 pl-9 pr-9 text-sm outline-none transition placeholder:text-slate-400 focus:border-indigo-300 focus:bg-white focus:ring-4 focus:ring-indigo-100"
                                />
                                {searchInput && (
                                    <button
                                        type="button"
                                        aria-label="Clear Leads page search"
                                        onClick={() => onSearchChange('')}
                                        className="absolute right-2 top-1/2 -translate-y-1/2 rounded-md p-1 text-slate-400 hover:bg-slate-200 hover:text-slate-700"
                                    >
                                        <X className="h-3.5 w-3.5" />
                                    </button>
                                )}
                            </label>

                            <label>
                                <span className="sr-only">Filter leads by status</span>
                                <select
                                    value={status}
                                    onChange={(event) => onStatusChange(event.target.value as LeadStatus | '')}
                                    className="h-10 rounded-xl border border-slate-200 bg-white px-3 text-sm text-slate-600 outline-none focus:border-indigo-300 focus:ring-4 focus:ring-indigo-100"
                                >
                                    <option value="">All statuses</option>
                                    {statuses.map((leadStatus) => (
                                        <option key={leadStatus} value={leadStatus}>
                                            {leadStatus.replaceAll('_', ' ')}
                                        </option>
                                    ))}
                                </select>
                            </label>

                            <label>
                                <span className="sr-only">Sort leads</span>
                                <select
                                    value={sort}
                                    onChange={(event) => onSortChange(event.target.value as LeadSort)}
                                    className="h-10 rounded-xl border border-slate-200 bg-white px-3 text-sm text-slate-600 outline-none focus:border-indigo-300 focus:ring-4 focus:ring-indigo-100"
                                >
                                    <option value="createdAt,desc">Newest first</option>
                                    <option value="createdAt,asc">Oldest first</option>
                                    <option value="qualificationScore,desc">Highest AI score</option>
                                    <option value="estimatedBudget,desc">Highest value</option>
                                </select>
                            </label>

                            {hasFilters && (
                                <button
                                    type="button"
                                    onClick={() => {
                                        onSearchChange('')
                                        onStatusChange('')
                                    }}
                                    className="h-10 text-sm font-semibold text-indigo-600 hover:text-indigo-700"
                                >
                                    Clear filters
                                </button>
                            )}
                        </div>
                    </div>

                    <span className="h-4 text-xs font-medium text-indigo-600" role="status" aria-live="polite">
                        {isFetching && !isLoading && isPlaceholderData ? 'Updating lead results...' : ''}
                    </span>
                </div>

                {isLoading && (
                    <div className="px-6 py-16 text-center">
                        <Clock3 className="mx-auto mb-3 h-6 w-6 animate-spin text-indigo-600" />
                        <p className="text-sm text-slate-500">Loading leads from the backend...</p>
                    </div>
                )}

                {isError && !page && (
                    <div className="mx-6 my-5 flex flex-wrap items-center justify-between gap-3 rounded-xl border border-rose-100 bg-rose-50 px-4 py-3 text-sm text-rose-700">
                        <span>Could not refresh leads. Please try again.</span>
                        <button type="button" onClick={onRetry} className="font-semibold underline decoration-rose-300 underline-offset-2 hover:text-rose-900">
                            Retry
                        </button>
                    </div>
                )}

                {!isLoading && (!isError || page !== null) && leads.length === 0 && (
                    <div className="px-6 py-16 text-center">
                        <Users className="mx-auto mb-3 h-8 w-8 text-slate-300" />
                        <p className="font-medium text-slate-700">{hasFilters ? 'No leads found' : 'No leads yet'}</p>
                        <p className="mt-1 text-sm text-slate-400">
                            {hasFilters
                                ? 'Try a different search, status, or sort selection.'
                                : 'Create your first lead to start AI qualification.'}
                        </p>
                    </div>
                )}

                {!isLoading && leads.length > 0 && (
                    <div className="overflow-x-auto">
                        <table className="w-full min-w-[950px] text-left">
                            <thead>
                                <tr className="border-b border-slate-100 bg-slate-50/60 text-xs font-semibold uppercase tracking-wider text-slate-400">
                                    <th className="px-6 py-4">Lead</th>
                                    <th className="px-6 py-4">Requested service</th>
                                    <th className="px-6 py-4">AI score</th>
                                    <th className="px-6 py-4">Priority</th>
                                    <th className="px-6 py-4">Status</th>
                                    <th className="px-6 py-4">Value</th>
                                    <th className="px-6 py-4"><span className="sr-only">Actions</span></th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-slate-100">
                                {leads.map((lead) => (
                                    <tr key={lead.id} className="transition hover:bg-slate-50/70">
                                        <td className="px-6 py-4">
                                            <div className="flex items-center gap-3">
                                                <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-indigo-50 text-xs font-bold text-indigo-700">{getInitials(lead.fullName)}</div>
                                                <div className="min-w-0">
                                                    <p className="truncate text-sm font-semibold text-slate-800">{lead.fullName}</p>
                                                    <p className="mt-0.5 truncate text-xs text-slate-400">{lead.company || lead.email}</p>
                                                </div>
                                            </div>
                                        </td>
                                        <td className="px-6 py-4 text-sm text-slate-600">{lead.requestedService}</td>
                                        <td className="px-6 py-4">
                                            {lead.qualificationScore !== null ? (
                                                <div className="flex items-center gap-3">
                                                    <div className="h-1.5 w-14 overflow-hidden rounded-full bg-slate-100">
                                                        <div style={{ width: `${Math.min(Math.max(lead.qualificationScore, 0), 100)}%` }} className="h-full rounded-full bg-indigo-600" />
                                                    </div>
                                                    <span className="text-sm font-semibold text-slate-700">{lead.qualificationScore}</span>
                                                </div>
                                            ) : (
                                                <span className="flex items-center gap-2 text-sm text-slate-400"><Clock3 className="h-4 w-4 animate-pulse" />Analyzing</span>
                                            )}
                                        </td>
                                        <td className="px-6 py-4">
                                            {lead.priority && lead.priority !== 'UNASSESSED' ? (
                                                <span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${lead.priority === 'HIGH' ? 'bg-rose-50 text-rose-700' : lead.priority === 'MEDIUM' ? 'bg-amber-50 text-amber-700' : 'bg-slate-100 text-slate-600'}`}>{lead.priority}</span>
                                            ) : <span className="text-sm text-slate-300">—</span>}
                                        </td>
                                        <td className="px-6 py-4">
                                            <span className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-semibold ${lead.status === 'QUALIFIED' ? 'bg-emerald-50 text-emerald-700' : lead.status === 'AUTOMATION_FAILED' ? 'bg-rose-50 text-rose-700' : lead.status === 'WON' ? 'bg-violet-50 text-violet-700' : lead.status === 'LOST' ? 'bg-slate-100 text-slate-600' : 'bg-blue-50 text-blue-700'}`}>
                                                {lead.status.replaceAll('_', ' ')}
                                            </span>
                                        </td>
                                        <td className="px-6 py-4 text-sm font-semibold text-slate-700">{formatMoney(lead.estimatedBudget, currency)}</td>
                                        <td className="px-6 py-4">
                                            <LeadRowActions lead={lead} currency={currency} />
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                )}

                {!isLoading && (totalElements > 0 || hasFilters) && (
                    <div className="flex flex-col gap-3 border-t border-slate-100 px-5 py-4 text-sm sm:flex-row sm:items-center sm:justify-between sm:px-6">
                        <p className="text-slate-500">
                            {totalElements.toLocaleString()} {totalElements === 1 ? 'lead' : 'leads'}
                        </p>
                        <div className="flex items-center gap-3">
                            <button type="button" disabled={!page || page.first} onClick={() => onPageChange(currentPage - 1)} className="h-9 rounded-lg border border-slate-200 px-3 font-medium text-slate-600 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40">Previous</button>
                            <span className="min-w-24 text-center text-slate-500">
                                Page {totalPages === 0 ? 0 : currentPage + 1} of {totalPages}
                            </span>
                            <button type="button" disabled={!page || page.last || totalPages === 0} onClick={() => onPageChange(currentPage + 1)} className="h-9 rounded-lg border border-slate-200 px-3 font-medium text-slate-600 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40">Next</button>
                        </div>
                    </div>
                )}
            </section>
        </main>
    )
}
