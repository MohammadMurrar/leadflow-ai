import { LockKeyhole, Mail, Sparkles } from 'lucide-react'
import { useState } from 'react'
import type { FormEvent } from 'react'
import type { LoginRequest } from '../auth/auth'

interface LoginPageProps {
    onSubmit: (request: LoginRequest) => Promise<void>
    isPending: boolean
    error: string | null
}

export default function LoginPage({ onSubmit, isPending, error }: LoginPageProps) {
    const [email, setEmail] = useState('')
    const [password, setPassword] = useState('')

    async function handleSubmit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault()
        const submittedPassword = password
        setPassword('')
        await onSubmit({ email, password: submittedPassword })
    }

    return (
        <main className="flex min-h-screen items-center justify-center bg-slate-50 px-4 py-12">
            <section className="w-full max-w-md rounded-3xl border border-slate-200 bg-white p-8 shadow-xl shadow-slate-200/60 sm:p-10" aria-labelledby="login-heading">
                <div className="mb-8 flex items-center gap-3">
                    <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-gradient-to-br from-indigo-600 to-violet-600 shadow-lg shadow-indigo-200">
                        <Sparkles className="h-5 w-5 text-white" />
                    </div>
                    <div>
                        <p className="text-lg font-bold tracking-tight text-slate-950">LeadFlow <span className="text-indigo-600">AI</span></p>
                        <p className="text-xs text-slate-400">Secure workspace</p>
                    </div>
                </div>
                <h1 id="login-heading" className="text-2xl font-bold tracking-tight text-slate-950">Sign in</h1>
                <p className="mt-2 text-sm text-slate-500">Use your administrator account to continue.</p>
                <form className="mt-8 space-y-5" onSubmit={handleSubmit}>
                    <label className="block text-sm font-medium text-slate-700">
                        Email address
                        <span className="relative mt-2 block">
                            <Mail className="absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                            <input type="email" autoComplete="username" required maxLength={254} value={email} onChange={(event) => setEmail(event.target.value)} disabled={isPending} className="h-12 w-full rounded-xl border border-slate-200 pl-10 pr-4 outline-none transition focus:border-indigo-400 focus:ring-4 focus:ring-indigo-100" />
                        </span>
                    </label>
                    <label className="block text-sm font-medium text-slate-700">
                        Password
                        <span className="relative mt-2 block">
                            <LockKeyhole className="absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                            <input type="password" autoComplete="current-password" required minLength={12} maxLength={256} value={password} onChange={(event) => setPassword(event.target.value)} disabled={isPending} className="h-12 w-full rounded-xl border border-slate-200 pl-10 pr-4 outline-none transition focus:border-indigo-400 focus:ring-4 focus:ring-indigo-100" />
                        </span>
                    </label>
                    {error && <p className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700" role="alert">{error}</p>}
                    <button type="submit" disabled={isPending} className="h-12 w-full rounded-xl bg-indigo-600 font-semibold text-white shadow-lg shadow-indigo-200 transition hover:bg-indigo-700 focus:outline-none focus:ring-4 focus:ring-indigo-200 disabled:cursor-not-allowed disabled:opacity-60">
                        {isPending ? 'Signing in…' : 'Sign in securely'}
                    </button>
                </form>
            </section>
        </main>
    )
}
