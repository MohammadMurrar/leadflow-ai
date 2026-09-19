import type { ReactNode } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { Lead } from '../types/lead'
import type { PageResponse } from '../types/page'
import { getInitials } from '../utils/getInitials'
import { getLead, getQualificationAttempts } from '../services/leadApi'
import OverviewPage from './OverviewPage'
import LeadsPage from './LeadsPage'
import AiQualificationPage from './AiQualificationPage'
import AnalyticsPage from './AnalyticsPage'

vi.mock('../services/leadApi', () => ({
    getLead: vi.fn(), getQualificationAttempts: vi.fn(), updateLeadStatus: vi.fn(), retryQualification: vi.fn(),
}))

const lead: Lead = {
    id: 'lead-1', version: 1, fullName: 'Emily Carter', email: 'emily@example.test', phone: null,
    company: 'Cedar Ridge Studio', requestedService: 'Product strategy', estimatedBudget: 4800,
    desiredStartDate: null, message: null, source: null, status: 'QUALIFIED', priority: 'HIGH',
    qualificationScore: 82, category: 'Strong fit', aiSummary: null, recommendedReply: null,
    createdAt: '2026-09-01T00:00:00Z', updatedAt: '2026-09-02T00:00:00Z',
}
const secondLead: Lead = { ...lead, id: 'lead-2', fullName: 'Daniel Brooks', company: 'Northstar Operations', status: 'NEW', priority: 'UNASSESSED', qualificationScore: null, category: null }
const page: PageResponse<Lead> = { content: [lead, secondLead], totalElements: 12, totalPages: 2, size: 10, number: 0, first: true, last: false, empty: false, numberOfElements: 2 }

function renderPage(node: ReactNode) {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    return render(<QueryClientProvider client={client}><MemoryRouter>{node}</MemoryRouter></QueryClientProvider>)
}

beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(getLead).mockResolvedValue(lead)
    vi.mocked(getQualificationAttempts).mockResolvedValue([])
})

describe('mobile lead records', () => {
    it('keeps Overview identity, service, status, value, and one action path together while retaining its table', async () => {
        const user = userEvent.setup()
        const setLeadSearchInput = vi.fn()
        renderPage(<OverviewPage currency="USD" userDisplayName="Admin User" workspaceDescription="Demo" selectedRange="30" setSelectedRange={vi.fn()}
            dashboardStats={undefined} isDashboardLoading isDashboardFetching={false} isDashboardPlaceholderData={false} isDashboardError={false} refetchDashboard={vi.fn()}
            displayedLeadsPage={page} leads={page.content} leadSearchInput="" setLeadSearchInput={setLeadSearchInput} isLeadSearchActive={false}
            isLoading={false} isLeadsFetching={false} isLeadsPlaceholderData={false} isError={false} refetchLeads={vi.fn()} getInitials={getInitials} />)
        const mobile = document.querySelector('[aria-label="Recent lead records"]')!
        const record = within(mobile as HTMLElement).getByRole('article', { name: 'Lead Emily Carter' })
        expect(record).toHaveTextContent('Cedar Ridge Studio')
        expect(record).toHaveTextContent('Product strategy')
        expect(record).toHaveTextContent('QUALIFIED')
        expect(record).toHaveTextContent('$4,800.00')
        expect(screen.getByRole('link', { name: 'View all leads' })).toHaveAttribute('href', '/leads')
        expect(screen.getByRole('table')).toBeInTheDocument()
        expect(screen.getByRole('table').parentElement).toHaveClass('hidden', 'md:block')
        expect(mobile).toHaveClass('md:hidden')
        await user.type(screen.getByRole('searchbox', { name: 'Search recent leads' }), 'Emily')
        expect(setLeadSearchInput).toHaveBeenCalled()
        await user.click(within(record).getByRole('button', { name: 'Actions for Emily Carter' }))
        await user.click(screen.getByRole('menuitem', { name: 'View details' }))
        expect(await screen.findByRole('dialog', { name: 'Lead details' })).toBeInTheDocument()
        await waitFor(() => expect(getLead).toHaveBeenCalledTimes(1))
    })

    it('retains Leads search, status, sort, pagination, and mobile actions', async () => {
        const user = userEvent.setup()
        const onSearchChange = vi.fn(), onStatusChange = vi.fn(), onSortChange = vi.fn(), onPageChange = vi.fn()
        renderPage(<LeadsPage currency="USD" page={page} leads={page.content} searchInput="" status="" sort="createdAt,desc"
            isLoading={false} isFetching={false} isPlaceholderData={false} isError={false}
            onSearchChange={onSearchChange} onStatusChange={onStatusChange} onSortChange={onSortChange} onPageChange={onPageChange} onRetry={vi.fn()} />)
        const mobile = document.querySelector('[aria-label="Lead records"]')!
        expect(within(mobile as HTMLElement).getAllByRole('article')).toHaveLength(2)
        const record = within(mobile as HTMLElement).getByRole('article', { name: 'Lead Emily Carter' })
        expect(record).toHaveTextContent('Product strategy')
        expect(record).toHaveTextContent('HIGH priority')
        expect(record).toHaveTextContent('82')
        expect(record).toHaveTextContent('$4,800.00')
        expect(within(record).getByRole('button', { name: 'Actions for Emily Carter' })).toHaveClass('h-11', 'w-11')
        expect(screen.getByRole('table').parentElement).toHaveClass('hidden', 'md:block')
        await user.type(screen.getByRole('searchbox', { name: 'Search leads page' }), 'Emily')
        expect(onSearchChange).toHaveBeenCalled()
        await user.selectOptions(screen.getByRole('combobox', { name: 'Filter leads by status' }), 'QUALIFIED')
        expect(onStatusChange).toHaveBeenCalledWith('QUALIFIED')
        await user.selectOptions(screen.getByRole('combobox', { name: 'Sort leads' }), 'estimatedBudget,desc')
        expect(onSortChange).toHaveBeenCalledWith('estimatedBudget,desc')
        await user.click(screen.getByRole('button', { name: 'Next' }))
        expect(onPageChange).toHaveBeenCalledOnce()
        expect(onPageChange).toHaveBeenCalledWith(1)
    })

    it('keeps qualification identity, result, category, controls, and review access together below unchanged metrics', async () => {
        const user = userEvent.setup()
        const onStateChange = vi.fn(), onSortChange = vi.fn(), onPageChange = vi.fn()
        renderPage(<AiQualificationPage currency="USD" page={page} leads={page.content} summary={undefined} searchInput="" qualificationState="" sort="createdAt,desc"
            isLoading={false} isFetching={false} isPlaceholderData={false} isError={false} isSummaryLoading isSummaryError={false}
            onSearchChange={vi.fn()} onStateChange={onStateChange} onSortChange={onSortChange} onPageChange={onPageChange} onRetry={vi.fn()} onSummaryRetry={vi.fn()} />)
        expect(screen.getByRole('region', { name: 'All-time qualification summary' })).toBeInTheDocument()
        const mobile = document.querySelector('[aria-label="Qualification records"]')!
        const record = within(mobile as HTMLElement).getByRole('article', { name: 'Lead Emily Carter' })
        expect(record).toHaveTextContent('Cedar Ridge Studio')
        expect(record).toHaveTextContent('QUALIFIED')
        expect(record).toHaveTextContent('82')
        expect(record).toHaveTextContent('Strong fit')
        expect(within(record).getByRole('button', { name: 'Actions for Emily Carter' })).toBeInTheDocument()
        expect(screen.getByRole('table').parentElement).toHaveClass('hidden', 'md:block')
        await user.selectOptions(screen.getByRole('combobox', { name: 'Filter by qualification state' }), 'FAILED')
        expect(onStateChange).toHaveBeenCalledWith('FAILED')
        await user.selectOptions(screen.getByRole('combobox', { name: 'Sort qualification queue' }), 'qualificationScore,desc')
        expect(onSortChange).toHaveBeenCalledWith('qualificationScore,desc')
        await user.click(screen.getByRole('button', { name: 'Next' }))
        expect(onPageChange).toHaveBeenCalledWith(1)
    })

    it('labels and expands the mobile Analytics range control without changing options or callback', async () => {
        const user = userEvent.setup()
        const onRangeChange = vi.fn()
        renderPage(<AnalyticsPage currency="USD" data={undefined} range="30" isLoading={false} isFetching={false} isPlaceholderData={false} isError={false} onRangeChange={onRangeChange} onRetry={vi.fn()} />)
        const select = screen.getByRole('combobox', { name: 'Reporting period' })
        expect(select).toHaveClass('h-11', 'w-full', 'sm:h-10', 'sm:w-auto')
        expect(within(select).getAllByRole('option')).toHaveLength(4)
        await user.selectOptions(select, '90')
        expect(onRangeChange).toHaveBeenCalledOnce()
        expect(onRangeChange).toHaveBeenCalledWith('90')
    })
})
