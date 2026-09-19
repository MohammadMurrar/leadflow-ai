import { Eye, EyeOff, LockKeyhole, Mail } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router-dom'
import type { LoginRequest } from '../auth/auth'
import AuthLayout from './AuthLayout'

interface LoginPageProps { onSubmit: (request: LoginRequest) => Promise<void>; isPending: boolean; error: string | null }

export default function LoginPage({ onSubmit, isPending, error }: LoginPageProps) {
  const [email, setEmail] = useState(''); const [password, setPassword] = useState(''); const [showPassword, setShowPassword] = useState(false)
  const submitting = useRef(false); const emailRef = useRef<HTMLInputElement>(null); const alertRef = useRef<HTMLParagraphElement>(null)
  useEffect(() => { emailRef.current?.focus() }, [])
  useEffect(() => { if (error) alertRef.current?.focus() }, [error])
  async function handleSubmit(event: FormEvent<HTMLFormElement>) { event.preventDefault(); if (submitting.current || isPending) return; submitting.current = true; const submittedPassword = password; setPassword(''); try { await onSubmit({ email, password: submittedPassword }) } finally { submitting.current = false } }
  return <AuthLayout><section className="auth-card" aria-labelledby="login-heading"><p className="auth-eyebrow">Administrator access</p><h1 id="login-heading" className="auth-heading">Welcome back</h1><p className="auth-copy">Sign in to manage inquiries and keep your pipeline moving.</p><form className="mt-8 space-y-5" onSubmit={handleSubmit}><div><label htmlFor="login-email" className="auth-label">Email address</label><span className="auth-input-wrap"><Mail className="auth-input-icon" /><input ref={emailRef} id="login-email" name="email" type="email" autoComplete="username" required maxLength={254} value={email} onChange={(event) => setEmail(event.target.value)} disabled={isPending} placeholder="name@company.com" aria-describedby="login-email-hint" className="auth-input" /></span><span id="login-email-hint" className="auth-hint">Use the email address assigned to your administrator account.</span></div><div><div className="flex items-center justify-between"><label htmlFor="login-password" className="auth-label !mb-0">Password</label><Link to="/forgot-password" className="auth-link text-sm">Forgot password?</Link></div><span className="auth-input-wrap mt-2"><LockKeyhole className="auth-input-icon" /><input id="login-password" name="password" type={showPassword ? 'text' : 'password'} autoComplete="current-password" required maxLength={256} value={password} onChange={(event) => setPassword(event.target.value)} disabled={isPending} placeholder="Your password" aria-describedby="login-password-hint" className="auth-input pr-12" /><button type="button" className="auth-visibility" onClick={() => setShowPassword((current) => !current)} aria-label={showPassword ? 'Hide password' : 'Show password'}>{showPassword ? <EyeOff /> : <Eye />}</button></span><span id="login-password-hint" className="auth-hint">Enter the password for this administrator account.</span></div>{error && <p ref={alertRef} tabIndex={-1} className="auth-alert" role="alert">{error}</p>}<button type="submit" disabled={isPending} className="auth-submit">{isPending ? 'Signing in…' : 'Sign in securely'}</button></form></section></AuthLayout>
}
