const CANONICAL_TOKEN = /^[A-Za-z0-9_-]{43}$/
let capturedToken: string | null = null
let activeLifecycles = 0
let lifecycleVersion = 0

export function captureResetToken(): string | null {
  const fragment = window.location.hash
  if (!fragment) return capturedToken

  // A present fragment begins a new navigation generation before it is validated.
  lifecycleVersion += 1
  let candidate: string | null = null
  const rawFragment = fragment.startsWith('#') ? fragment.slice(1) : ''
  if (rawFragment.startsWith('token=') && !rawFragment.includes('&')) {
    const rawToken = rawFragment.slice('token='.length)
    if (CANONICAL_TOKEN.test(rawToken)) candidate = rawToken
  }
  window.history.replaceState(window.history.state, '', window.location.pathname + window.location.search)
  capturedToken = candidate
  return candidate
}

export function retainResetTokenLifecycle(): () => void {
  activeLifecycles += 1
  lifecycleVersion += 1
  let released = false
  return () => {
    if (released) return
    released = true
    activeLifecycles -= 1
    const releaseVersion = ++lifecycleVersion
    queueMicrotask(() => {
      if (activeLifecycles === 0 && lifecycleVersion === releaseVersion) clearResetToken()
    })
  }
}

export function clearResetToken(): void {
  capturedToken = null
  lifecycleVersion += 1
}

export function isCanonicalResetToken(value: string): boolean {
  return CANONICAL_TOKEN.test(value)
}
