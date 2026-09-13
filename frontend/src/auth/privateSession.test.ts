import { QueryClient } from '@tanstack/react-query'
import axios from 'axios'
import { describe, expect, it } from 'vitest'
import api from '../services/leadApi'
import { createPrivateSession, removeUnscopedPrivateData } from './privateSession'

const identity = (id: string, workspaceId = id) => ({ id, workspaceId, email: 'synthetic@example.invalid', displayName: id, role: 'ADMIN' as const })
const keys = [
    ['leads'], ['lead-details', 'lead'], ['qualification-attempts', 'lead'],
    ['dashboard-stats'], ['analytics'], ['notifications', 'unread-count'],
    ['notifications', 'inbox', 0, 10], ['settings', 'workspace'],
    ['settings', 'automation-status'], ['services'], ['active-services'],
]

describe('private authenticated cache lifetime', () => {
    it('captures A request ownership before an immediate synchronous switch to B', async () => {
        const a = createPrivateSession(identity('A'))
        let complete!: () => void
        const pending = api.put('/settings/workspace', {}, { adapter: config => new Promise(resolve => {
            complete = () => resolve({ data: { currency: 'USD' }, status: 200, statusText: 'OK', headers: {}, config })
        }) }).catch(error => error)
        a.close()
        const b = createPrivateSession(identity('B'))
        // An asynchronous interceptor would incorrectly capture B here.
        await Promise.resolve()
        complete()
        expect(axios.isCancel(await pending)).toBe(true)
        expect(b.client.getQueryCache().getAll()).toHaveLength(0)
        b.close()
    })

    it('separates every private data family across users, workspaces and restored cycles', () => {
        const a = createPrivateSession(identity('A'))
        keys.forEach(key => a.client.setQueryData(key, { owner: 'A', currency: 'USD', recipients: ['A'] }))
        const hashesA = a.client.getQueryCache().getAll().map(query => query.queryHash)
        a.close()
        expect(a.client.getQueryCache().getAll()).toHaveLength(0)
        for (const user of [identity('B'), identity('A', 'B'), identity('A')]) {
            const next = createPrivateSession(user)
            keys.forEach(key => {
                expect(next.client.getQueryData(key)).toBeUndefined()
                next.client.setQueryData(key, { owner: user.id })
            })
            expect(next.client.getQueryCache().getAll().some(query => hashesA.includes(query.queryHash))).toBe(false)
            next.close()
        }
    })

    it('retires unscoped private data while preserving slug-scoped inquiry and public branding', () => {
        const root = new QueryClient()
        keys.forEach(key => root.setQueryData(key, 'private'))
        root.setQueryData(['public-inquiry', 'configuration', 'A'], 'public-A')
        root.setQueryData(['public-inquiry', 'configuration', 'B'], 'public-B')
        root.setQueryData(['public', 'auth-branding'], 'branding')
        removeUnscopedPrivateData(root)
        expect(root.getQueryCache().getAll()).toHaveLength(3)
        expect(root.getQueryData(['public-inquiry', 'configuration', 'A'])).toBe('public-A')
        expect(root.getQueryData(['public-inquiry', 'configuration', 'B'])).toBe('public-B')
    })

    it('settings updates and currency remain in the owning session only', () => {
        const a = createPrivateSession(identity('A'))
        const b = createPrivateSession(identity('B'))
        a.client.setQueryData(['settings', 'workspace'], { currency: 'USD' })
        b.client.setQueryData(['settings', 'workspace'], { currency: 'EUR' })
        a.client.setQueryData(['settings', 'workspace'], { currency: 'GBP' })
        expect(b.client.getQueryData(['settings', 'workspace'])).toEqual({ currency: 'EUR' })
        a.close()
        expect(a.client.getQueryData(['settings', 'workspace'])).toBeUndefined()
        b.close()
    })

    it('cancels a slow A query and cannot populate B when its underlying promise resolves', async () => {
        const a = createPrivateSession(identity('A'))
        let resolve!: (value: string) => void
        const pending = a.client.fetchQuery({ queryKey: ['leads'], queryFn: () => new Promise<string>(done => { resolve = done }) })
        const observed = pending.catch(() => undefined)
        a.close()
        const b = createPrivateSession(identity('B'))
        b.client.setQueryData(['leads'], 'B')
        resolve('A')
        await observed
        expect(a.client.getQueryCache().getAll()).toHaveLength(0)
        expect(b.client.getQueryData(['leads'])).toBe('B')
        b.close()
    })

    it('rejects a late private HTTP mutation response even if the transport ignores abort', async () => {
        const a = createPrivateSession(identity('A'))
        let complete!: () => void
        let started!: () => void
        const ready = new Promise<void>(resolve => { started = resolve })
        const request = api.put('/settings/workspace', {}, { adapter: config => new Promise(resolve => {
            complete = () => resolve({ data: { currency: 'USD' }, status: 200, statusText: 'OK', headers: {}, config })
            started()
        }) })
        const result = request.catch(error => error)
        await ready
        a.close()
        const b = createPrivateSession(identity('B'))
        complete()
        expect(axios.isCancel(await result)).toBe(true)
        expect(b.client.getQueryCache().getAll()).toHaveLength(0)
        b.close()
    })
})
