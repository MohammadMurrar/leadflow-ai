import { Component, Suspense } from 'react'
import type { ReactNode } from 'react'

interface ChunkErrorBoundaryProps {
    children: ReactNode
    mode: 'full' | 'content'
    resetKey?: string
}

interface ChunkErrorBoundaryState {
    failed: boolean
}

class ChunkErrorBoundary extends Component<ChunkErrorBoundaryProps, ChunkErrorBoundaryState> {
    state: ChunkErrorBoundaryState = { failed: false }

    static getDerivedStateFromError(): ChunkErrorBoundaryState {
        return { failed: true }
    }

    componentDidCatch() {
        // Dynamic import details are intentionally not rendered or logged.
    }

    componentDidUpdate(previousProps: ChunkErrorBoundaryProps) {
        if (this.state.failed && previousProps.resetKey !== this.props.resetKey) {
            this.setState({ failed: false })
        }
    }

    render() {
        if (!this.state.failed) return this.props.children

        return <BoundaryFrame mode={this.props.mode} error />
    }
}

function BoundaryFrame({ mode, error = false }: { mode: 'full' | 'content'; error?: boolean }) {
    const content = error ? (
        <div role="alert" className="w-full max-w-lg rounded-2xl border border-rose-100 bg-white p-6 text-center shadow-sm">
            <h1 className="text-lg font-bold text-slate-950">This workspace view could not be loaded</h1>
            <p className="mt-2 text-sm text-slate-500">The application may have been updated. Refresh to load the current version.</p>
            <button
                type="button"
                onClick={() => window.location.reload()}
                className="mt-5 rounded-xl bg-indigo-600 px-5 py-2.5 text-sm font-semibold text-white transition hover:bg-indigo-700 focus:outline-none focus:ring-4 focus:ring-indigo-200"
            >
                Refresh application
            </button>
        </div>
    ) : (
        <div role="status" className="flex items-center gap-3 text-sm font-medium text-slate-500">
            <span className="h-2.5 w-2.5 rounded-full bg-indigo-500" aria-hidden="true" />
            {mode === 'full' ? 'Loading your workspace…' : 'Loading workspace view…'}
        </div>
    )

    return mode === 'full' ? (
        <main className="flex min-h-screen items-center justify-center bg-slate-50 px-4">
            {content}
        </main>
    ) : (
        <main className="flex min-h-[calc(100vh-5rem)] items-center justify-center px-4 py-8">
            {content}
        </main>
    )
}

export default function RouteLoadBoundary({
    children,
    mode,
    resetKey,
}: {
    children: ReactNode
    mode: 'full' | 'content'
    resetKey?: string
}) {
    return (
        <ChunkErrorBoundary mode={mode} resetKey={resetKey}>
            <Suspense fallback={<BoundaryFrame mode={mode} />}>
                {children}
            </Suspense>
        </ChunkErrorBoundary>
    )
}
