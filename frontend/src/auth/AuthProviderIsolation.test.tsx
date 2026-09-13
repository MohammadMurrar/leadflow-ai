import { QueryClient, QueryClientProvider, useQuery, useQueryClient } from '@tanstack/react-query'
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { useEffect } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import AuthProvider from './AuthProvider'
import { useAuth } from './auth'
import { getCurrentUser, login, logout } from './authApi'
import { setUnauthorizedHandler } from '../services/leadApi'

vi.mock('./authApi', () => ({ getCurrentUser: vi.fn(), logout: vi.fn(), login: vi.fn(), obtainCsrfToken: vi.fn() }))
vi.mock('../services/publicInquiryApi', () => ({ getPublicInquiryConfiguration: vi.fn().mockRejectedValue(new Error('unavailable')) }))
vi.mock('../services/leadApi', () => ({ setUnauthorizedHandler: vi.fn(), activatePrivateRequests: vi.fn(() => vi.fn()) }))

const user = (id: string) => ({ id, workspaceId: `workspace-${id}`, email: 'synthetic@example.invalid', displayName: id, role: 'ADMIN' as const })
class Channel {
    static open: Channel[] = []
    static sent: unknown[] = []
    onmessage: ((event: { data: unknown }) => void) | null = null
    constructor() { Channel.open.push(this) }
    postMessage(data: unknown) { Channel.sent.push(data) }
    close() { Channel.open = Channel.open.filter(item => item !== this) }
    static receive(data: unknown) { Channel.open.forEach(item => item.onmessage?.({ data })) }
}
let privateClient: QueryClient
function PrivatePage() {
    const { user: identity, signOut } = useAuth()
    const client = useQueryClient()
    useEffect(() => { privateClient = client }, [client])
    const { data } = useQuery({ queryKey: ['settings', 'workspace'], queryFn: async () => `${identity.id}-currency` })
    return <><div>{identity.id} private {data}</div><button onClick={() => { void signOut() }}>Log out</button></>
}
function mount(root = new QueryClient()) {
    return { root, ...render(<QueryClientProvider client={root}><MemoryRouter><AuthProvider><PrivatePage /></AuthProvider></MemoryRouter></QueryClientProvider>) }
}
beforeEach(() => {
    vi.stubGlobal('BroadcastChannel', Channel)
    Channel.open = []
    Channel.sent = []
    vi.mocked(getCurrentUser).mockResolvedValue(user('A'))
})
afterEach(() => vi.unstubAllGlobals())

describe('authentication identity transitions', () => {
    it('A login, logout and B login in the same lifecycle never render A currency for B', async () => {
        vi.mocked(getCurrentUser).mockRejectedValueOnce(new Error('signed out'))
        vi.mocked(login).mockResolvedValueOnce(user('A')).mockResolvedValueOnce(user('B'))
        mount()
        const signIn = async () => {
            fireEvent.change(await screen.findByLabelText('Email address'), { target: { value: 'synthetic@example.invalid' } })
            fireEvent.change(screen.getByLabelText('Password'), { target: { value: `Aa1!${crypto.randomUUID()}` } })
            await act(async () => { screen.getByRole('button', { name: 'Sign in securely' }).click() })
        }
        await signIn()
        await screen.findByText('A private A-currency')
        const old = privateClient
        await act(async () => { screen.getByText('Log out').click() })
        expect(old.getQueryCache().getAll()).toHaveLength(0)
        await signIn()
        await screen.findByText('B private B-currency')
        expect(screen.queryByText(/A private/)).not.toBeInTheDocument()
        expect(privateClient).not.toBe(old)
        expect(Channel.sent).toEqual(['changed', 'logout', 'changed'])
    })

    it('a late restoration cannot overwrite a newer cross-tab identity', async () => {
        let resolve!: (value: ReturnType<typeof user>) => void
        vi.mocked(getCurrentUser).mockImplementationOnce(() => new Promise(done => { resolve = done }))
            .mockResolvedValueOnce(user('B'))
        mount()
        await waitFor(() => expect(resolve).toBeDefined())
        await act(async () => { Channel.receive('changed') })
        await screen.findByText('B private B-currency')
        await act(async () => { resolve(user('A')) })
        expect(screen.queryByText(/A private/)).not.toBeInTheDocument()
        expect(screen.getByText('B private B-currency')).toBeVisible()
    })
    it('logout removes private currency and preserves public inquiry cache', async () => {
        const view = mount()
        view.root.setQueryData(['public-inquiry', 'configuration', 'A'], 'public-A')
        await screen.findByText('A private A-currency')
        const old = privateClient
        await act(async () => { screen.getByText('Log out').click() })
        await screen.findByLabelText('Email address')
        expect(logout).toHaveBeenCalledTimes(1)
        expect(old.getQueryCache().getAll()).toHaveLength(0)
        expect(view.root.getQueryData(['public-inquiry', 'configuration', 'A'])).toBe('public-A')
        expect(Channel.sent).toEqual(['logout'])
    })

    it('public-route unmount and restoration as B cannot render A data', async () => {
        const first = mount()
        await screen.findByText('A private A-currency')
        const old = privateClient
        first.unmount()
        expect(old.getQueryCache().getAll()).toHaveLength(0)
        vi.mocked(getCurrentUser).mockResolvedValue(user('B'))
        mount(first.root)
        expect(screen.queryByText(/A private/)).not.toBeInTheDocument()
        await screen.findByText('B private B-currency')
        expect(privateClient).not.toBe(old)
    })

    it.each(['logout', 'changed'])('cross-tab %s retires A before any restoration', async event => {
        mount()
        await screen.findByText('A private A-currency')
        const old = privateClient
        vi.mocked(getCurrentUser).mockResolvedValue(user('B'))
        await act(async () => { Channel.receive(event) })
        expect(old.getQueryCache().getAll()).toHaveLength(0)
        expect(screen.queryByText(/A private/)).not.toBeInTheDocument()
        if (event === 'changed') await screen.findByText('B private B-currency')
        else await screen.findByLabelText('Email address')
        expect(Channel.sent).toHaveLength(0)
    })

    it('failed me restoration and session expiry remove all private entries', async () => {
        const root = new QueryClient()
        root.setQueryData(['leads'], 'stale')
        vi.mocked(getCurrentUser).mockRejectedValueOnce(new Error('expired'))
        const first = mount(root)
        await screen.findByLabelText('Email address')
        expect(root.getQueryData(['leads'])).toBeUndefined()
        first.unmount()
        mount(root)
        await screen.findByText('A private A-currency')
        const old = privateClient
        const handlers = vi.mocked(setUnauthorizedHandler).mock.calls.map(call => call[0]).filter(Boolean)
        await act(async () => { handlers.at(-1)!() })
        await screen.findByLabelText('Email address')
        expect(old.getQueryCache().getAll()).toHaveLength(0)
        expect(Channel.sent).toEqual(['logout'])
    })

    it('ignores unrecognized messages and removes synchronization listeners on unmount', async () => {
        const view = mount()
        await screen.findByText('A private A-currency')
        await act(async () => { Channel.receive({ user: 'B' }) })
        expect(screen.getByText('A private A-currency')).toBeVisible()
        view.unmount()
        await waitFor(() => expect(Channel.open).toHaveLength(0))
        expect(Channel.sent.every(item => item === 'changed' || item === 'logout')).toBe(true)
    })
})
