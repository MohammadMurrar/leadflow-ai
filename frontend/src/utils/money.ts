import type { SupportedCurrency } from '../types/currency'

const formatters = new Map<SupportedCurrency, Intl.NumberFormat>()

export function formatMoney(value: number | null | undefined, currency: SupportedCurrency, empty = '—') {
    if (value === null || value === undefined || !Number.isFinite(value)) return empty
    let formatter = formatters.get(currency)
    if (!formatter) {
        formatter = new Intl.NumberFormat('en-US', { style: 'currency', currency })
        formatters.set(currency, formatter)
    }
    return formatter.format(value)
}
