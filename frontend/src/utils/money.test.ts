import { describe, expect, it } from 'vitest'
import { formatMoney } from './money'

describe('formatMoney', () => {
    it('formats the same stored amount in every supported workspace currency without conversion', () => {
        expect(formatMoney(4000, 'USD')).toBe('$4,000.00')
        expect(formatMoney(4000, 'EUR')).toBe('€4,000.00')
        expect(formatMoney(4000, 'ILS')).toBe('₪4,000.00')
        expect(formatMoney(4000, 'JOD')).toBe('JOD 4,000.000')
        expect(formatMoney(4000, 'SAR')).toBe('SAR 4,000.00')
        expect(formatMoney(4000, 'AED')).toBe('AED 4,000.00')
        expect(formatMoney(4000, 'GBP')).toBe('£4,000.00')
        expect(formatMoney(4000, 'KWD')).toBe('KWD 4,000.000')
        expect(formatMoney(4000, 'QAR')).toBe('QAR 4,000.00')
        expect(formatMoney(4000, 'EGP')).toBe('EGP 4,000.00')
    })

    it('uses a safe placeholder for missing or non-finite values', () => {
        expect(formatMoney(null, 'USD')).toBe('—')
        expect(formatMoney(Number.NaN, 'USD', 'Not provided')).toBe('Not provided')
    })
})
