import axios from 'axios'
import { z } from 'zod'
import type { PublicLeadRequest } from '../types/publicInquiry'

const budgetPattern = /^\d{1,10}(?:\.\d{1,2})?$/
const datePattern = /^(\d{4})-(\d{2})-(\d{2})$/

export function getLocalDateString(date = new Date()): string {
    const year = date.getFullYear()
    const month = String(date.getMonth() + 1).padStart(2, '0')
    const day = String(date.getDate()).padStart(2, '0')
    return `${year}-${month}-${day}`
}

function isValidLocalDate(value: string): boolean {
    const match = datePattern.exec(value)
    if (!match) return false
    const year = Number(match[1])
    const month = Number(match[2])
    const day = Number(match[3])
    const date = new Date(year, month - 1, day)
    return date.getFullYear() === year
        && date.getMonth() === month - 1
        && date.getDate() === day
}

const optionalText = (maximum: number, message: string) => z.string().trim().max(maximum, message)

export const publicInquiryFormSchema = z.object({
    fullName: z.string().trim()
        .min(1, 'Enter your full name.')
        .max(100, 'Full name must contain 100 characters or fewer.'),
    email: z.string().trim()
        .min(1, 'Enter an email address.')
        .max(180, 'Email must contain 180 characters or fewer.')
        .email('Enter a valid email address.'),
    phone: optionalText(30, 'Phone number must contain 30 characters or fewer.'),
    company: optionalText(140, 'Company must contain 140 characters or fewer.'),
    serviceId: z.string().trim()
        .min(1, 'Choose an available service.')
        .uuid('Choose an available service.'),
    estimatedBudget: z.string().trim().refine(
        (value) => value === '' || budgetPattern.test(value),
        'Enter a non-negative budget with up to 10 integer digits and 2 decimal places.',
    ),
    desiredStartDate: z.string().trim()
        .refine((value) => value === '' || isValidLocalDate(value), 'Enter a valid date.')
        .refine(
            (value) => value === '' || !isValidLocalDate(value) || value >= getLocalDateString(),
            'Choose today or a future date.',
        ),
    message: z.string().trim()
        .min(20, 'Message must contain at least 20 characters.')
        .max(3000, 'Message must contain 3000 characters or fewer.'),
    website: optionalText(200, 'Submission could not be accepted.'),
})

export type PublicInquiryFormValues = z.input<typeof publicInquiryFormSchema>
export type ValidatedPublicInquiryForm = z.output<typeof publicInquiryFormSchema>
export type PublicInquiryFormField = keyof PublicInquiryFormValues
export type PublicInquiryFieldErrors = Partial<Record<PublicInquiryFormField, string>>

export interface NormalizedPublicInquiryError {
    formError: string | null
    fieldErrors: PublicInquiryFieldErrors
}

export function buildPublicLeadRequest(values: ValidatedPublicInquiryForm): PublicLeadRequest {
    const phone = values.phone.trim()
    const company = values.company.trim()
    const budget = values.estimatedBudget.trim()
    const desiredStartDate = values.desiredStartDate.trim()

    return {
        fullName: values.fullName.trim(),
        email: values.email.trim().toLowerCase(),
        phone: phone || null,
        company: company || null,
        serviceId: values.serviceId,
        estimatedBudget: budget ? Number(budget) : null,
        desiredStartDate: desiredStartDate || null,
        message: values.message.trim(),
        website: values.website.trim(),
    }
}

const safeFieldMessages: Record<PublicInquiryFormField, string> = {
    fullName: 'Enter a full name of 100 characters or fewer.',
    email: 'Enter a valid email address of 180 characters or fewer.',
    phone: 'Phone number must contain 30 characters or fewer.',
    company: 'Company must contain 140 characters or fewer.',
    serviceId: 'Choose an available service.',
    estimatedBudget: 'Enter a non-negative budget with up to 10 integer digits and 2 decimal places.',
    desiredStartDate: 'Choose today or a future date.',
    message: 'Enter a message containing between 20 and 3000 characters.',
    website: 'Submission could not be accepted.',
}

function readStatusAndDetails(error: unknown): { status?: number; details: unknown[] } {
    if (!axios.isAxiosError(error) || !error.response) return { details: [] }
    const data: unknown = error.response.data
    if (typeof data !== 'object' || data === null) {
        return { status: error.response.status, details: [] }
    }
    const details = (data as Record<string, unknown>).details
    return {
        status: error.response.status,
        details: Array.isArray(details) ? details : [],
    }
}

function fieldErrorsFrom(details: unknown[]): PublicInquiryFieldErrors {
    const fieldErrors: PublicInquiryFieldErrors = {}
    for (const detail of details) {
        if (typeof detail !== 'string') continue
        const separator = detail.indexOf(':')
        if (separator < 1) continue
        const field = detail.slice(0, separator).trim()
        if (Object.hasOwn(safeFieldMessages, field)) {
            const publicField = field as PublicInquiryFormField
            fieldErrors[publicField] = safeFieldMessages[publicField]
        }
    }
    return fieldErrors
}

export function normalizePublicInquiryError(error: unknown): NormalizedPublicInquiryError {
    if (axios.isAxiosError(error) && !error.response) {
        return {
            formError: 'We couldn’t connect. Check your connection and try again.',
            fieldErrors: {},
        }
    }

    const { status, details } = readStatusAndDetails(error)
    if (status === 400) {
        const fieldErrors = fieldErrorsFrom(details)
        return {
            formError: Object.keys(fieldErrors).length === 0
                ? 'Some details were not accepted. Review the form and try again.'
                : null,
            fieldErrors,
        }
    }
    if (status === 403) {
        return { formError: 'Your form session expired. Refresh the page and try again.', fieldErrors: {} }
    }
    if (status === 413) {
        return { formError: 'The submission is too large. Shorten your message and try again.', fieldErrors: {} }
    }
    if (status === 415) {
        return { formError: 'The submission format was not accepted. Refresh and try again.', fieldErrors: {} }
    }
    if (status === 429) {
        return { formError: 'Too many submissions. Please wait and try again.', fieldErrors: {} }
    }
    if (status !== undefined && status >= 500) {
        return { formError: 'The service is temporarily unavailable. Please try again.', fieldErrors: {} }
    }
    return { formError: 'The inquiry could not be sent. Please try again.', fieldErrors: {} }
}
