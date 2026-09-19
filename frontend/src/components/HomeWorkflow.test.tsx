import { StrictMode } from 'react'
import { act, render, screen, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import HomeWorkflow from './HomeWorkflow'

let reduceMotion = false
let onIntersection: IntersectionObserverCallback
let observerOptions: IntersectionObserverInit
const observe = vi.fn()
const disconnect = vi.fn()
const request = vi.fn()

beforeEach(() => {
  reduceMotion = false
  observe.mockReset()
  disconnect.mockReset()
  request.mockReset()
  vi.useFakeTimers()
  vi.stubGlobal('fetch', request)
  vi.stubGlobal('matchMedia', vi.fn(() => ({ matches: reduceMotion, addEventListener: vi.fn(), removeEventListener: vi.fn() })))
  vi.stubGlobal('IntersectionObserver', class {
    constructor(callback: IntersectionObserverCallback, options: IntersectionObserverInit) {
      onIntersection = callback
      observerOptions = options
    }
    observe = observe
    disconnect = disconnect
  })
})

afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

describe('illustrative inquiry workflow', () => {
  it('keeps readable pending rows until 40% intersection, then completes each row once', () => {
    render(<HomeWorkflow />)
    const record = screen.getByRole('article', { name: 'Illustrative inquiry activity' })
    const section = record.closest('section')!
    const rows = within(record).getAllByRole('listitem')
    expect(section).toHaveAttribute('data-workflow-step', '0')
    expect(record).toHaveAttribute('data-active', 'false')
    expect(observe).toHaveBeenCalledWith(record)
    expect(observerOptions).toEqual({ threshold: 0.4, rootMargin: '0px' })
    expect(screen.getByText('Illustrative workflow data')).toBeVisible()
    expect(within(record).getByRole('heading', { name: 'Emily Carter' })).toBeVisible()
    expect(record).toHaveTextContent('Cedar Ridge Studio · Product strategy inquiry')
    expect(record.querySelector('.home-workflow-subject h3')).toHaveTextContent(/^Emily Carter$/)
    for (const title of ['Inquiry received', 'AI-assisted qualification', 'Human review and decision', 'Follow-up and workspace update']) {
      expect(screen.getByRole('heading', { name: title })).toBeVisible()
    }
    for (const row of rows) {
      expect(row).toHaveAttribute('data-state', 'pending')
      expect(within(row).getByText('Pending')).toBeVisible()
    }
    act(() => vi.advanceTimersByTime(20_000))
    expect(section).toHaveAttribute('data-workflow-step', '0')
    act(() => onIntersection([{ isIntersecting: true, intersectionRatio: 0.39 } as IntersectionObserverEntry], {} as IntersectionObserver))
    expect(section).toHaveAttribute('data-workflow-step', '0')
    expect(disconnect).not.toHaveBeenCalled()

    act(() => onIntersection([{ isIntersecting: true, intersectionRatio: 0.4 } as IntersectionObserverEntry], {} as IntersectionObserver))
    expect(section).toHaveAttribute('data-workflow-step', '1')
    expect(record).toHaveAttribute('data-active', 'true')
    expect(disconnect).toHaveBeenCalledOnce()
    expect(rows[0]).toHaveAttribute('data-state', 'complete')
    expect(within(rows[0]).getByText('Received')).toBeVisible()
    expect(within(rows[1]).getByText('Pending')).toBeVisible()

    for (const [index, result] of ['Score 82/100', 'Review complete', 'Action recorded'].entries()) {
      act(() => vi.advanceTimersByTime(179))
      expect(rows[index + 1]).toHaveAttribute('data-state', 'pending')
      act(() => vi.advanceTimersByTime(1))
      expect(rows[index + 1]).toHaveAttribute('data-state', 'complete')
      expect(within(rows[index + 1]).getByText(result)).toBeVisible()
    }
    expect(section).toHaveAttribute('data-workflow-step', '4')
    expect(within(record).queryByText('Pending')).not.toBeInTheDocument()
    act(() => onIntersection([{ isIntersecting: false } as IntersectionObserverEntry], {} as IntersectionObserver))
    act(() => onIntersection([{ isIntersecting: true, intersectionRatio: 1 } as IntersectionObserverEntry], {} as IntersectionObserver))
    act(() => vi.advanceTimersByTime(20_000))
    expect(section).toHaveAttribute('data-workflow-step', '4')
    expect(disconnect).toHaveBeenCalledOnce()
    expect(vi.getTimerCount()).toBe(0)
    expect(request).not.toHaveBeenCalled()
  })

  it('does not complete during React development effect remounting', () => {
    render(<StrictMode><HomeWorkflow /></StrictMode>)
    const section = screen.getByRole('article', { name: 'Illustrative inquiry activity' }).closest('section')!
    act(() => vi.advanceTimersByTime(20_000))
    expect(section).toHaveAttribute('data-workflow-step', '0')
    expect(screen.getAllByText('Pending')).toHaveLength(4)
  })

  it('renders the final state immediately with reduced motion', () => {
    reduceMotion = true
    render(<HomeWorkflow />)
    const section = screen.getByRole('article', { name: 'Illustrative inquiry activity' }).closest('section')!
    expect(section).toHaveAttribute('data-workflow-step', '4')
    expect(within(section).queryByText('Pending')).not.toBeInTheDocument()
    for (const result of ['Received', 'Score 82/100', 'Review complete', 'Action recorded']) {
      expect(within(section).getByText(result)).toBeVisible()
    }
    expect(observe).not.toHaveBeenCalled()
    expect(vi.getTimerCount()).toBe(0)
    act(() => vi.advanceTimersByTime(20_000))
    expect(section).toHaveAttribute('data-workflow-step', '4')
  })

  it('shows the completed record when IntersectionObserver is unavailable', () => {
    vi.stubGlobal('IntersectionObserver', undefined)
    render(<HomeWorkflow />)
    const record = screen.getByRole('article', { name: 'Illustrative inquiry activity' })
    expect(record.closest('section')).toHaveAttribute('data-workflow-step', '4')
    expect(within(record).getByText('Action recorded')).toBeVisible()
  })
})
