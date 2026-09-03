export interface PasswordPolicyResult {
  length: boolean
  uppercase: boolean
  lowercase: boolean
  digit: boolean
  symbol: boolean
  surroundingWhitespace: boolean
  controls: boolean
}

export function evaluatePassword(password: string): PasswordPolicyResult {
  const codeUnits = Array.from({ length: password.length }, (_, index) =>
    String.fromCharCode(password.charCodeAt(index)))
  const isLetter = (value: string) => /\p{L}/u.test(value)
  const isNumber = (value: string) => /\p{N}/u.test(value)
  const hasSurroundingTrimCharacter = password.length > 0 && (
    password.charCodeAt(0) <= 0x20 || password.charCodeAt(password.length - 1) <= 0x20
  )
  return {
    length: password.length >= 12 && password.length <= 128,
    uppercase: codeUnits.some((value) => /\p{Lu}/u.test(value)),
    lowercase: codeUnits.some((value) => /\p{Ll}/u.test(value)),
    digit: codeUnits.some((value) => /\p{Nd}/u.test(value)),
    symbol: codeUnits.some((value) => !isLetter(value) && !isNumber(value)),
    surroundingWhitespace: !hasSurroundingTrimCharacter,
    controls: !/[\p{Cc}]/u.test(password),
  }
}

export function passwordIsValid(password: string): boolean {
  return Object.values(evaluatePassword(password)).every(Boolean)
}
