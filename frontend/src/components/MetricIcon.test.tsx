import { render } from '@testing-library/react'
import { Users } from 'lucide-react'
import { describe, expect, it } from 'vitest'
import MetricIcon from './MetricIcon'

describe('metric icon', () => {
  it('uses the shared decorative tile and consistent icon geometry', () => {
    const { container } = render(<MetricIcon icon={Users} />)
    expect(container.querySelector('.metric-icon')).toHaveAttribute('aria-hidden', 'true')
    expect(container.querySelector('.metric-icon svg')).toHaveAttribute('width', '22')
    expect(container.querySelector('.metric-icon svg')).toHaveAttribute('stroke-width', '2')
  })
})
