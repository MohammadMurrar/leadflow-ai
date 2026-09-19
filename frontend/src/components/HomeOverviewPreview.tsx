import { useEffect, useState } from 'react'
import MurravoLogo from './MurravoLogo'

const days = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']
const illustrativeNewLeads = [1, 3, 2, 4, 2, 3, 3]
const illustrativeQualifiedLeads = [0, 1, 1, 2, 1, 3, 3]

function point(index: number, value: number) {
  return `${40 + index * 80},${148 - value * 30}`
}

function points(values: number[]) {
  return values.map((value, index) => point(index, value)).join(' ')
}

export default function HomeOverviewPreview() {
  const [step, setStep] = useState(() => window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ? 4 : 0)

  useEffect(() => {
    const media = window.matchMedia?.('(prefers-reduced-motion: reduce)')
    if (media?.matches) return
    const timers = [
      window.setTimeout(() => setStep(1), 280),
      window.setTimeout(() => setStep(2), 620),
      window.setTimeout(() => setStep(3), 860),
      window.setTimeout(() => setStep(4), 1080),
    ]
    const onMotionChange = (event: MediaQueryListEvent) => {
      if (event.matches) {
        timers.forEach(window.clearTimeout)
        setStep(4)
      }
    }
    media?.addEventListener('change', onMotionChange)
    return () => {
      timers.forEach(window.clearTimeout)
      media?.removeEventListener('change', onMotionChange)
    }
  }, [])

  const qualified = step >= 2

  return <section className="home-overview" data-demo-step={step} aria-label="Illustrative Murravo overview">
    <header className="home-overview-header">
      <span className="home-overview-workspace"><MurravoLogo compact decorative /> Demo workspace</span>
      <span className="home-overview-disclosure">Illustrative workspace data</span>
    </header>
    <div className="home-overview-body">
      <div className="home-overview-title"><div><span>Overview</span><h2>Inquiries and qualification</h2></div><span className="home-overview-range">Last 7 days</span></div>
      <div className="home-overview-totals" aria-label="Seven-day lead totals">
        <p>New leads <strong>{step >= 3 ? 18 : 17}</strong></p>
        <p>Qualified leads <strong>{step >= 3 ? 11 : 10}</strong></p>
      </div>
      <div className="home-overview-content">
        <section className="home-overview-recent" aria-label="Recent leads">
          <h3>Recent leads</h3>
          <div className={`home-overview-lead ${step >= 1 ? 'is-visible' : ''}`} aria-hidden={step < 1}>
            <div className="home-overview-lead-heading"><strong>Emily Carter</strong><span className={qualified ? 'is-qualified' : ''}>{qualified ? 'Qualified' : 'New'}</span></div>
            <p>Cedar Ridge Studio · Product strategy</p>
            <div className="home-overview-outcome"><span>{qualified ? 'AI score 82/100' : 'Qualification in progress'}</span><span>{qualified ? 'High priority' : 'Awaiting result'}</span></div>
            <p className="home-overview-next"><strong>Human next step</strong> Review and respond</p>
          </div>
        </section>
        <figure className="home-overview-chart">
          <figcaption><strong>Lead performance</strong><span>Daily leads · Last 7 days</span></figcaption>
          <div className="home-overview-legend"><span className="is-new">New leads</span><span className="is-qualified">Qualified leads</span></div>
          <svg viewBox="0 0 560 184" role="img" aria-label="Daily new and qualified lead counts from Monday through Sunday" preserveAspectRatio="xMidYMid meet">
            {[0, 2, 4].map((tick) => <g key={tick}><line className="home-chart-gridline" x1="40" x2="520" y1={148 - tick * 30} y2={148 - tick * 30} /><text className="home-chart-axis" x="16" y={152 - tick * 30}>{tick}</text></g>)}
            {days.map((day, index) => <text className="home-chart-axis" key={day} x={40 + index * 80} y="176" textAnchor="middle">{day}</text>)}
            <g className={`home-chart-series ${step >= 4 ? 'is-visible' : ''}`}>
              <polyline className="home-chart-new" points={points(illustrativeNewLeads)} />
              <polyline className="home-chart-qualified" points={points(illustrativeQualifiedLeads)} />
              {illustrativeNewLeads.map((value, index) => <circle className="home-chart-dot-new" key={`new-${index}`} cx={40 + index * 80} cy={148 - value * 30} r="4"><title>{days[index]}: {value} new leads</title></circle>)}
              {illustrativeQualifiedLeads.map((value, index) => <circle className="home-chart-dot-qualified" key={`qualified-${index}`} cx={40 + index * 80} cy={148 - value * 30} r="4"><title>{days[index]}: {value} qualified leads</title></circle>)}
            </g>
          </svg>
          <table className="sr-only"><caption>Illustrative daily lead performance</caption><thead><tr><th>Day</th><th>New leads</th><th>Qualified leads</th></tr></thead><tbody>{days.map((day, index) => <tr key={day}><th scope="row">{day}</th><td>{illustrativeNewLeads[index]}</td><td>{illustrativeQualifiedLeads[index]}</td></tr>)}</tbody></table>
        </figure>
      </div>
    </div>
  </section>
}
