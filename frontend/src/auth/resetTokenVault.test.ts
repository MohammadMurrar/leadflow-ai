import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  captureResetToken,
  clearResetToken,
  retainResetTokenLifecycle,
} from './resetTokenVault'

const tokenA = 'A'.repeat(43)
const tokenB = 'B'.repeat(43)

async function flushLifecycleCleanup() {
  await Promise.resolve()
}

beforeEach(() => {
  clearResetToken()
  window.history.replaceState({ safe: 'state' }, '', '/reset-password?source=safe')
})

afterEach(() => {
  clearResetToken()
  vi.restoreAllMocks()
})

describe('reset token vault', () => {
  it('captures a valid fragment and removes it while preserving safe navigation state', () => {
    const state = { safe: 'state' }
    window.history.replaceState(state, '', `/reset-password?source=safe#token=${tokenA}`)

    expect(captureResetToken()).toBe(tokenA)
    expect(window.location.pathname).toBe('/reset-password')
    expect(window.location.search).toBe('?source=safe')
    expect(window.location.hash).toBe('')
    expect(window.history.state).toEqual(state)
  })

  it('reuses a missing fragment only inside the retained lifecycle', async () => {
    window.history.replaceState(null, '', `/reset-password#token=${tokenA}`)
    expect(captureResetToken()).toBe(tokenA)
    const releaseFirstMount = retainResetTokenLifecycle()
    releaseFirstMount()
    const releaseStrictRemount = retainResetTokenLifecycle()
    await flushLifecycleCleanup()
    expect(captureResetToken()).toBe(tokenA)

    releaseStrictRemount()
    await flushLifecycleCleanup()
    expect(captureResetToken()).toBeNull()
  })

  it.each([
    `#token=${'A'.repeat(42)}=`,
    `#token=${'A'.repeat(42)} `,
    `#token=${'A'.repeat(42)}+`,
    `#token=${'A'.repeat(42)}/`,
    `#token=${'A'.repeat(42)}%20`,
    `#token=${tokenA}&token=${tokenB}`,
    `#token=${tokenA}&extra=1`,
    '#token=',
    '#other=value',
  ])('rejects and removes malformed fragment %s', (fragment) => {
    window.history.replaceState(null, '', `/reset-password${fragment}`)
    expect(captureResetToken()).toBeNull()
    expect(window.location.hash).toBe('')
  })

  it('replaces token A with token B and removes the second fragment', () => {
    window.history.replaceState(null, '', `/reset-password#token=${tokenA}`)
    expect(captureResetToken()).toBe(tokenA)
    window.history.replaceState(null, '', `/reset-password#token=${tokenB}`)
    expect(captureResetToken()).toBe(tokenB)
    expect(window.location.hash).toBe('')
  })

  it('clears token A when a second fragment is malformed', () => {
    window.history.replaceState(null, '', `/reset-password#token=${tokenA}`)
    expect(captureResetToken()).toBe(tokenA)
    window.history.replaceState(null, '', `/reset-password#token=${tokenB}&extra=1`)
    expect(captureResetToken()).toBeNull()
    expect(window.location.hash).toBe('')
  })

  it('protects valid token B from lifecycle A pending cleanup', async () => {
    window.history.replaceState(null, '', `/reset-password#token=${tokenA}`)
    expect(captureResetToken()).toBe(tokenA)
    const releaseA = retainResetTokenLifecycle()
    releaseA()

    window.history.replaceState(null, '', `/reset-password#token=${tokenB}`)
    expect(captureResetToken()).toBe(tokenB)
    const releaseB = retainResetTokenLifecycle()
    expect(window.location.hash).toBe('')
    await flushLifecycleCleanup()
    expect(captureResetToken()).toBe(tokenB)

    releaseB()
    await flushLifecycleCleanup()
    expect(captureResetToken()).toBeNull()
  })

  it('fails closed when malformed B arrives before lifecycle A cleanup', async () => {
    window.history.replaceState(null, '', `/reset-password#token=${tokenA}`)
    expect(captureResetToken()).toBe(tokenA)
    const releaseA = retainResetTokenLifecycle()
    releaseA()

    window.history.replaceState(null, '', `/reset-password#token=${tokenB}&extra=1`)
    expect(captureResetToken()).toBeNull()
    expect(window.location.hash).toBe('')
    await flushLifecycleCleanup()
    expect(captureResetToken()).toBeNull()
  })

  it('makes the second same-task reset navigation win over the first cleanup', async () => {
    window.history.replaceState(null, '', `/reset-password#token=${tokenA}`)
    expect(captureResetToken()).toBe(tokenA)
    const releaseA = retainResetTokenLifecycle()
    releaseA()
    window.history.replaceState(null, '', `/reset-password#token=${tokenB}`)
    expect(captureResetToken()).toBe(tokenB)
    const releaseB = retainResetTokenLifecycle()

    await flushLifecycleCleanup()
    expect(captureResetToken()).toBe(tokenB)
    expect(window.location.hash).toBe('')
    releaseB()
  })

  it('keeps Strict Mode cleanup from affecting a later navigation generation', async () => {
    window.history.replaceState(null, '', `/reset-password#token=${tokenA}`)
    expect(captureResetToken()).toBe(tokenA)
    const releaseInitialMount = retainResetTokenLifecycle()
    releaseInitialMount()
    const releaseStrictRemount = retainResetTokenLifecycle()
    await flushLifecycleCleanup()
    expect(captureResetToken()).toBe(tokenA)

    releaseStrictRemount()
    window.history.replaceState(null, '', `/reset-password#token=${tokenB}`)
    expect(captureResetToken()).toBe(tokenB)
    const releaseB = retainResetTokenLifecycle()
    await flushLifecycleCleanup()
    expect(captureResetToken()).toBe(tokenB)
    releaseB()
  })

  it('starts with an empty vault after a genuine module reset boundary', async () => {
    window.history.replaceState(null, '', `/reset-password#token=${tokenA}`)
    expect(captureResetToken()).toBe(tokenA)
    vi.resetModules()
    const freshVault = await import('./resetTokenVault')
    expect(freshVault.captureResetToken()).toBeNull()
    freshVault.clearResetToken()
  })

  it('clears a released lifecycle and cannot resurrect removed material', async () => {
    window.history.replaceState(null, '', `/reset-password#token=${tokenA}`)
    expect(captureResetToken()).toBe(tokenA)
    const release = retainResetTokenLifecycle()
    release()
    await flushLifecycleCleanup()
    window.history.replaceState(null, '', '/elsewhere')
    window.history.replaceState(null, '', '/reset-password')
    expect(captureResetToken()).toBeNull()
  })

  it('never writes storage or logs token material', () => {
    const local = vi.spyOn(window.localStorage, 'setItem')
    const session = vi.spyOn(window.sessionStorage, 'setItem')
    const log = vi.spyOn(console, 'log').mockImplementation(() => undefined)
    window.history.replaceState(null, '', `/reset-password#token=${tokenA}`)
    expect(captureResetToken()).toBe(tokenA)
    expect(local).not.toHaveBeenCalled()
    expect(session).not.toHaveBeenCalled()
    expect(log).not.toHaveBeenCalled()
    expect(document.body).not.toHaveTextContent(tokenA)
  })
})
