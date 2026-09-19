import { Check, Inbox, Layers3, Workflow } from 'lucide-react'
import { useQuery } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { getPublicInquiryConfiguration } from '../services/publicInquiryApi'
import MurravoLogo from './MurravoLogo'

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
  const brandName = 'Murravo'
  const workspaceName = branding.data?.publicBrandName?.trim() || branding.data?.workspaceName?.trim()
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
            <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-white/15"><MurravoLogo compact decorative className="text-white" /></span>
            <div><p className="font-semibold text-white">{brandName}</p><p className="flex items-center gap-1.5 text-xs text-primary-200">{logoPath && <img src={logoPath} alt="" className="h-4 w-4 object-contain" />}{workspaceName || 'Secure workspace'}</p></div>
          </div>
          <div className="my-auto max-w-xl py-12">
            <p className="text-sm font-semibold uppercase tracking-[0.2em] text-primary-200">Lead operations, refined</p>
            <h2 className="mt-4 text-4xl font-bold leading-tight text-white lg:text-5xl">Turn every inquiry into organized momentum.</h2>
            <p className="mt-5 max-w-lg text-base leading-7 text-primary-100">{tagline}</p>
            <ul className="mt-8 space-y-4">
              {[[Inbox, 'Capture every inquiry'], [Workflow, 'Qualify leads with AI'], [Layers3, 'Keep your sales pipeline organized']].map(([Icon, label]) => (
                <li key={label as string} className="flex items-center gap-3 text-sm font-medium text-white"><span className="flex h-9 w-9 items-center justify-center rounded-lg bg-white/10"><Icon className="h-4 w-4" /></span>{label as string}<Check className="ml-auto h-4 w-4 text-primary-200" /></li>
              ))}
            </ul>
            <div className="auth-preview mt-10" aria-label="Illustrative inquiry workflow">
              <p className="text-xs font-semibold uppercase tracking-wider text-primary-100">A lead, from start to follow-up</p>
              <ol className="mt-4 grid gap-2">
                <li className="flex items-center gap-3 rounded-lg bg-white/10 px-3 py-2 text-sm"><Inbox className="h-4 w-4 text-primary-200" aria-hidden="true" /><span>New inquiry received</span><span className="ml-auto text-xs text-primary-100">01</span></li>
                <li className="flex items-center gap-3 rounded-lg bg-white/10 px-3 py-2 text-sm"><Workflow className="h-4 w-4 text-primary-200" aria-hidden="true" /><span>Qualification completed</span><span className="ml-auto text-xs text-primary-100">02</span></li>
                <li className="flex items-center gap-3 rounded-lg bg-white/10 px-3 py-2 text-sm"><Check className="h-4 w-4 text-primary-200" aria-hidden="true" /><span>Ready for follow-up</span><span className="ml-auto text-xs text-primary-100">03</span></li>
              </ol>
            </div>
          </div>
        </div>
      </section>
      <section className="auth-form-panel"><div className="auth-mobile-brand"><MurravoLogo decorative /></div>{children}</section>
    </main>
  )
}
