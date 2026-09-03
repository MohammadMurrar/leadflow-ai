import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import SettingsPage from './SettingsPage'

const updateWorkspaceSettings = vi.fn()

vi.mock('../services/settingsApi', () => ({
    getWorkspaceSettings: vi.fn(async () => ({
        version: 0, workspaceName: 'Workspace A', contactEmail: null, description: null,
        publicBrandName: null, publicTagline: null, publicLogoPath: null, timeZone: 'UTC',
        currency: 'USD', responseTimeText: 'One business day', privacyPolicyUrl: null,
        privacyNoticeText: null, privacyNoticeVersion: null, notificationRecipients: [],
        updatedAt: '2026-08-31T00:00:00Z',
    })),
    updateWorkspaceSettings: (...args: unknown[]) => updateWorkspaceSettings(...args),
    getAutomationStatus: vi.fn(async () => ({ dispatcherEnabled: false,
        legacyCallbackEnabled: false, retryEnabled: false, attemptTrackingAvailable: true })),
}))

const preferences = {
    schemaVersion: 1 as const, defaultAnalyticsRange: '30' as const,
    defaultLeadPageSize: 20 as const, defaultLeadSort: 'createdAt,desc' as const,
    defaultQualificationState: '' as const,
}

describe('workspace currency settings', () => {
    beforeEach(() => updateWorkspaceSettings.mockReset())

    it('offers exactly the approved currencies and explains display-only behavior', async () => {
        render(<QueryClientProvider client={new QueryClient()}><SettingsPage
            preferences={preferences} onPreferencesSaved={vi.fn()} /></QueryClientProvider>)

        const selector = await screen.findByRole('combobox', { name: /currency/i })
        expect(selector).toHaveValue('USD')
        expect(Array.from((selector as HTMLSelectElement).options).map(({ value }) => value))
            .toEqual(['USD', 'EUR', 'ILS', 'JOD', 'SAR', 'AED', 'GBP', 'KWD', 'QAR', 'EGP'])
        expect(screen.getByText('This changes how monetary values are displayed. Existing amounts are not converted. No live exchange-rate conversion occurs; stored amounts currently support up to two decimal places.'))
            .toBeInTheDocument()
        await userEvent.selectOptions(selector, 'ILS')
        expect(selector).toHaveValue('ILS')
    })
})
