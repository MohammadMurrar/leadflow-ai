import type { InterfacePreferences } from '../types/settings'

export const INTERFACE_PREFERENCES_KEY = 'leadflow.interface-preferences'

export const DEFAULT_INTERFACE_PREFERENCES: InterfacePreferences = {
    schemaVersion: 1,
    defaultAnalyticsRange: '30',
    defaultLeadPageSize: 10,
    defaultLeadSort: 'createdAt,desc',
    defaultQualificationState: '',
}

function isRecord(value: unknown): value is Record<string, unknown> {
    return typeof value === 'object' && value !== null
}

export function isInterfacePreferences(value: unknown): value is InterfacePreferences {
    if (!isRecord(value) || value.schemaVersion !== 1) return false
    return ['7', '30', '90', 'all'].includes(String(value.defaultAnalyticsRange))
        && [10, 20, 50].includes(Number(value.defaultLeadPageSize))
        && ['createdAt,desc', 'createdAt,asc', 'qualificationScore,desc', 'estimatedBudget,desc']
            .includes(String(value.defaultLeadSort))
        && ['', 'PROCESSING', 'SUCCESSFULLY_QUALIFIED', 'FAILED']
            .includes(String(value.defaultQualificationState))
}

export function readInterfacePreferences(): InterfacePreferences {
    try {
        const stored = window.localStorage.getItem(INTERFACE_PREFERENCES_KEY)
        if (!stored) return DEFAULT_INTERFACE_PREFERENCES
        const parsed: unknown = JSON.parse(stored)
        return isInterfacePreferences(parsed) ? parsed : DEFAULT_INTERFACE_PREFERENCES
    } catch {
        return DEFAULT_INTERFACE_PREFERENCES
    }
}

export function saveInterfacePreferences(preferences: InterfacePreferences): boolean {
    try {
        window.localStorage.setItem(INTERFACE_PREFERENCES_KEY, JSON.stringify(preferences))
        return true
    } catch {
        return false
    }
}

export function resetInterfacePreferences(): InterfacePreferences {
    try {
        window.localStorage.removeItem(INTERFACE_PREFERENCES_KEY)
    } catch {
        // The defaults are still usable when browser storage is unavailable.
    }
    return DEFAULT_INTERFACE_PREFERENCES
}
