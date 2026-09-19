import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { runInNewContext } from 'node:vm'
import { describe, expect, it, vi } from 'vitest'

const source = readFileSync(resolve('public/theme-init.js'), 'utf8')

function boot(pathname: string, preference: string | null, systemDark: boolean) {
  const root = { dataset: {} as Record<string, string>, style: { colorScheme: '' } }
  const storage = { getItem: vi.fn(() => preference), setItem: vi.fn(), removeItem: vi.fn() }
  runInNewContext(source, {
    document: { documentElement: root },
    localStorage: storage,
    location: { pathname },
    matchMedia: () => ({ matches: systemDark }),
  })
  return { root, storage }
}

describe('before-paint theme bootstrap', () => {
  it.each(['/', '/privacy', '/login', '/forgot-password', '/reset-password', '/inquiry', '/inquiry/demo', '/missing', '/signup', '/settings'])('defaults %s to light before authentication resolves', (path) => {
    const { root, storage } = boot(path, 'dark', true)
    expect(root.dataset.theme).toBe('light')
    expect(root.style.colorScheme).toBe('light')
    expect(storage.getItem).not.toHaveBeenCalled()
    expect(storage.setItem).not.toHaveBeenCalled()
    expect(storage.removeItem).not.toHaveBeenCalled()
  })

  it('stays light with System and a dark OS preference', () => {
    expect(boot('/privacy', null, true).root.dataset.theme).toBe('light')
  })
})
