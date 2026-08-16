import axios from 'axios'

export interface LeadFormValues {
    fullName: string
    email: string
    phone: string
    company: string
    serviceMode: 'catalog' | 'custom'
    serviceId: string
    requestedService: string
    estimatedBudget: string
    desiredStartDate: string
    message: string
}

export type LeadFormField = keyof LeadFormValues
export type LeadFormErrors = Partial<Record<LeadFormField, string>>

interface ApiErrorResponse {
    status?: number
    message?: string
    details?: unknown[]
}

export interface NormalizedLeadError {
    formError: string | null
    fieldErrors: LeadFormErrors
}

const fieldNames = new Set<LeadFormField>([
    'fullName',
    'email',
    'phone',
    'company',
    'serviceId',
    'requestedService',
    'estimatedBudget',
    'desiredStartDate',
    'message',
])

const backendFieldMessages: Partial<Record<LeadFormField, string>> = {
    fullName: 'Enter a full name of 100 characters or fewer.',
    email: 'Enter a valid email address of 180 characters or fewer.',
    phone: 'Phone number must contain 30 characters or fewer.',
    company: 'Company must contain 140 characters or fewer.',
    serviceId: 'Choose an active catalog service.',
    requestedService: 'Enter a requested service of 120 characters or fewer.',
    estimatedBudget: 'Enter a non-negative budget with up to 10 digits and 2 decimal places.',
    desiredStartDate: 'Choose today or a future date.',
    message: 'Message must contain between 20 and 3000 characters.',
}

export function getLocalDateString(date = new Date()) {
    const year = date.getFullYear()
    const month = String(date.getMonth() + 1).padStart(2, '0')
    const day = String(date.getDate()).padStart(2, '0')
    return `${year}-${month}-${day}`
}

export function validateLeadForm(values: LeadFormValues): LeadFormErrors {
    const errors: LeadFormErrors = {}
    const fullName = values.fullName.trim()
    const email = values.email.trim()
    const requestedService = values.requestedService.trim()
    const message = values.message.trim()

    if (!fullName) errors.fullName = 'Enter the lead’s full name.'
    else if (fullName.length > 100) errors.fullName = 'Full name must contain 100 characters or fewer.'

    if (!email) errors.email = 'Enter an email address.'
    else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) errors.email = 'Enter a valid email address.'
    else if (email.length > 180) errors.email = 'Email must contain 180 characters or fewer.'

    if (values.phone.length > 30) errors.phone = 'Phone number must contain 30 characters or fewer.'
    if (values.company.length > 140) errors.company = 'Company must contain 140 characters or fewer.'

    if (values.serviceMode === 'catalog') {
        if (!values.serviceId) errors.serviceId = 'Choose an active catalog service.'
    } else if (!requestedService) errors.requestedService = 'Enter the requested service.'
    else if (requestedService.length > 120) errors.requestedService = 'Requested service must contain 120 characters or fewer.'

    const budget = values.estimatedBudget.trim()
    if (budget) {
        if (!/^\d+(\.\d{1,2})?$/.test(budget)
            || Number(budget) > 9_999_999_999.99) {
            errors.estimatedBudget = 'Enter a non-negative budget with up to 10 digits and 2 decimal places.'
        }
    }

    if (values.desiredStartDate && values.desiredStartDate < getLocalDateString()) {
        errors.desiredStartDate = 'Choose today or a future date.'
    }

    if (!message) errors.message = 'Enter a message describing what the lead needs.'
    else if (message.length < 20) errors.message = 'Message must contain at least 20 characters.'
    else if (message.length > 3000) errors.message = 'Message must contain 3000 characters or fewer.'

    return errors
}

export function normalizeLeadCreationError(error: unknown): NormalizedLeadError {
    if (!axios.isAxiosError<ApiErrorResponse>(error)) {
        return { formError: 'Could not create the lead. Please try again.', fieldErrors: {} }
    }

    if (error.code === 'ECONNABORTED' || error.code === 'ETIMEDOUT') {
        return { formError: 'The request took too long. Please try again.', fieldErrors: {} }
    }

    if (!error.response) {
        return {
            formError: 'We couldn’t connect to the server. Check your connection and try again.',
            fieldErrors: {},
        }
    }

    if (error.response.status === 409) {
        return {
            formError: 'A lead with this email was submitted recently. Please wait before submitting it again.',
            fieldErrors: {},
        }
    }

    if (error.response.status === 400) {
        const fieldErrors: LeadFormErrors = {}
        let hasUnknownField = false
        for (const detail of error.response.data?.details ?? []) {
            if (typeof detail !== 'string') {
                hasUnknownField = true
                continue
            }
            const separator = detail.indexOf(':')
            const field = separator < 0 ? '' : detail.slice(0, separator).trim()
            if (fieldNames.has(field as LeadFormField)) {
                const typedField = field as LeadFormField
                fieldErrors[typedField] = backendFieldMessages[typedField]
            } else {
                hasUnknownField = true
            }
        }
        return {
            formError: hasUnknownField || Object.keys(fieldErrors).length === 0
                ? 'Some lead details were not accepted. Review the form and try again.'
                : null,
            fieldErrors,
        }
    }

    if (error.response.status >= 500) {
        return {
            formError: 'Something went wrong while creating the lead. Please try again.',
            fieldErrors: {},
        }
    }

    return { formError: 'Could not create the lead. Please try again.', fieldErrors: {} }
}
