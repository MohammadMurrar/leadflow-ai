import { existsSync, readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import MurravoLogo from './MurravoLogo'

describe('Murravo logo assets', () => {
  it('names an informative logo and hides decorative instances', () => {
    const { container } = render(<><MurravoLogo compact /><MurravoLogo decorative /></>)
    expect(screen.getByRole('img', { name: 'Murravo' })).toBeVisible()
    expect(container.querySelector('[aria-hidden="true"].murravo-logo')).toBeInTheDocument()
  })

  it.each(['favicon.svg', 'murravo-logo.svg', 'murravo-mark.svg', 'murravo-mark-light.svg'])(
    'ships %s as an SVG asset', (filename) => {
      const path = resolve(process.cwd(), 'public', filename)
      expect(existsSync(path)).toBe(true)
      expect(readFileSync(path, 'utf8')).toContain('<svg')
    },
  )
})
