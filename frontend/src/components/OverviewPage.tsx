import { ChevronDown, CircleDollarSign, Clock3, Gauge, MoreHorizontal, Search, Target, Users, X } from 'lucide-react'
import { Link } from 'react-router-dom'
import {
    Area,
    AreaChart,
    CartesianGrid,
    ResponsiveContainer,
    Tooltip,
    XAxis,
    YAxis,
} from 'recharts'
import LeadRowActions from './LeadRowActions'
import LeadMobileRecord from './LeadMobileRecord'
import MetricIcon from './MetricIcon'
import type { DashboardRange, DashboardStats } from '../types/dashboard'
import type { Lead } from '../types/lead'
import type { PageResponse } from '../types/page'
import type { SupportedCurrency } from '../types/currency'
import { formatMoney } from '../utils/money'

interface OverviewPageProps {
    currency: SupportedCurrency
    userDisplayName: string
    workspaceDescription: string
    selectedRange: DashboardRange
    setSelectedRange: (range: DashboardRange) => void
    dashboardStats: DashboardStats | undefined
    isDashboardLoading: boolean
    isDashboardFetching: boolean
    isDashboardPlaceholderData: boolean
    isDashboardError: boolean
    refetchDashboard: () => void
    displayedLeadsPage: PageResponse<Lead> | null | undefined
    leads: Lead[]
    leadSearchInput: string
    setLeadSearchInput: (value: string) => void
    isLeadSearchActive: boolean
    isLoading: boolean
    isLeadsFetching: boolean
    isLeadsPlaceholderData: boolean
    isError: boolean
    refetchLeads: () => void
    getInitials: (fullName: string) => string
}

function formatChartDate(value: string) {
    return new Intl.DateTimeFormat('en-US', {
        month: 'short',
        day: 'numeric',
        timeZone: 'UTC',
    }).format(new Date(`${value}T00:00:00Z`))
}

function formatFullChartDate(value: string) {
    return new Intl.DateTimeFormat('en-US', {
        year: 'numeric',
        month: 'long',
        day: 'numeric',
        timeZone: 'UTC',
    }).format(new Date(`${value}T00:00:00Z`))
}

const dashboardRangeLabels: Record<DashboardRange, string> = {
    '7': 'Last 7 days',
    '30': 'Last 30 days',
    '90': 'Last 90 days',
    all: 'All time',
}

export default function OverviewPage({
    currency,
    userDisplayName,
    workspaceDescription,
    selectedRange,
    setSelectedRange,
    dashboardStats,
    isDashboardLoading,
    isDashboardFetching,
    isDashboardPlaceholderData,
    isDashboardError,
    refetchDashboard,
    displayedLeadsPage,
    leads,
    leadSearchInput,
    setLeadSearchInput,
    isLeadSearchActive,
    isLoading,
    isLeadsFetching,
    isLeadsPlaceholderData,
    isError,
    refetchLeads,
    getInitials,
}: OverviewPageProps) {
    const stats = [
        {
            label: 'Total Leads',
            value: dashboardStats?.totalLeads.toLocaleString() ?? '—',
            description: 'All recorded leads',
            icon: Users,
        },
        {
            label: 'Qualified Leads',
            value: dashboardStats?.qualifiedLeads.toLocaleString() ?? '—',
            description: dashboardStats
                ? `${dashboardStats.qualificationRate.toFixed(1)}% qualification rate`
                : 'Qualification rate unavailable',
            icon: Target,
        },
        {
            label: 'Pipeline Value',
            value: dashboardStats ? formatMoney(dashboardStats.pipelineValue, currency) : '—',
            description: 'Total estimated opportunity value',
            icon: CircleDollarSign,
        },
        {
            label: 'Average AI Score',
            value: dashboardStats?.averageAiScore.toFixed(1) ?? '—',
            description: 'Across leads with an AI score',
            icon: Gauge,
        },
    ]

    const successfullyQualifiedCount = dashboardStats?.qualifiedLeads ?? 0
    const processingCount = (dashboardStats?.statusCounts.NEW ?? 0)
        + (dashboardStats?.statusCounts.QUALIFYING ?? 0)
    const automationFailedCount = dashboardStats?.statusCounts.AUTOMATION_FAILED ?? 0
    const successfullyQualifiedDegrees = dashboardStats?.totalLeads
        ? (successfullyQualifiedCount / dashboardStats.totalLeads) * 360
        : 0
    const processingDegrees = dashboardStats?.totalLeads
        ? (processingCount / dashboardStats.totalLeads) * 360
        : 0
    const chartTickInterval = dashboardStats
        ? Math.max(0, Math.ceil(dashboardStats.performance.length / 8) - 1)
        : 0
    const now = new Date()
    const greeting = now.getHours() < 12 ? 'morning' : now.getHours() < 18 ? 'afternoon' : 'evening'
    const currentDate = new Intl.DateTimeFormat(undefined, {
        weekday: 'long', year: 'numeric', month: 'long', day: 'numeric',
    }).format(now)

    return (
          <main className="px-4 py-7 sm:px-6 lg:px-8">
            <section className="mb-7 flex flex-col justify-between gap-4 sm:flex-row sm:items-end">
              <div>
                <p className="mb-1 text-sm font-medium text-primary">
                  {currentDate}
                </p>

                <h1 className="text-2xl font-bold tracking-tight text-slate-950 sm:text-3xl">
                  Good {greeting}, {userDisplayName.split(/\s+/)[0]}
                </h1>

                <p className="mt-2 text-sm text-slate-500">
                  {workspaceDescription}
                </p>
              </div>

              <div className="flex items-center gap-3 self-start sm:self-auto">
                {isDashboardFetching && !isDashboardLoading && (
                    <span className="text-xs font-medium text-primary" role="status">
                      Updating {dashboardRangeLabels[selectedRange].toLowerCase()}...
                    </span>
                )}
                <label className="relative">
                  <span className="sr-only">Dashboard date range</span>
                  <select
                      value={selectedRange}
                      onChange={(event) => setSelectedRange(event.target.value as DashboardRange)}
                      className="h-10 appearance-none rounded-xl border border-slate-200 bg-white py-0 pl-4 pr-10 text-sm font-medium text-slate-600 shadow-sm outline-none transition hover:bg-slate-50 focus:border-primary-300 focus:ring-4 focus:ring-primary-100"
                  >
                    <option value="7">Last 7 days</option>
                    <option value="30">Last 30 days</option>
                    <option value="90">Last 90 days</option>
                    <option value="all">All time</option>
                  </select>
                  <ChevronDown className="pointer-events-none absolute right-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                </label>
              </div>
            </section>

            {isDashboardError && (
                <div className="mb-5 flex flex-col gap-3 rounded-xl border border-rose-100 bg-rose-50 px-4 py-3 text-sm text-rose-700 sm:flex-row sm:items-center sm:justify-between">
                  <span>
                    Could not refresh dashboard analytics:{' '}
                    Please try the selected range again.
                  </span>
                  <button
                      type="button"
                      onClick={() => void refetchDashboard()}
                      className="self-start font-semibold text-rose-700 underline decoration-rose-300 underline-offset-2 hover:text-rose-900 sm:self-auto"
                  >
                    Retry
                  </button>
                </div>
            )}

            <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
              {stats.map((stat) => (
                  <article
                      key={stat.label}
                      className="rounded-2xl border border-slate-200/80 bg-white p-5 shadow-sm transition hover:-translate-y-0.5 hover:shadow-md"
                  >
                    <div className="mb-5 flex items-start justify-between">
                      <MetricIcon icon={stat.icon} />

                      <span className="rounded-full bg-slate-50 px-2 py-1 text-xs font-semibold text-slate-500">
                        {isDashboardLoading
                            ? 'Loading'
                            : isDashboardPlaceholderData
                              ? 'Updating'
                              : 'Live data'}
                      </span>
                    </div>

                    <p className="text-sm font-medium text-slate-500">
                      {stat.label}
                    </p>

                    <p className="mt-1 text-3xl font-bold tracking-tight text-slate-950">
                      {stat.value}
                    </p>

                    <p className="mt-2 text-xs text-slate-400">
                      {stat.description}
                    </p>
                  </article>
              ))}
            </section>

            <section className="mt-6 grid gap-6 xl:grid-cols-[1.65fr_1fr]">
              <article className="rounded-2xl border border-slate-200/80 bg-white p-5 shadow-sm sm:p-6">
                <div className="mb-8 flex items-center justify-between">
                  <div>
                    <h2 className="font-bold text-slate-950">
                      Lead performance
                    </h2>
                    <p className="mt-1 text-sm text-slate-400">
                      New and qualified leads over time
                    </p>
                  </div>

                  <button
                      type="button"
                      aria-label="Performance options"
                      className="rounded-lg p-2 text-slate-400 hover:bg-slate-50 hover:text-slate-700"
                  >
                    <MoreHorizontal className="h-5 w-5" />
                  </button>
                </div>

                <div className="h-64">
                  {isDashboardLoading ? (
                      <div className="flex h-full items-center justify-center rounded-xl bg-slate-50 text-sm text-slate-400">
                        Loading performance data...
                      </div>
                  ) : (
                      <ResponsiveContainer width="100%" height="100%">
                        <AreaChart data={dashboardStats?.performance ?? []} margin={{ top: 8, right: 8, left: -24, bottom: 0 }}>
                          <defs>
                            <linearGradient id="totalLeadsGradient" x1="0" y1="0" x2="0" y2="1">
                              <stop offset="5%" stopColor="#142A43" stopOpacity={0.24} />
                              <stop offset="95%" stopColor="#142A43" stopOpacity={0} />
                            </linearGradient>
                          </defs>
                          <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#e2e8f0" />
                          <XAxis
                              dataKey="date"
                              tickFormatter={formatChartDate}
                              interval={chartTickInterval}
                              minTickGap={28}
                              axisLine={false}
                              tickLine={false}
                              tick={{ fill: '#94a3b8', fontSize: 11 }}
                          />
                          <YAxis
                              allowDecimals={false}
                              axisLine={false}
                              tickLine={false}
                              tick={{ fill: '#94a3b8', fontSize: 11 }}
                          />
                          <Tooltip labelFormatter={(label) => formatFullChartDate(String(label))} />
                          <Area
                              type="monotone"
                              dataKey="totalLeads"
                              name="New leads"
                              stroke="#142A43"
                              strokeWidth={2}
                              fill="url(#totalLeadsGradient)"
                          />
                          <Area
                              type="monotone"
                              dataKey="qualifiedLeads"
                              name="Qualified leads"
                              stroke="#10b981"
                              strokeWidth={2}
                              fill="transparent"
                          />
                        </AreaChart>
                      </ResponsiveContainer>
                  )}
                </div>
              </article>

              <article className="rounded-2xl border border-slate-200/80 bg-white p-5 shadow-sm sm:p-6">
                <div className="mb-6 flex items-center justify-between">
                  <div>
                    <h2 className="font-bold text-slate-950">
                      Qualification overview
                    </h2>
                    <p className="mt-1 text-sm text-slate-400">
                      AI qualification status
                    </p>
                  </div>

                  <div className="metric-icon">
                    <Gauge className="h-5 w-5" />
                  </div>
                </div>

                <div className="flex flex-col items-center py-2">
                  <div
                      role="img"
                      aria-label={dashboardStats
                          ? `Qualification overview: ${successfullyQualifiedCount} successfully qualified, ${processingCount} processing, and ${automationFailedCount} Not qualified out of ${dashboardStats.totalLeads} total leads. Qualification rate ${dashboardStats.qualificationRate.toFixed(1)}%.`
                          : 'Qualification overview is loading.'}
                      className="relative flex h-40 w-40 items-center justify-center rounded-full"
                      style={{
                        background: dashboardStats?.totalLeads
                            ? `conic-gradient(#142A43 0deg ${successfullyQualifiedDegrees}deg, #6FA4C9 ${successfullyQualifiedDegrees}deg ${successfullyQualifiedDegrees + processingDegrees}deg, #F4775B ${successfullyQualifiedDegrees + processingDegrees}deg 360deg)`
                            : '#e2e8f0',
                      }}
                  >
                    <div className="flex h-[120px] w-[120px] flex-col items-center justify-center rounded-full bg-white">
                    <span className="text-3xl font-bold text-slate-950">
                      {dashboardStats ? `${dashboardStats.qualificationRate.toFixed(1)}%` : '—'}
                    </span>
                      <span className="mt-1 text-xs text-slate-400">
                      Qualified
                    </span>
                    </div>
                  </div>
                </div>

                <div className="mt-6 space-y-3">
                  <div className="flex items-center justify-between text-sm">
                  <span className="flex items-center gap-2 text-slate-500">
                    <span className="h-2.5 w-2.5 rounded-full bg-primary" />
                    Successfully qualified
                  </span>

                    <span className="font-semibold text-slate-800">
                    {successfullyQualifiedCount.toLocaleString()} leads
                  </span>
                  </div>

                  <div className="flex items-center justify-between text-sm">
                  <span className="flex items-center gap-2 text-slate-500">
                    <span className="h-2.5 w-2.5 rounded-full bg-blue-500" />
                    Processing
                  </span>

                    <span className="font-semibold text-slate-800">
                    {processingCount.toLocaleString()} leads
                  </span>
                  </div>

                  <div className="flex items-center justify-between text-sm">
                  <span className="flex items-center gap-2 text-slate-500">
                    <span className="h-2.5 w-2.5 rounded-full bg-rose-500" />
                    Automation failed
                  </span>

                    <span className="font-semibold text-slate-800">
                    {automationFailedCount.toLocaleString()} leads
                  </span>
                  </div>
                </div>
              </article>
            </section>

            <section className="mt-6 overflow-hidden rounded-2xl border border-slate-200/80 bg-white shadow-sm">
              <div className="flex flex-col justify-between gap-4 border-b border-slate-100 p-5 sm:flex-row sm:items-center sm:p-6">
                <div>
                  <h2 className="font-bold text-slate-950">Recent leads</h2>
                  <p className="mt-1 text-sm text-slate-400">
                    Latest opportunities analyzed by Murravo
                  </p>
                </div>

                <div className="flex w-full flex-wrap items-center gap-3 sm:w-auto sm:justify-end">
                  <label className="relative min-w-0 flex-1 sm:w-64 sm:flex-none">
                    <span className="sr-only">Search recent leads</span>
                    <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                    <input
                        type="search"
                        maxLength={100}
                        value={leadSearchInput}
                        onChange={(event) => setLeadSearchInput(event.target.value)}
                        placeholder="Search leads..."
                        className="h-10 w-full rounded-xl border border-slate-200 bg-slate-50 pl-9 pr-9 text-sm outline-none transition placeholder:text-slate-400 focus:border-primary-300 focus:bg-white focus:ring-4 focus:ring-primary-100"
                    />
                    {leadSearchInput && (
                        <button
                            type="button"
                            aria-label="Clear lead search"
                            onClick={() => setLeadSearchInput('')}
                            className="absolute right-2 top-1/2 -translate-y-1/2 rounded-md p-1 text-slate-400 transition hover:bg-slate-200 hover:text-slate-700"
                        >
                          <X className="h-3.5 w-3.5" />
                        </button>
                    )}
                  </label>

                  <span className="h-4 text-xs font-medium text-primary" role="status" aria-live="polite">
                    {isLeadsFetching && !isLoading && isLeadsPlaceholderData ? 'Searching leads...' : ''}
                  </span>

                  <Link
                      to="/leads"
                      className="text-sm font-semibold text-primary hover:text-primary-700"
                  >
                    View all leads
                  </Link>
                </div>
              </div>

              {isLoading && (
                  <div className="px-6 py-12 text-center">
                    <Clock3 className="mx-auto mb-3 h-6 w-6 animate-spin text-primary" />
                    <p className="text-sm text-slate-500">
                      Loading leads from the backend...
                    </p>
                  </div>
              )}

              {isError && !displayedLeadsPage && (
                  <div className="mx-6 my-5 flex flex-wrap items-center justify-between gap-3 rounded-xl border border-rose-100 bg-rose-50 px-4 py-3 text-sm text-rose-700">
                    <span>Could not refresh leads. Please try again.</span>
                    <button
                        type="button"
                        onClick={() => void refetchLeads()}
                        className="font-semibold underline decoration-rose-300 underline-offset-2 hover:text-rose-900"
                    >
                      Retry
                    </button>
                  </div>
              )}

              {!isLoading && (!isError || displayedLeadsPage) && leads.length === 0 && (
                  <div className="px-6 py-12 text-center">
                    <Users className="mx-auto mb-3 h-8 w-8 text-slate-300" />
                    <p className="font-medium text-slate-700">
                      {isLeadSearchActive ? 'No leads found' : 'No leads yet'}
                    </p>
                    <p className="mt-1 text-sm text-slate-400">
                      {isLeadSearchActive
                          ? 'Try a different name, email, company, phone, or service.'
                          : 'Create your first lead to start AI qualification.'}
                    </p>
                    {isLeadSearchActive && (
                        <button
                            type="button"
                            onClick={() => setLeadSearchInput('')}
                            className="mt-4 text-sm font-semibold text-primary hover:text-primary-700"
                        >
                          Clear search
                        </button>
                    )}
                  </div>
              )}

              {!isLoading && leads.length > 0 && (
                  <>
                  <div className="divide-y divide-slate-100 md:hidden" aria-label="Recent lead records">
                    {leads.map((lead) => <LeadMobileRecord key={lead.id} lead={lead} currency={currency} context="overview" />)}
                  </div>
                  <div className="hidden overflow-x-auto md:block">
                    <table className="w-full min-w-[950px] text-left">
                      <thead>
                      <tr className="border-b border-slate-100 bg-slate-50/60 text-xs font-semibold uppercase tracking-wider text-slate-400">
                        <th className="px-6 py-4">Lead</th>
                        <th className="px-6 py-4">Requested service</th>
                        <th className="px-6 py-4">AI score</th>
                        <th className="px-6 py-4">Priority</th>
                        <th className="px-6 py-4">Status</th>
                        <th className="px-6 py-4">Value</th>
                        <th className="relative px-6 py-4">
                          <span className="sr-only">Actions</span>
                        </th>
                      </tr>
                      </thead>

                      <tbody className="divide-y divide-slate-100">
                      {leads.map((lead) => (
                          <tr
                              key={lead.id}
                              className="transition hover:bg-slate-50/70"
                          >
                            <td className="px-6 py-4">
                              <div className="flex items-center gap-3">
                                <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-primary-50 text-xs font-bold text-primary-700">
                                  {getInitials(lead.fullName)}
                                </div>

                                <div className="min-w-0">
                                  <p className="truncate text-sm font-semibold text-slate-800">
                                    {lead.fullName}
                                  </p>

                                  <p className="mt-0.5 truncate text-xs text-slate-400">
                                    {lead.company || lead.email}
                                  </p>
                                </div>
                              </div>
                            </td>

                            <td className="px-6 py-4 text-sm text-slate-600">
                              {lead.requestedService}
                            </td>

                            <td className="px-6 py-4">
                              {lead.qualificationScore !== null ? (
                                  <div className="flex items-center gap-3">
                                    <div className="h-1.5 w-14 overflow-hidden rounded-full bg-slate-100">
                                      <div
                                          style={{
                                            width: `${Math.min(
                                                Math.max(
                                                    lead.qualificationScore,
                                                    0,
                                                ),
                                                100,
                                            )}%`,
                                          }}
                                          className="h-full rounded-full bg-primary"
                                      />
                                    </div>

                                    <span className="text-sm font-semibold text-slate-700">
                                {lead.qualificationScore}
                              </span>
                                  </div>
                              ) : (
                                  <span className="flex items-center gap-2 text-sm text-slate-400">
                              <Clock3 className="h-4 w-4 animate-pulse" />
                              Analyzing
                            </span>
                              )}
                            </td>

                            <td className="px-6 py-4">
                              {lead.priority &&
                              lead.priority !== 'UNASSESSED' ? (
                                  <span
                                      className={`rounded-full px-2.5 py-1 text-xs font-semibold ${
                                          lead.priority === 'HIGH'
                                              ? 'bg-rose-50 text-rose-700'
                                              : lead.priority === 'MEDIUM'
                                                  ? 'bg-amber-50 text-amber-700'
                                                  : 'bg-slate-100 text-slate-600'
                                      }`}
                                  >
                              {lead.priority}
                            </span>
                              ) : (
                                  <span className="text-sm text-slate-300">—</span>
                              )}
                            </td>

                            <td className="px-6 py-4">
                          <span
                              className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-semibold ${
                                  lead.status === 'QUALIFIED'
                                      ? 'bg-emerald-50 text-emerald-700'
                                      : lead.status === 'AUTOMATION_FAILED'
                                          ? 'bg-rose-50 text-rose-700'
                                          : lead.status === 'WON'
                                              ? 'bg-primary-50 text-primary-700'
                                              : lead.status === 'LOST'
                                                  ? 'bg-slate-100 text-slate-600'
                                                  : 'bg-blue-50 text-blue-700'
                              }`}
                          >
                            <span
                                className={`h-1.5 w-1.5 rounded-full ${
                                    lead.status === 'QUALIFIED'
                                        ? 'bg-emerald-500'
                                        : lead.status ===
                                        'AUTOMATION_FAILED'
                                            ? 'bg-rose-500'
                                            : lead.status === 'WON'
                                                ? 'bg-primary-500'
                                                : lead.status === 'LOST'
                                                    ? 'bg-slate-400'
                                                    : 'animate-pulse bg-blue-500'
                                }`}
                            />

                            {lead.status.replaceAll('_', ' ')}
                          </span>
                            </td>

                            <td className="px-6 py-4 text-sm font-semibold text-slate-700">
                              {lead.estimatedBudget !== null
                                  ? formatMoney(lead.estimatedBudget, currency)
                                  : '—'}
                            </td>

                            <td className="px-6 py-4">
                              <LeadRowActions lead={lead} currency={currency} />
                            </td>
                          </tr>
                      ))}
                      </tbody>
                    </table>
                  </div>
                  </>
              )}
            </section>

            <footer className="py-8 text-center text-xs text-slate-400">
              Murravo · AI-assisted lead qualification
            </footer>
          </main>

    )
}
