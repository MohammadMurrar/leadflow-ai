import type { CSSProperties } from 'react'

type Props = {
  compact?: boolean
  decorative?: boolean
  className?: string
  style?: CSSProperties
}

export default function MurravoLogo({ compact = false, decorative = false, className = '', style }: Props) {
  return <span className={`murravo-logo ${className}`} style={style} role={decorative ? undefined : 'img'} aria-label={decorative ? undefined : 'Murravo'} aria-hidden={decorative || undefined}>
    <svg className="murravo-mark" viewBox="0 0 40 40" fill="none" aria-hidden="true">
      <path d="M6 30V10L20 24L34 10V30" stroke="currentColor" strokeWidth="5.5" strokeLinecap="square" strokeLinejoin="miter" />
      <path d="M27 30H34" stroke="currentColor" strokeWidth="5.5" strokeLinecap="square" />
    </svg>
    {!compact && <span className="murravo-wordmark">Murravo</span>}
  </span>
}
