import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ServicesPage from './ServicesPage'
import { deactivateService, getServices, reactivateService } from '../services/serviceApi'
import type { ServiceOffering } from '../types/service'

vi.mock('../services/serviceApi', () => ({
  getServices: vi.fn(), createService: vi.fn(), updateService: vi.fn(),
  deactivateService: vi.fn(), reactivateService: vi.fn(),
}))

const active: ServiceOffering = { id: 'active', version: 3, name: 'Consulting', description: null, active: true, createdAt: '2026-09-01T00:00:00Z', updatedAt: '2026-09-01T00:00:00Z' }
const inactive: ServiceOffering = { ...active, id: 'inactive', version: 7, name: 'Research', active: false }

function pending() {
  let resolve!: (value: ServiceOffering) => void
  const promise = new Promise<ServiceOffering>((done) => { resolve = done })
  return { promise, resolve }
}

function renderServices() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={client}><ServicesPage /></QueryClientProvider>)
}

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(getServices).mockResolvedValue({ content: [active, inactive], totalElements: 2, totalPages: 1, size: 10, number: 0, first: true, last: true, empty: false, numberOfElements: 2 })
})

describe('service activation controls', () => {
  it('offers labeled distinct actions and retains the confirmation callback', async () => {
    const request = pending()
    vi.mocked(deactivateService).mockReturnValue(request.promise)
    const user = userEvent.setup()
    renderServices()
    const mobile = (await screen.findByRole('article', { name: 'Service Consulting' })).parentElement!
    const deactivate = await within(mobile).findByRole('button', { name: 'Deactivate' })
    const reactivate = within(mobile).getByRole('button', { name: 'Reactivate' })
    expect(mobile).toHaveClass('md:hidden')
    expect(screen.getByRole('table').parentElement).toHaveClass('hidden', 'md:block')
    expect(within(mobile).getByRole('article', { name: 'Service Consulting' })).toHaveTextContent('Active')
    expect(within(mobile).getByRole('button', { name: 'Edit Consulting' })).toHaveTextContent('Edit')
    expect(deactivate).toHaveClass('service-action--danger')
    expect(reactivate).toHaveClass('service-action--success')
    expect(document.querySelector('.service-status--active')).toHaveTextContent('Active')
    expect(document.querySelector('.service-status--inactive')).toHaveTextContent('Inactive')
    await user.click(deactivate)
    expect(deactivateService).not.toHaveBeenCalled()
    await user.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Deactivate' }))
    await waitFor(() => expect(deactivateService).toHaveBeenCalledTimes(1))
    expect(deactivateService).toHaveBeenCalledWith('active', 3)
    expect(screen.getByRole('button', { name: 'Deactivating...' })).toBeDisabled()
    request.resolve({ ...active, active: false, version: 4 })
  })

  it('reactivates the inactive service once and disables the action while pending', async () => {
    const request = pending()
    vi.mocked(reactivateService).mockReturnValue(request.promise)
    const user = userEvent.setup()
    renderServices()
    const mobile = (await screen.findByRole('article', { name: 'Service Research' })).parentElement!
    const button = await within(mobile).findByRole('button', { name: 'Reactivate' })
    expect(within(mobile).getByRole('article', { name: 'Service Research' })).toHaveTextContent('Inactive')
    await user.click(button)
    expect(reactivateService).toHaveBeenCalledTimes(1)
    expect(reactivateService).toHaveBeenCalledWith('inactive', 7)
    expect(button).toBeDisabled()
    request.resolve({ ...inactive, active: true, version: 8 })
  })

  it('retains the desktop service table and its action control', async () => {
    renderServices()
    const table = await screen.findByRole('table')
    expect(within(table).getByText('Consulting')).toBeInTheDocument()
    expect(within(table).getByRole('button', { name: 'Edit Consulting' })).toBeInTheDocument()
    expect(within(table).getByRole('button', { name: 'Deactivate' })).toHaveClass('service-action--danger')
    expect(within(table).getByRole('button', { name: 'Reactivate' })).toHaveClass('service-action--success')
  })
})
