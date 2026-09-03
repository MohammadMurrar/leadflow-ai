import { Bot, Check, Inbox, Layers3, Sparkles } from 'lucide-react'
import { useQuery } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { getPublicInquiryConfiguration } from '../services/publicInquiryApi'

function safeLogoPath(path: string | null | undefined): string | null {
  if (!path || !path.startsWith('/') || path.startsWith('//')) return null
  try {
    const url = new URL(path, window.location.origin)
    return url.origin === window.location.origin ? `${url.pathname}${url.search}` : null
  } catch {
    return null
  }
}

export default function AuthLayout({ children }: { children: ReactNode }) {
  const branding = useQuery({
    queryKey: ['public', 'auth-branding'],
    queryFn: ({ signal }) => getPublicInquiryConfiguration(undefined, signal),
    retry: false,
    staleTime: 5 * 60_000,
  })
  const brandName = branding.data?.publicBrandName?.trim() || 'LeadFlow AI'
  const tagline = branding.data?.publicTagline?.trim()
    || 'A focused workspace for modern lead operations.'
  const logoPath = safeLogoPath(branding.data?.publicLogoPath)

  return (
    <main className="auth-shell">
      <section className="auth-brand-panel" aria-label={`${brandName} product overview`}>
        <div className="auth-orb auth-orb-one" aria-hidden="true" />
        <div className="auth-orb auth-orb-two" aria-hidden="true" />
        <div className="relative z-10 flex h-full flex-col">
          <div className="flex items-center gap-3">
            {logoPath
              ? <img src={logoPath} alt="" className="h-11 w-11 rounded-xl object-contain" />
              : <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-white/15"><Sparkles className="h-5 w-5" /></span>}
            <div><p className="font-semibold text-white">{brandName}</p><p className="text-xs text-indigo-200">Secure workspace</p></div>
          </div>
          <div className="my-auto max-w-xl py-12">
            <p className="text-sm font-semibold uppercase tracking-[0.2em] text-indigo-200">Lead operations, refined</p>
            <h2 className="mt-4 text-4xl font-bold leading-tight text-white lg:text-5xl">Turn every inquiry into organized momentum.</h2>
            <p className="mt-5 max-w-lg text-base leading-7 text-indigo-100">{tagline}</p>
            <ul className="mt-8 space-y-4">
              {[[Inbox, 'Capture every inquiry'], [Bot, 'Qualify leads with AI'], [Layers3, 'Keep your sales pipeline organized']].map(([Icon, label]) => (
                <li key={label as string} className="flex items-center gap-3 text-sm font-medium text-white"><span className="flex h-9 w-9 items-center justify-center rounded-lg bg-white/10"><Icon className="h-4 w-4" /></span>{label as string}<Check className="ml-auto h-4 w-4 text-violet-200" /></li>
              ))}
            </ul>
            <div className="auth-preview mt-10" aria-hidden="true">
              <div className="flex items-center justify-between"><span className="h-2 w-24 rounded bg-indigo-200/50" /><span className="h-7 w-16 rounded-lg bg-violet-400/40" /></div>
              <div className="mt-5 grid grid-cols-3 gap-3">{[68, 48, 82].map((width) => <div key={width} className="rounded-xl bg-white/10 p-3"><span className="block h-2 rounded bg-white/30" style={{ width: `${width}%` }} /><span className="mt-3 block h-8 rounded-lg bg-indigo-300/15" /></div>)}</div>
              <div className="mt-4 h-2 rounded bg-white/10"><span className="block h-full w-2/3 rounded bg-violet-300/60" /></div>
            </div>
          </div>
        </div>
      </section>
      <section className="auth-form-panel"><div className="auth-mobile-brand"><Sparkles className="h-5 w-5 text-indigo-600" /><span>{brandName}</span></div>{children}</section>
    </main>
  )
}
