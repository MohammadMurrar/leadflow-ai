import type { ReactNode } from 'react'
import type { SupportedCurrency } from '../types/currency'
import type { Lead } from '../types/lead'
import { getInitials } from '../utils/getInitials'
import { formatMoney } from '../utils/money'
import LeadRowActions from './LeadRowActions'

interface LeadMobileRecordProps {
    lead: Lead
    currency: SupportedCurrency
    context: 'overview' | 'leads' | 'qualification'
}

function statusClasses(status: Lead['status']) {
    if (status === 'QUALIFIED') return 'bg-emerald-50 text-emerald-700'
    if (status === 'AUTOMATION_FAILED') return 'bg-rose-50 text-rose-700'
    if (status === 'WON') return 'bg-primary-50 text-primary-700'
    if (status === 'LOST') return 'bg-slate-100 text-slate-600'
    return 'bg-blue-50 text-blue-700'
}

function priorityClasses(priority: Lead['priority']) {
    if (priority === 'HIGH') return 'bg-rose-50 text-rose-700'
    if (priority === 'MEDIUM') return 'bg-amber-50 text-amber-700'
    return 'bg-slate-100 text-slate-600'
}

function Field({ label, children }: { label: string; children: ReactNode }) {
    return <div className="min-w-0"><dt className="text-xs font-medium text-slate-500">{label}</dt><dd className="mt-1 break-words text-sm font-semibold text-slate-800">{children}</dd></div>
}

export default function LeadMobileRecord({ lead, currency, context }: LeadMobileRecordProps) {
    const priority = lead.priority && lead.priority !== 'UNASSESSED' ? lead.priority : null

    return <article aria-label={`Lead ${lead.fullName}`} className="min-w-0 px-4 py-4">
        <div className="flex items-start gap-3">
            <span aria-hidden="true" className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-primary-50 text-xs font-bold text-primary-700">{getInitials(lead.fullName)}</span>
            <div className="min-w-0 flex-1">
                <h3 className="break-words text-sm font-semibold text-slate-800">{lead.fullName}</h3>
                <p className="mt-0.5 break-words text-sm text-slate-500">{lead.company || lead.email}</p>
            </div>
            <LeadRowActions lead={lead} currency={currency} buttonClassName="h-11 w-11 shrink-0" />
        </div>
        <div className="mt-3 flex flex-wrap items-center gap-2">
            <span className={`inline-flex rounded-full px-2.5 py-1 text-xs font-semibold ${statusClasses(lead.status)}`}>{lead.status.replaceAll('_', ' ')}</span>
            {priority && <span className={`inline-flex rounded-full px-2.5 py-1 text-xs font-semibold ${priorityClasses(priority)}`}>{priority} priority</span>}
        </div>
        <dl className="mt-3 grid grid-cols-2 gap-x-3 gap-y-3 border-t border-slate-100 pt-3">
            <div className="col-span-2 min-w-0"><dt className="text-xs font-medium text-slate-500">Requested service</dt><dd className="mt-1 break-words text-sm font-semibold text-slate-800">{lead.requestedService}</dd></div>
            {context === 'qualification' ? <>
                <Field label="AI score">{lead.qualificationScore ?? 'Analyzing'}</Field>
                <Field label="Category">{lead.category || 'Not available'}</Field>
            </> : <>
                {context === 'leads' && <Field label="AI score">{lead.qualificationScore ?? 'Analyzing'}</Field>}
                <Field label="Estimated value">{formatMoney(lead.estimatedBudget, currency)}</Field>
            </>}
        </dl>
    </article>
}
