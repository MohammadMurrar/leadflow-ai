import { useState } from 'react'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'

function CounterFixture() {
  const [count, setCount] = useState(0)

  return (
    <section aria-labelledby="counter-heading">
      <h1 id="counter-heading">Testing foundation</h1>
      <button type="button" onClick={() => setCount((current) => current + 1)}>
        Count: {count}
      </button>
    </section>
  )
}

describe('frontend testing foundation', () => {
  it('renders in jsdom and supports an accessible user interaction', async () => {
    const user = userEvent.setup()
    render(<CounterFixture />)

    expect(document.body).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Testing foundation' })).toBeVisible()
    const button = screen.getByRole('button', { name: 'Count: 0' })
    await user.click(button)

    expect(screen.getByRole('button', { name: 'Count: 1' })).toHaveFocus()
  })

  it('starts with an isolated DOM and component state', () => {
    expect(screen.queryByRole('heading', { name: 'Testing foundation' })).not.toBeInTheDocument()

    render(<CounterFixture />)

    expect(screen.getByRole('button', { name: 'Count: 0' })).toBeEnabled()
  })
})
