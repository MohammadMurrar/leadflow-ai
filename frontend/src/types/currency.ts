export const SUPPORTED_CURRENCIES = [
    { code: 'USD', name: 'US Dollar' },
    { code: 'EUR', name: 'Euro' },
    { code: 'ILS', name: 'Israeli New Shekel' },
    { code: 'JOD', name: 'Jordanian Dinar' },
    { code: 'SAR', name: 'Saudi Riyal' },
    { code: 'AED', name: 'UAE Dirham' },
    { code: 'GBP', name: 'British Pound' },
    { code: 'KWD', name: 'Kuwaiti Dinar' },
    { code: 'QAR', name: 'Qatari Riyal' },
    { code: 'EGP', name: 'Egyptian Pound' },
] as const

export type SupportedCurrency = typeof SUPPORTED_CURRENCIES[number]['code']

export function isSupportedCurrency(value: string): value is SupportedCurrency {
    return SUPPORTED_CURRENCIES.some(({ code }) => code === value)
}
