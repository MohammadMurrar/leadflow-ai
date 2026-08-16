import api from './leadApi'
import type { AutomationStatus, UpdateWorkspaceSettingsRequest, WorkspaceSettings } from '../types/settings'

export async function getWorkspaceSettings(): Promise<WorkspaceSettings> {
    const response = await api.get<WorkspaceSettings>('/settings/workspace')
    return response.data
}

export async function updateWorkspaceSettings(
    request: UpdateWorkspaceSettingsRequest,
): Promise<WorkspaceSettings> {
    const response = await api.put<WorkspaceSettings>('/settings/workspace', request)
    return response.data
}

export async function getAutomationStatus(): Promise<AutomationStatus> {
    const response = await api.get<AutomationStatus>('/settings/automation-status')
    return response.data
}
