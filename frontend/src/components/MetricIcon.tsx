import type { LucideIcon } from 'lucide-react'

export default function MetricIcon({ icon: Icon }: { icon: LucideIcon }) {
  return <span className="metric-icon" aria-hidden="true"><Icon size={22} strokeWidth={2} /></span>
}
