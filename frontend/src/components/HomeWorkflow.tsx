import { useEffect, useRef, useState } from 'react'

const activity = [
  { title: 'Inquiry received', detail: 'Emily Carter at Cedar Ridge Studio asks about Product strategy.', result: 'Received' },
  { title: 'AI-assisted qualification', detail: 'A score and suggested reply are prepared for review.', result: 'Score 82/100' },
  { title: 'Human review and decision', detail: 'An administrator checks the context and chooses to follow up.', result: 'Review complete' },
  { title: 'Follow-up and workspace update', detail: 'The next action is recorded with the lead.', result: 'Action recorded' },
] as const

export default function HomeWorkflow() {
  const recordRef = useRef<HTMLElement>(null)
  const [step, setStep] = useState(() => window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
    || typeof window.IntersectionObserver === 'undefined' ? 4 : 0)

  useEffect(() => {
    const media = window.matchMedia?.('(prefers-reduced-motion: reduce)')
    if (media?.matches) return
    if (typeof window.IntersectionObserver === 'undefined') return

    let started = false
    let timers: number[] = []
    const observer = new IntersectionObserver((entries) => {
      if (started || !entries.some((entry) => entry.isIntersecting && entry.intersectionRatio >= 0.4)) return
      started = true
      observer.disconnect()
      setStep(1)
      timers = [2, 3, 4].map((next, index) => window.setTimeout(() => setStep(next), (index + 1) * 180))
    }, { threshold: 0.4, rootMargin: '0px' })
    if (recordRef.current) observer.observe(recordRef.current)

    const onMotionChange = (event: MediaQueryListEvent) => {
      if (!event.matches) return
      observer.disconnect()
      timers.forEach(window.clearTimeout)
      setStep(4)
    }
    media?.addEventListener('change', onMotionChange)
    return () => {
      observer.disconnect()
      timers.forEach(window.clearTimeout)
      media?.removeEventListener('change', onMotionChange)
    }
  }, [])

  return <section id="process" className="public-process" data-workflow-step={step}>
    <div className="public-container public-process-grid">
      <div><p className="public-kicker">HOW IT WORKS</p><h2>A clear path through every inquiry.</h2><p>Automation helps organize the work. Human judgment stays in charge of the relationship.</p></div>
      <article ref={recordRef} className="home-workflow-record" data-active={step > 0} aria-label="Illustrative inquiry activity">
        <header className="home-workflow-header"><strong>Inquiry activity</strong><span>Illustrative workflow data</span></header>
        <div className="home-workflow-subject"><h3>Emily Carter</h3><p>Cedar Ridge Studio · Product strategy inquiry</p></div>
        <ol className="home-workflow-events">
          {activity.map(({ title, detail, result }, index) => <li key={title} data-state={step > index ? 'complete' : 'pending'}>
            <span className="home-workflow-number">{String(index + 1).padStart(2, '0')}</span>
            <div><h4>{title}</h4><p>{detail}</p></div>
            <span className="home-workflow-result">{step > index ? result : 'Pending'}</span>
          </li>)}
        </ol>
      </article>
    </div>
  </section>
}
