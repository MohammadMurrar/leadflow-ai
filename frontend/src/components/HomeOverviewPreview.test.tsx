import { act, render, screen, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import HomeOverviewPreview from './HomeOverviewPreview'

let reduceMotion = false

beforeEach(() => {
  reduceMotion = false
  vi.useFakeTimers()
  vi.stubGlobal('matchMedia', vi.fn(() => ({ matches: reduceMotion, addEventListener: vi.fn(), removeEventListener: vi.fn() })))
})

afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

describe('illustrative overview story', () => {
  it('uses the approved seven-day values and reaches a stable completed state once', () => {
    render(<HomeOverviewPreview />)
    const preview = screen.getByRole('region', { name: 'Illustrative Murravo overview' })
    expect(preview).toHaveAttribute('data-demo-step', '0')
    expect(screen.getByText('Illustrative workspace data')).toBeVisible()
    const totals = within(screen.getByLabelText('Seven-day lead totals'))
    expect(totals.getByText('17')).toBeVisible()
    expect(totals.getByText('10')).toBeVisible()

    act(() => vi.advanceTimersByTime(280))
    expect(preview).toHaveAttribute('data-demo-step', '1')
    expect(screen.getByText('Emily Carter')).toBeVisible()
    expect(screen.getByText('Cedar Ridge Studio · Product strategy')).toBeVisible()
    expect(preview.querySelector('.home-overview-lead-heading strong')).toHaveTextContent(/^Emily Carter$/)
    expect(screen.getByText('New', { selector: '.home-overview-lead-heading span' })).toBeVisible()

    act(() => vi.advanceTimersByTime(340))
    expect(preview).toHaveAttribute('data-demo-step', '2')
    expect(screen.getByText('Qualified', { selector: '.home-overview-lead-heading span' })).toBeVisible()
    expect(screen.getByText('AI score 82/100')).toBeVisible()

    act(() => vi.advanceTimersByTime(240))
    expect(totals.getByText('18')).toBeVisible()
    expect(totals.getByText('11')).toBeVisible()

    act(() => vi.advanceTimersByTime(220))
    expect(preview).toHaveAttribute('data-demo-step', '4')
    expect(preview.querySelector('.home-chart-series')).toHaveClass('is-visible')
    const rows = within(screen.getByRole('table', { name: 'Illustrative daily lead performance' })).getAllByRole('row')
    expect(rows.slice(1).map((row) => [within(row).getByRole('rowheader').textContent, ...within(row).getAllByRole('cell').map((cell) => Number(cell.textContent))])).toEqual([
      ['Mon', 1, 0], ['Tue', 3, 1], ['Wed', 2, 1], ['Thu', 4, 2], ['Fri', 2, 1], ['Sat', 3, 3], ['Sun', 3, 3],
    ])
    expect(screen.getByText('Review and respond')).toBeVisible()
    act(() => vi.advanceTimersByTime(20_000))
    expect(preview).toHaveAttribute('data-demo-step', '4')
  })

  it('renders the final totals, status, and chart immediately under reduced motion', () => {
    reduceMotion = true
    render(<HomeOverviewPreview />)
    const preview = screen.getByRole('region', { name: 'Illustrative Murravo overview' })
    expect(preview).toHaveAttribute('data-demo-step', '4')
    expect(screen.getByText('Qualified', { selector: '.home-overview-lead-heading span' })).toBeVisible()
    expect(within(screen.getByLabelText('Seven-day lead totals')).getByText('18')).toBeVisible()
    expect(within(screen.getByLabelText('Seven-day lead totals')).getByText('11')).toBeVisible()
    expect(preview.querySelector('.home-chart-series')).toHaveClass('is-visible')
    act(() => vi.advanceTimersByTime(20_000))
    expect(preview).toHaveAttribute('data-demo-step', '4')
  })
})
