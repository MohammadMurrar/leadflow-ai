import { Check, Circle, Eye, EyeOff, KeyRound } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import type { FormEvent, Ref } from 'react'
import { Link } from 'react-router-dom'
import { classifyPasswordResetError, confirmPasswordReset } from '../auth/anonymousAuthApi'
import { evaluatePassword, passwordIsValid } from '../auth/passwordPolicy'
import { captureResetToken, clearResetToken, retainResetTokenLifecycle } from '../auth/resetTokenVault'
import AuthLayout from './AuthLayout'

type Outcome = 'form' | 'success' | 'invalid'

export default function ResetPasswordPage() {
  const [initialToken] = useState(captureResetToken)
  const tokenRef = useRef<string | null>(initialToken)
  const [outcome, setOutcome] = useState<Outcome>(initialToken ? 'form' : 'invalid')
  const [password, setPassword] = useState(''); const [confirmation, setConfirmation] = useState('')
  const [showPassword, setShowPassword] = useState(false); const [showConfirmation, setShowConfirmation] = useState(false)
  const [pending, setPending] = useState(false); const [error, setError] = useState<string | null>(null)
  const [errorFocus, setErrorFocus] = useState<'password' | 'confirmation' | 'alert' | null>(null)
  const submitting = useRef(false); const passwordRef = useRef<HTMLInputElement>(null); const confirmationRef = useRef<HTMLInputElement>(null); const outcomeRef = useRef<HTMLHeadingElement>(null); const errorRef = useRef<HTMLParagraphElement>(null)
  const policy = evaluatePassword(password)
  useEffect(() => retainResetTokenLifecycle(), [])
  useEffect(() => { if (outcome === 'form') passwordRef.current?.focus(); else outcomeRef.current?.focus() }, [outcome])
  useEffect(() => {
    if (!error) return
    if (errorFocus === 'confirmation') confirmationRef.current?.focus()
    else if (errorFocus === 'alert') errorRef.current?.focus()
    else if (errorFocus === 'password') passwordRef.current?.focus()
  }, [error, errorFocus])

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (submitting.current || pending || !tokenRef.current) return
    if (!passwordIsValid(password)) { setErrorFocus('password'); setError('Use a password that satisfies every requirement.'); return }
    if (password !== confirmation) { setErrorFocus('confirmation'); setError('Passwords do not match.'); return }
    submitting.current = true; setPending(true); setError(null); setErrorFocus(null)
    try { await confirmPasswordReset(tokenRef.current, password); setPassword(''); setConfirmation(''); tokenRef.current = null; clearResetToken(); setOutcome('success') }
    catch (requestError) { const kind = classifyPasswordResetError(requestError); setPassword(''); setConfirmation(''); if (kind === 'invalid') { tokenRef.current = null; clearResetToken(); setOutcome('invalid') } else { setErrorFocus('alert'); setError(kind === 'policy' ? 'Choose a different password that meets every security requirement.' : 'Your password could not be updated. Please try again later.') } }
    finally { setPending(false); submitting.current = false }
  }

  if (outcome === 'invalid') return <AuthLayout><section className="auth-card"><p className="auth-eyebrow">Secure reset</p><h1 ref={outcomeRef} tabIndex={-1} className="auth-heading">This reset link is invalid or expired</h1><p className="auth-copy mt-3">Request a new link to continue securely.</p><Link to="/forgot-password" className="auth-submit mt-7 flex items-center justify-center">Request a new link</Link></section></AuthLayout>
  if (outcome === 'success') return <AuthLayout><section className="auth-card"><div className="mb-5 flex h-12 w-12 items-center justify-center rounded-2xl bg-emerald-100 text-emerald-700"><Check /></div><h1 ref={outcomeRef} tabIndex={-1} className="auth-heading">Password updated</h1><p className="auth-copy mt-3">Your password has been changed and existing sessions were signed out.</p><Link to="/login" className="auth-submit mt-7 flex items-center justify-center">Return to sign in</Link></section></AuthLayout>

  const rules: Array<[keyof typeof policy, string]> = [['length', '12–128 characters'], ['uppercase', 'One uppercase letter'], ['lowercase', 'One lowercase letter'], ['digit', 'One decimal digit'], ['symbol', 'One non-alphanumeric character'], ['surroundingWhitespace', 'No leading or trailing whitespace'], ['controls', 'No control characters']]
  return <AuthLayout><section className="auth-card" aria-labelledby="reset-heading"><p className="auth-eyebrow">Secure reset</p><h1 id="reset-heading" className="auth-heading">Create a new password</h1><p className="auth-copy">Choose a strong password for your administrator account.</p><form className="mt-7 space-y-5" onSubmit={submit}><PasswordField id="new-password" label="New password" value={password} setValue={setPassword} visible={showPassword} setVisible={setShowPassword} inputRef={passwordRef} describedBy={error ? 'password-requirements reset-error' : 'password-requirements'} invalid={Boolean(error)} /><ul id="password-requirements" className="grid gap-2 text-xs text-slate-600 sm:grid-cols-2" aria-label="Password requirements">{rules.map(([key, label]) => <li key={key} className="flex items-center gap-2">{policy[key] ? <Check className="h-4 w-4 text-emerald-600" aria-hidden="true" /> : <Circle className="h-4 w-4 text-slate-400" aria-hidden="true" />}<span>{label}: {policy[key] ? 'met' : 'not met'}</span></li>)}</ul><PasswordField id="confirm-password" label="Confirm new password" value={confirmation} setValue={setConfirmation} visible={showConfirmation} setVisible={setShowConfirmation} invalid={Boolean(error && password !== confirmation)} describedBy={error ? 'reset-error' : undefined} inputRef={confirmationRef} />{error && <p ref={errorRef} tabIndex={-1} id="reset-error" className="auth-alert" role="alert">{error}</p>}<button type="submit" disabled={pending} className="auth-submit">{pending ? 'Updating password…' : 'Update password'}</button></form></section></AuthLayout>
}

interface PasswordFieldProps { id: string; label: string; value: string; setValue: (value: string) => void; visible: boolean; setVisible: (value: boolean) => void; invalid: boolean; describedBy?: string; inputRef?: Ref<HTMLInputElement> }
function PasswordField({ id, label, value, setValue, visible, setVisible, invalid, describedBy, inputRef }: PasswordFieldProps) {
  return <div><label htmlFor={id} className="auth-label">{label}</label><span className="auth-input-wrap"><KeyRound className="auth-input-icon" /><input ref={inputRef} id={id} type={visible ? 'text' : 'password'} autoComplete="new-password" required maxLength={128} value={value} onChange={(event) => setValue(event.target.value)} aria-invalid={invalid} aria-describedby={describedBy} className="auth-input pr-12" /><button type="button" className="auth-visibility" onClick={() => setVisible(!visible)} aria-label={visible ? `Hide ${label.toLowerCase()}` : `Show ${label.toLowerCase()}`}>{visible ? <EyeOff /> : <Eye />}</button></span></div>
}
