import { hashKey, QueryClient } from '@tanstack/react-query'
import type { AuthenticatedUser } from './auth'
import { activatePrivateRequests } from '../services/leadApi'

export function isPublicKey(key: readonly unknown[]) {
    return key[0] === 'public' || key[0] === 'public-inquiry'
}

export function removeUnscopedPrivateData(client: QueryClient) {
    const filters = { predicate: (query: { queryKey: readonly unknown[] }) => !isPublicKey(query.queryKey) }
    void client.cancelQueries(filters)
    client.removeQueries(filters)
    client.getMutationCache().clear()
}

export function createPrivateSession(user: AuthenticatedUser) {
    if (!user.id || !user.workspaceId) throw new Error('Invalid authenticated identity')
    const namespace = ['private', user.id, user.workspaceId, crypto.randomUUID()] as const
    const client = new QueryClient({ defaultOptions: { queries: {
        queryKeyHashFn: key => hashKey([namespace, key]),
        staleTime: 30_000, retry: 1, refetchOnWindowFocus: false,
    } } })
    const stopRequests = activatePrivateRequests()
    return {
        user, client, namespace,
        close() {
            stopRequests()
            void client.cancelQueries()
            client.clear()
        },
    }
}

export type PrivateSession = ReturnType<typeof createPrivateSession>
