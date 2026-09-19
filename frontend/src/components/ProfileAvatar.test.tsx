import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import ProfileAvatar from './ProfileAvatar'

describe('profile avatar', () => {
  it.each([
    ['Mona', 'M'],
    ['Mohammad Murrar', 'MM'],
    ['Mohammad Murrar Admin', 'MM'],
  ])('uses the existing initials rule for %s', (name, initials) => {
    render(<ProfileAvatar name={name} />)
    const avatar = screen.getByText(initials)
    expect(avatar).toHaveClass('profile-avatar')
    expect(avatar).toHaveAttribute('aria-hidden', 'true')
  })
})
