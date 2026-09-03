import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { Lead } from '../types/lead'
import LeadsPage from './LeadsPage'

const hostileText = '<img src=x onerror="alert(1)"><script>alert(2)</script>'

describe('untrusted lead rendering', () => {
  it('renders stored customer fields as text instead of executable markup', () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const lead: Lead = {
      id: '11111111-1111-4111-8111-111111111111',
      version: 0,
      fullName: hostileText,
      email: 'synthetic-security@example.invalid',
      phone: null,
      company: hostileText,
      requestedService: hostileText,
      estimatedBudget: 4000,
      desiredStartDate: null,
      message: hostileText,
      source: 'security-test',
      status: 'NEW',
      priority: 'UNASSESSED',
      qualificationScore: null,
      category: null,
      aiSummary: hostileText,
      recommendedReply: hostileText,
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
    }

    const { container } = render(
      <QueryClientProvider client={client}>
        <LeadsPage
          currency="USD"
          page={{ content: [lead], totalElements: 1, totalPages: 1, size: 20,
            number: 0, first: true, last: true, empty: false, numberOfElements: 1 }}
          leads={[lead]}
          searchInput=""
          status=""
          sort="createdAt,desc"
          isLoading={false}
          isFetching={false}
          isPlaceholderData={false}
          isError={false}
          onSearchChange={vi.fn()}
          onStatusChange={vi.fn()}
          onSortChange={vi.fn()}
          onPageChange={vi.fn()}
          onRetry={vi.fn()}
        />
      </QueryClientProvider>,
    )

    expect(screen.getAllByText(hostileText)).toHaveLength(3)
    expect(container.querySelector('script')).not.toBeInTheDocument()
    expect(container.querySelector('img')).not.toBeInTheDocument()
  })
})
