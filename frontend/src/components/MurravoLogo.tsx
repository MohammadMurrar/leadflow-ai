import type { CSSProperties } from 'react'

type Props = {
  compact?: boolean
  decorative?: boolean
  className?: string
  style?: CSSProperties
}

export default function MurravoLogo({ compact = false, decorative = false, className = '', style }: Props) {
  return <span className={`murravo-logo ${className}`} style={style} role={decorative ? undefined : 'img'} aria-label={decorative ? undefined : 'Murravo'} aria-hidden={decorative || undefined}>
    <svg className="murravo-mark" viewBox="0 0 512 512" aria-hidden="true">
      <path className="murravo-mark-navy" d="M64 120c0-42 50-63 80-33l120 120c18 18 18 47 0 65l-31 31v-56c0-12-5-24-14-33l-72-72c-30-30-83-9-83 34V120Z" />
      <path className="murravo-mark-sky" d="M64 291c0-39 47-59 75-31l76 76c16 16 16 43 0 59l-75 75c-28 28-76 8-76-32V291Z" />
      <path className="murravo-mark-coral" d="M296 207 369 134c29-29 79-9 79 33v271c0 40-48 60-76 32l-77-77c-16-16-17-41-2-58l34-40c13-15 13-38 0-53l-34-39 3 4Z" />
    </svg>
    {!compact && <span className="murravo-wordmark">Murravo</span>}
  </span>
}
