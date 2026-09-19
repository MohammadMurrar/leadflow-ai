import {
    AlertTriangle,
    BarChart3,
    CircleDollarSign,
    Gauge,
    Target,
    TrendingUp,
    Users,
} from 'lucide-react'
import {
    Area,
    AreaChart,
    CartesianGrid,
    ResponsiveContainer,
    Tooltip,
    XAxis,
    YAxis,
} from 'recharts'
import type { AnalyticsBreakdownItem, AnalyticsResponse } from '../types/analytics'
import type { DashboardRange } from '../types/dashboard'
import type { LeadStatus } from '../types/lead'
import type { SupportedCurrency } from '../types/currency'
import { formatMoney } from '../utils/money'
import MetricIcon from './MetricIcon'

interface AnalyticsPageProps {
    currency: SupportedCurrency
    data: AnalyticsResponse | undefined
    range: DashboardRange
    isLoading: boolean
    isFetching: boolean
    isPlaceholderData: boolean
    isError: boolean
    onRangeChange: (range: DashboardRange) => void
    onRetry: () => void
}

const rangeLabels: Record<DashboardRange, string> = {
    '7': 'Last 7 days',
    '30': 'Last 30 days',
    '90': 'Last 90 days',
    all: 'All time',
}

const statusOrder: LeadStatus[] = [
    'NEW', 'QUALIFYING', 'QUALIFIED', 'CONTACTED', 'WON', 'LOST', 'AUTOMATION_FAILED',
]

function humanize(value: string) {
    return value.toLowerCase().replaceAll('_', ' ').replace(/\b\w/g, (letter) => letter.toUpperCase())
}

function formatPeriod(value: string, monthly: boolean, full = false) {
    return new Intl.DateTimeFormat('en-US', monthly
        ? { year: 'numeric', month: full ? 'long' : 'short', timeZone: 'UTC' }
        : { year: full ? 'numeric' : undefined, month: full ? 'long' : 'short', day: 'numeric', timeZone: 'UTC' })
        .format(new Date(`${value}T00:00:00Z`))
}

function BreakdownCard({
    title, description, items, emptyMessage,
}: {
    title: string
    description: string
    items: AnalyticsBreakdownItem[]
    emptyMessage: string
}) {
    const max = Math.max(...items.map((item) => item.count), 1)
    return (
        <article className="rounded-2xl border border-slate-200/80 bg-white p-5 shadow-sm sm:p-6">
            <h2 className="font-bold text-slate-950">{title}</h2>
            <p className="mt-1 text-sm text-slate-400">{description}</p>
            {items.length === 0 ? (
                <p className="mt-8 rounded-xl bg-slate-50 px-4 py-8 text-center text-sm text-slate-500">{emptyMessage}</p>
            ) : (
                <ul className="mt-5 space-y-4">
                    {items.map((item) => (
                        <li key={item.name}>
                            <div className="mb-1.5 flex items-center justify-between gap-4 text-sm">
                                <span className="truncate font-medium text-slate-700" title={humanize(item.name)}>{humanize(item.name)}</span>
                                <span className="shrink-0 tabular-nums text-slate-500">{item.count.toLocaleString()} · {item.percentage.toFixed(1)}%</span>
                            </div>
                            <div className="h-2 overflow-hidden rounded-full bg-slate-100" aria-hidden="true">
                                <div className="h-full rounded-full bg-primary-500" style={{ width: `${(item.count / max) * 100}%` }} />
                            </div>
                        </li>
                    ))}
                </ul>
            )}
        </article>
    )
}

export default function AnalyticsPage({
    currency,
    data, range, isLoading, isFetching, isPlaceholderData, isError,
    onRangeChange, onRetry,
}: AnalyticsPageProps) {
    const formatCurrency = (value: number) => formatMoney(value, currency)
    const monthly = data?.performanceGranularity === 'MONTHLY'
    const performanceTickInterval = data
        ? Math.max(0, Math.ceil(data.performance.length / (range === '90' ? 7 : 10)) - 1)
        : 0
    const statusItems = data
        ? statusOrder.map((status) => ({
            name: status,
            count: data.statusCounts[status] ?? 0,
            percentage: data.totalLeads === 0 ? 0 : ((data.statusCounts[status] ?? 0) / data.totalLeads) * 100,
        }))
        : []
    const cards = data ? [
        { label: 'Total leads', value: data.totalLeads.toLocaleString(), detail: 'Created in this period', icon: Users },
        { label: 'Successfully qualified', value: data.qualifiedLeads.toLocaleString(), detail: 'Qualified and later lifecycle states', icon: Target },
        { label: 'Qualification rate', value: `${data.qualificationRate.toFixed(1)}%`, detail: 'Successfully qualified ÷ total leads', icon: TrendingUp },
        { label: 'Pipeline value', value: formatMoney(data.pipelineValue, currency), detail: 'Total estimated opportunity value', icon: CircleDollarSign },
        { label: 'Average AI score', value: data.averageAiScore.toFixed(1), detail: 'Only leads with a real score', icon: Gauge },
    ] : []

    if (isLoading && !data) {
        return <main className="px-4 py-7 sm:px-6 lg:px-8"><div className="flex min-h-[420px] items-center justify-center rounded-2xl border border-slate-200 bg-white"><div className="text-center"><BarChart3 className="mx-auto h-8 w-8 animate-pulse text-primary-500" /><p className="mt-3 text-sm text-slate-500">Loading analytics...</p></div></div></main>
    }

    if (isError && !data) {
        return <main className="px-4 py-7 sm:px-6 lg:px-8"><div className="flex min-h-[420px] items-center justify-center rounded-2xl border border-rose-100 bg-white"><div className="text-center"><AlertTriangle className="mx-auto h-8 w-8 text-rose-500" /><h1 className="mt-3 text-lg font-bold text-slate-900">Analytics could not be loaded</h1><p className="mt-1 text-sm text-slate-500">The request did not complete. Your selected range is preserved.</p><button type="button" onClick={onRetry} className="mt-5 rounded-xl bg-primary px-4 py-2 text-sm font-semibold text-white outline-none focus:ring-4 focus:ring-primary-200">Retry</button></div></div></main>
    }

    return (
        <main className="min-w-0 px-4 py-7 sm:px-6 lg:px-8">
            <section className="mb-7 flex flex-col justify-between gap-4 sm:flex-row sm:items-end">
                <div>
                    <p className="text-sm font-semibold text-primary">Decision intelligence</p>
                    <h1 className="mt-1 text-2xl font-bold tracking-tight text-slate-950 sm:text-3xl">Analytics</h1>
                    <p className="mt-2 max-w-2xl text-sm text-slate-500">Lead acquisition, qualification, pipeline opportunity, and service demand using one consistent UTC creation period.</p>
                </div>
                <div className="flex w-full flex-col gap-2 self-start sm:w-auto sm:flex-row sm:items-center sm:gap-3 sm:self-auto">
                    <label className="block sm:order-2"><span className="mb-1.5 block text-sm font-medium text-slate-700 sm:sr-only">Reporting period</span><select value={range} onChange={(event) => onRangeChange(event.target.value as DashboardRange)} className="h-11 w-full rounded-xl border border-slate-200 bg-white px-4 text-sm font-medium text-slate-600 shadow-sm outline-none focus:border-primary-300 focus:ring-4 focus:ring-primary-100 sm:h-10 sm:w-auto"><option value="7">Last 7 days</option><option value="30">Last 30 days</option><option value="90">Last 90 days</option><option value="all">All time</option></select></label>
                    <span className="text-xs font-semibold text-primary sm:order-1 sm:min-w-20 sm:text-right" role="status" aria-live="polite">{isFetching && !isLoading ? `Updating ${rangeLabels[range].toLowerCase()}...` : ''}</span>
                </div>
            </section>

            {isError && data && <div className="mb-5 flex items-center justify-between gap-3 rounded-xl border border-rose-100 bg-rose-50 p-4 text-sm text-rose-700"><span>Analytics could not be refreshed. The last successful data remains visible.</span><button type="button" onClick={onRetry} className="font-semibold underline underline-offset-2 focus:outline-none focus:ring-2 focus:ring-rose-300">Retry</button></div>}

            <section className="mb-6 grid gap-4 sm:grid-cols-2 xl:grid-cols-5" aria-label={`${rangeLabels[range]} analytics summary`} aria-busy={isPlaceholderData}>
                {cards.map((card) => <article key={card.label} className="rounded-2xl border border-slate-200/80 bg-white p-5 shadow-sm"><MetricIcon icon={card.icon} /><p className="mt-4 text-sm font-medium text-slate-500">{card.label}</p><p className="mt-1 text-2xl font-bold text-slate-950">{card.value}</p><p className="mt-1 text-xs leading-5 text-slate-400">{card.detail}</p></article>)}
            </section>

            {data && data.totalLeads === 0 ? (
                <section className="rounded-2xl border border-slate-200/80 bg-white px-6 py-16 text-center shadow-sm"><BarChart3 className="mx-auto h-9 w-9 text-slate-300" /><h2 className="mt-4 text-lg font-bold text-slate-800">No leads in {rangeLabels[range].toLowerCase()}</h2><p className="mt-1 text-sm text-slate-500">Choose a broader period to explore acquisition and qualification performance.</p></section>
            ) : data && (
                <>
                    <article className="mb-6 rounded-2xl border border-slate-200/80 bg-white p-5 shadow-sm sm:p-6">
                        <div className="mb-5"><h2 className="font-bold text-slate-950">Lead performance</h2><p className="mt-1 text-sm text-slate-400">Created leads and successfully qualified leads by {monthly ? 'month' : 'UTC day'}.</p></div>
                        <div className="h-80 w-full" role="img" aria-label={`Trend with ${data.totalLeads} total leads and ${data.qualifiedLeads} successfully qualified leads in ${rangeLabels[range].toLowerCase()}`}>
                            <ResponsiveContainer width="100%" height="100%"><AreaChart data={data.performance} margin={{ top: 8, right: 8, left: -22, bottom: 0 }}><defs><linearGradient id="analyticsTotalGradient" x1="0" y1="0" x2="0" y2="1"><stop offset="5%" stopColor="#185FA5" stopOpacity={0.22} /><stop offset="95%" stopColor="#185FA5" stopOpacity={0} /></linearGradient></defs><CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#e2e8f0" /><XAxis dataKey="periodStart" tickFormatter={(value: string) => formatPeriod(value, monthly)} interval={performanceTickInterval} minTickGap={14} axisLine={false} tickLine={false} tick={{ fill: '#94a3b8', fontSize: 11 }} /><YAxis allowDecimals={false} axisLine={false} tickLine={false} tick={{ fill: '#94a3b8', fontSize: 11 }} /><Tooltip labelFormatter={(label) => formatPeriod(String(label), monthly, true)} formatter={(value, name) => [Number(value).toLocaleString(), name === 'totalLeads' ? 'Total leads' : 'Successfully qualified']} contentStyle={{ borderRadius: 12, border: '1px solid #e2e8f0', boxShadow: '0 8px 24px rgba(15,23,42,0.08)' }} /><Area type="linear" dataKey="totalLeads" name="Total leads" stroke="#185FA5" strokeWidth={2} fill="url(#analyticsTotalGradient)" /><Area type="linear" dataKey="qualifiedLeads" name="Successfully qualified" stroke="#10b981" strokeWidth={2} fill="transparent" /></AreaChart></ResponsiveContainer>
                        </div>
                        <div className="mt-3 flex flex-wrap gap-5 text-xs font-medium text-slate-500"><span className="flex items-center gap-2"><span className="h-2.5 w-2.5 rounded-full bg-primary-500" />Total leads</span><span className="flex items-center gap-2"><span className="h-2.5 w-2.5 rounded-full bg-emerald-500" />Successfully qualified</span></div>
                    </article>

                    <section className="mb-6 grid gap-6 xl:grid-cols-3">
                        <BreakdownCard title="Status distribution" description="Current concrete status for leads created in the period." items={statusItems} emptyMessage="No status data for this period." />
                        <BreakdownCard title="Priority distribution" description="Share of all period leads; unassessed leads remain visible." items={data.priorityBreakdown} emptyMessage="No priority data for this period." />
                        <BreakdownCard title="Qualification categories" description="Share of successfully qualified leads only." items={data.categoryBreakdown} emptyMessage="No qualification categories for this period." />
                    </section>
                    <p className="-mt-3 mb-6 text-xs text-slate-400">Only failures explicitly stored as AUTOMATION_FAILED are included.</p>

                    <article className="overflow-hidden rounded-2xl border border-slate-200/80 bg-white shadow-sm">
                        <div className="p-5 sm:p-6"><h2 className="font-bold text-slate-950">Top requested services</h2><p className="mt-1 text-sm text-slate-400">Up to 10 services, ordered by lead count and then service name.</p></div>
                        {data.topServices.length === 0 ? <p className="border-t border-slate-100 px-6 py-12 text-center text-sm text-slate-500">No requested-service data for this period.</p> : <div className="overflow-x-auto"><table className="w-full min-w-[820px] text-left"><thead><tr className="border-y border-slate-100 bg-slate-50/60 text-xs font-semibold uppercase tracking-wider text-slate-400"><th className="px-6 py-4">Service</th><th className="px-6 py-4">Leads</th><th className="px-6 py-4">Successfully qualified</th><th className="px-6 py-4">Qualification rate</th><th className="px-6 py-4">Pipeline value</th><th className="px-6 py-4">Average AI score</th></tr></thead><tbody className="divide-y divide-slate-100">{data.topServices.map((service) => <tr key={service.service}><td className="max-w-xs px-6 py-4 text-sm font-semibold text-slate-800"><span className="block truncate" title={service.service}>{service.service}</span></td><td className="px-6 py-4 text-sm tabular-nums text-slate-600">{service.leadCount.toLocaleString()}</td><td className="px-6 py-4 text-sm tabular-nums text-slate-600">{service.qualifiedLeads.toLocaleString()}</td><td className="px-6 py-4 text-sm tabular-nums text-slate-600">{service.qualificationRate.toFixed(1)}%</td><td className="px-6 py-4 text-sm tabular-nums text-slate-600">{formatCurrency(service.pipelineValue)}</td><td className="px-6 py-4 text-sm tabular-nums text-slate-600">{service.averageAiScore.toFixed(1)}</td></tr>)}</tbody></table></div>}
                    </article>
                </>
            )}
        </main>
    )
}
