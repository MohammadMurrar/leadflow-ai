import { useEffect, useRef, useState } from 'react'
import { ArrowRight, Bell, ChartNoAxesCombined, LockKeyhole, Mail, Menu, ShieldCheck, X } from 'lucide-react'
import { Link, useLocation } from 'react-router-dom'
import HomeOverviewPreview from './HomeOverviewPreview'
import HomeWorkflow from './HomeWorkflow'
import MurravoLogo from './MurravoLogo'
import './publicPages.css'
import './homePage.css'

const contact = 'mmurrar.business@gmail.com'

function PublicHeader({ home = false }: { home?: boolean }) {
  const [open, setOpen] = useState(false)
  const links = home ? [
    ['Product', '#product'], ['How it works', '#process'], ['Security', '#security'], ['Contact', '#contact'],
  ] : [['Home', '/'], ['Privacy', '/privacy'], ['Contact', '/#contact']]
  return <header className="public-header">
    <div className="public-header-inner">
      <Link to="/" className="public-brand" aria-label="Murravo home"><MurravoLogo decorative /></Link>
      <nav className="public-nav" aria-label="Main navigation">
        {links.map(([label, href]) => href.startsWith('#') ? <a key={label} href={href}>{label}</a> : <Link key={label} to={href}>{label}</Link>)}
        {home && <Link to="/privacy">Privacy</Link>}
      </nav>
      <Link to="/login" className="public-signin">Sign in <ArrowRight size={16} aria-hidden="true" /></Link>
      <button className="public-menu-button" type="button" aria-label={open ? 'Close menu' : 'Open menu'} aria-expanded={open} aria-controls="public-mobile-menu" onClick={() => setOpen(!open)}>{open ? <X size={22} /> : <Menu size={22} />}</button>
    </div>
    <nav id="public-mobile-menu" className={`public-mobile-nav ${open ? 'is-open' : ''}`} aria-label="Mobile navigation" inert={!open}>
      {links.map(([label, href]) => href.startsWith('#') ? <a key={label} href={href} onClick={() => setOpen(false)}>{label}</a> : <Link key={label} to={href} onClick={() => setOpen(false)}>{label}</Link>)}
      {home && <Link to="/privacy" onClick={() => setOpen(false)}>Privacy</Link>}
      <Link to="/login" onClick={() => setOpen(false)}>Sign in</Link>
    </nav>
  </header>
}

function PublicFooter() {
  return <footer className="public-footer">
    <div className="public-container public-footer-inner">
      <div><Link to="/" aria-label="Murravo home"><MurravoLogo decorative /></Link><p>Operated by Mohammad Murrar</p></div>
      <div className="public-footer-links"><Link to="/privacy">Privacy</Link><a href={`mailto:${contact}`}>{contact}</a><Link to="/login">Sign in</Link></div>
      <small>© {new Date().getFullYear()} Murravo</small>
    </div>
  </footer>
}

export function HomePage() {
  const location = useLocation()
  useEffect(() => {
    if (location.hash) document.getElementById(location.hash.slice(1))?.scrollIntoView?.()
  }, [location.hash])
  return <div className="public-page public-home"><PublicHeader home /><main>
    <section className="public-hero">
      <div className="public-container">
        <div className="public-hero-copy"><h1>Murravo</h1><p className="public-hero-statement">From inquiry to the next decision.</p><p>Receive inquiries, organize leads, qualify opportunities with AI assistance, and follow progress in one business workspace.</p><div className="public-actions"><a className="public-button" href="#contact">Contact for a pilot</a><Link className="public-secondary" to="/login">Sign in</Link></div></div>
        <HomeOverviewPreview />
      </div>
    </section>
    <section id="product" className="public-section public-container public-outcome"><div className="public-section-intro"><p className="public-kicker">THE WORKSPACE</p><h2>Everything connected to the lead.</h2><p>Give each workspace its own public inquiry page, then manage the resulting leads with service context, currency, notifications, dashboards, and analytics in one place.</p></div><div className="public-capabilities"><article><span>01 / CAPTURE</span><h3>Capture and organize</h3><p>Customers can submit a workspace-specific inquiry. Administrators can search, sort, and review leads without losing their context.</p></article><article><span>02 / ASSIST</span><h3>Qualify with assistance</h3><p>AI-assisted qualification surfaces scores, priority, and a suggested reply. Your team decides what to do next.</p></article><article><span>03 / FOLLOW UP</span><h3>Stay operational</h3><p>Notifications, service configuration, workspace currency, and performance views support day-to-day follow-up.</p></article></div></section>
    <HomeWorkflow />
    <section id="security" className="public-section public-container public-security"><div className="public-section-intro"><p className="public-kicker">ACCESS & CONTROL</p><h2>Built for a protected workspace.</h2><p>Tenant isolation and server-side access controls separate business workspaces. Administrator sessions, CSRF protection, rate-limited public inquiry submission, and a secure password-reset flow are part of the current application.</p></div><div className="public-security-points"><p><ShieldCheck size={20} aria-hidden="true" /> Workspace-scoped access</p><p><LockKeyhole size={20} aria-hidden="true" /> Protected administrator sessions</p><p><Bell size={20} aria-hidden="true" /> Operational notifications</p><p><ChartNoAxesCombined size={20} aria-hidden="true" /> Reviewable lead progress</p></div></section>
    <section id="contact" className="public-contact"><div className="public-container public-contact-inner"><div><p className="public-kicker">CONTACT</p><h2>Explore a controlled pilot.</h2><p>Tell Mohammad a little about your business and how you currently handle inquiries. We can discuss whether Murravo is a fit.</p></div><a className="public-button" href={`mailto:${contact}?subject=Murravo%20pilot%20inquiry`}>Email Mohammad <Mail size={18} aria-hidden="true" /></a></div></section>
  </main><PublicFooter /></div>
}

export function NotFoundPage() {
  const headingRef = useRef<HTMLHeadingElement>(null)
  useEffect(() => { headingRef.current?.focus() }, [])
  return <div className="public-page"><PublicHeader /><main className="public-empty public-container"><p className="public-kicker">404</p><h1 ref={headingRef} tabIndex={-1}>Page not found</h1><p>That page is not available. You can return home or sign in to your workspace.</p><div className="public-actions"><Link className="public-button" to="/">Return home <ArrowRight size={18} aria-hidden="true" /></Link><Link className="public-secondary" to="/login">Sign in</Link></div></main><PublicFooter /></div>
}

const policySections = [
  ['Information submitted through inquiry forms', 'Inquiry forms can collect a person’s name, email, phone, company, requested service, budget, preferred start date, and message. Fields marked optional may be left blank. A workspace’s own public notice may provide additional context.'],
  ['Administrator accounts and sessions', 'Administrators use account identity and credentials to access their workspace. The application uses necessary session and CSRF cookies for authentication and request protection. Browser-local appearance and interface preferences are presentation settings, not advertising cookies.'],
  ['Leads and qualification', 'Murravo stores inquiries as leads, including status, service context, notes, estimated value, qualification results, and follow-up history. AI-assisted processing may use relevant inquiry details to prepare scores, summaries, and suggested replies; an administrator makes the business decision.'],
  ['Workspace and service configuration', 'Workspace administrators can configure public identity, available services, currency display, notification recipients, and other workspace settings. These settings support inquiry handling and day-to-day operations.'],
  ['Email', 'The application can send administrator notifications about lead activity and password-reset messages to eligible accounts. Email delivery uses configured mail infrastructure and may be disabled in a given deployment.'],
  ['Why this information is used', 'Information is used to receive and respond to inquiries, operate and protect administrator access, organize and qualify leads, notify workspace administrators, and maintain the service. Murravo does not include advertising trackers or non-essential analytics in the current frontend.'],
  ['Retention', 'Lead and workspace information is retained according to operational needs and the arrangements for a given workspace. No universal automatic deletion period is currently implemented. Password-reset records have a configurable operational retention setting; this is not a promise that all other records are deleted on that schedule.'],
  ['Security', 'The application uses workspace isolation, server-side authorization, protected sessions, CSRF protection, rate limiting for public submissions, and a constrained password-reset flow. No system can guarantee absolute security.'],
  ['Service providers and location', 'Hosting, database, email delivery, and AI-processing providers may process information to operate the service. A production host and processing locations have not been fixed in this policy; hosted or cross-border processing will depend on the selected deployment and providers. Ask us about a specific pilot setup before sharing sensitive information.'],
  ['Your choices and requests', 'For questions about information submitted to Murravo, or to request access, correction, or deletion where applicable, contact the operator. We will assess requests in the context of the relevant workspace and applicable obligations.'],
  ['Children', 'Murravo is intended for business use, not for children. Do not submit children’s personal information through an inquiry form.'],
  ['Changes', 'This policy may change as the product, providers, and deployment arrangements develop. The last-updated date below will change when the policy is revised.'],
] as const

export function PrivacyPage() {
  return <div className="public-page"><PublicHeader /><main className="public-policy public-container"><p className="public-kicker">POLICY</p><h1>Privacy at Murravo</h1><p className="public-policy-lead">This good-faith MVP policy explains how the current Murravo application handles information. It is not a certification or a substitute for advice about a particular deployment.</p><dl className="public-policy-facts"><div><dt>Product</dt><dd>Murravo</dd></div><div><dt>Operator</dt><dd>Mohammad Murrar</dd></div><div><dt>Contact</dt><dd><a href={`mailto:${contact}`}>{contact}</a></dd></div><div><dt>Last updated</dt><dd>September 16, 2026</dd></div></dl><div className="public-policy-body">{policySections.map(([heading, copy]) => <section key={heading}><h2>{heading}</h2><p>{copy}</p></section>)}</div></main><PublicFooter /></div>
}
