import { describe, expect, it } from 'vitest'
import { evaluatePassword, passwordIsValid } from './passwordPolicy'

const validLength = (length: number) => `Aa1!${'x'.repeat(length - 4)}`

describe('password policy', () => {
  it('uses UTF-16 code-unit boundaries from 12 through 128', () => {
    expect(passwordIsValid(validLength(11))).toBe(false)
    expect(passwordIsValid(validLength(12))).toBe(true)
    expect(passwordIsValid(validLength(128))).toBe(true)
    expect(passwordIsValid(validLength(129))).toBe(false)
    expect(passwordIsValid(`Aa1!${'x'.repeat(6)}😀`)).toBe(true)
    expect(passwordIsValid(`Aa1!${'x'.repeat(122)}😀`)).toBe(true)
    expect(passwordIsValid(`Aa1!${'x'.repeat(123)}😀`)).toBe(false)
  })

  it('mirrors every frontend-visible character rule', () => {
    expect(evaluatePassword('Aa1! internal space')).toEqual({ length: true, uppercase: true, lowercase: true, digit: true, symbol: true, surroundingWhitespace: true, controls: true })
    for (const value of [` ${validLength(12)}`, `${validLength(12)} `, 'Aa1!xxxxxxx\t', 'Aa1!xxxxxxx\r', 'Aa1!xxxxxxx\n', 'Aa1!xxxxxxx\0', 'Aa1!xxxxxxx\u001f']) expect(passwordIsValid(value)).toBe(false)
    expect(evaluatePassword('abcdefghijkl!1').uppercase).toBe(false)
    expect(evaluatePassword('ABCDEFGHIJK!1').lowercase).toBe(false)
    expect(evaluatePassword('Abcdefghijk!').digit).toBe(false)
    expect(evaluatePassword('Abcdefghijk1').symbol).toBe(false)
  })

  it('documents Java trim and Unicode category parity', () => {
    expect(passwordIsValid(`\u00a0${validLength(12)}`)).toBe(true)
    expect(passwordIsValid(`${validLength(12)}\u2003`)).toBe(true)
    expect(passwordIsValid('Ää١!xxxxxxxx')).toBe(true)
    expect(evaluatePassword('Äabcdefghij!1').uppercase).toBe(true)
    expect(evaluatePassword('ÄäABCDEFGHI!1').lowercase).toBe(true)
    expect(evaluatePassword('Ää١!xxxxxxxx').digit).toBe(true)
  })
})
