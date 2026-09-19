import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import axios from 'axios'
import { AlertTriangle, BriefcaseBusiness, Clock3, Edit3, Plus, Search, X } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import {
    createService,
    deactivateService,
    getServices,
    reactivateService,
    updateService,
} from '../services/serviceApi'
import type { ServiceOffering, ServiceSort } from '../types/service'

type StatusFilter = '' | 'active' | 'inactive'

function formatDate(value: string) {
    return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
}

function safeError(error: unknown, fallback: string) {
    if (!axios.isAxiosError(error)) return fallback
    const message = typeof error.response?.data === 'object' && error.response.data !== null
        && 'message' in error.response.data && typeof error.response.data.message === 'string'
        ? error.response.data.message : ''
    if (error.response?.status === 409 && message.includes('already exists')) return 'A service with this name already exists.'
    if (error.response?.status === 409 && message.includes('changed elsewhere')) return 'This service changed elsewhere. Refresh and try again.'
    if (!error.response) return 'Could not reach the server. Check the connection and try again.'
    return fallback
}

export default function ServicesPage() {
    const queryClient = useQueryClient()
    const [searchInput, setSearchInput] = useState('')
    const [search, setSearch] = useState('')
    const [status, setStatus] = useState<StatusFilter>('')
    const [sort, setSort] = useState<ServiceSort>('name,asc')
    const [page, setPage] = useState(0)
    const [lastPage, setLastPage] = useState<Awaited<ReturnType<typeof getServices>> | null>(null)
    const [editing, setEditing] = useState<ServiceOffering | null | undefined>(undefined)
    const [confirming, setConfirming] = useState<ServiceOffering | null>(null)
    const [form, setForm] = useState({ name: '', description: '' })
    const [formError, setFormError] = useState<string | null>(null)
    const [actionError, setActionError] = useState<string | null>(null)
    const dialogRef = useRef<HTMLDivElement>(null)
    const triggerRef = useRef<HTMLButtonElement | null>(null)

    useEffect(() => {
        const timer = window.setTimeout(() => {
            setSearch(searchInput.trim())
            setPage(0)
        }, 350)
        return () => window.clearTimeout(timer)
    }, [searchInput])

    const query = useQuery({
        queryKey: ['services', { search, status, sort, page }],
        queryFn: async () => {
            const result = await getServices({
                page,
                size: 10,
                search: search || undefined,
                active: status ? status === 'active' : undefined,
                sort,
            })
            setLastPage(result)
            if (page > 0 && (result.totalPages === 0 || page >= result.totalPages)) {
                setPage(Math.max(result.totalPages - 1, 0))
            }
            return result
        },
        placeholderData: keepPreviousData,
    })

    const displayedPage = query.data ?? lastPage
    const refresh = async (activeOptions: boolean) => {
        const invalidations = [queryClient.invalidateQueries({ queryKey: ['services'] })]
        if (activeOptions) invalidations.push(queryClient.invalidateQueries({ queryKey: ['active-services'] }))
        await Promise.all(invalidations)
    }

    const saveMutation = useMutation({
        mutationFn: () => {
            const name = form.name.trim().replace(/\s+/g, ' ')
            const description = form.description.trim() || null
            if (!name) return Promise.reject(new Error('name'))
            if (name.length > 120) return Promise.reject(new Error('name-length'))
            if (description && description.length > 1000) return Promise.reject(new Error('description-length'))
            return editing
                ? updateService(editing.id, { version: editing.version, name, description })
                : createService({ name, description })
        },
        onMutate: () => setFormError(null),
        onSuccess: async () => {
            setEditing(undefined)
            await refresh(true)
            window.requestAnimationFrame(() => triggerRef.current?.focus())
        },
        onError: async (error) => {
            if (error instanceof Error && error.message === 'name') setFormError('Enter a service name.')
            else if (error instanceof Error && error.message === 'name-length') setFormError('Service name must contain 120 characters or fewer.')
            else if (error instanceof Error && error.message === 'description-length') setFormError('Description must contain 1000 characters or fewer.')
            else setFormError(safeError(error, 'Could not save the service. Please try again.'))
            if (axios.isAxiosError(error) && error.response?.status === 409) await query.refetch()
        },
    })

    const stateMutation = useMutation({
        mutationFn: ({ offering, active }: { offering: ServiceOffering; active: boolean }) => active
            ? reactivateService(offering.id, offering.version)
            : deactivateService(offering.id, offering.version),
        onMutate: () => setActionError(null),
        onSuccess: async () => {
            setConfirming(null)
            await refresh(true)
            window.requestAnimationFrame(() => triggerRef.current?.focus())
        },
        onError: async (error) => {
            setActionError(safeError(error, 'Could not update service availability. Please try again.'))
            if (axios.isAxiosError(error) && error.response?.status === 409) await query.refetch()
        },
    })

    const dialogOpen = editing !== undefined || confirming !== null
    useEffect(() => {
        if (!dialogOpen) return
        const previousOverflow = document.body.style.overflow
        document.body.style.overflow = 'hidden'
        window.requestAnimationFrame(() => dialogRef.current?.focus())
        const keydown = (event: KeyboardEvent) => {
            if (event.key === 'Escape' && !saveMutation.isPending && !stateMutation.isPending) {
                setEditing(undefined)
                setConfirming(null)
                window.requestAnimationFrame(() => triggerRef.current?.focus())
            }
            if (event.key !== 'Tab' || !dialogRef.current) return
            const focusable = Array.from(dialogRef.current.querySelectorAll<HTMLElement>('button:not([disabled]), input:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'))
            if (!focusable.length) return
            const first = focusable[0]
            const last = focusable[focusable.length - 1]
            if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus() }
            else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus() }
        }
        document.addEventListener('keydown', keydown)
        return () => { document.body.style.overflow = previousOverflow; document.removeEventListener('keydown', keydown) }
    }, [dialogOpen, saveMutation.isPending, stateMutation.isPending])

    const openCreate = (trigger: HTMLButtonElement) => {
        triggerRef.current = trigger
        setForm({ name: '', description: '' })
        setFormError(null)
        setEditing(null)
    }
    const openEdit = (offering: ServiceOffering, trigger: HTMLButtonElement) => {
        triggerRef.current = trigger
        setForm({ name: offering.name, description: offering.description ?? '' })
        setFormError(null)
        setEditing(offering)
    }
    const hasControls = Boolean(searchInput || status || sort !== 'name,asc')
    const serviceActions = (offering: ServiceOffering, mobile = false) => <div className={mobile ? 'flex flex-wrap gap-2' : 'flex justify-end gap-2'}>
        <button type="button" onClick={(event) => openEdit(offering, event.currentTarget)} className={mobile ? 'flex min-h-11 items-center justify-center gap-2 rounded-lg border border-slate-200 px-4 text-sm font-semibold text-slate-700 hover:bg-slate-50' : 'rounded-lg border border-slate-200 p-2 text-slate-600 hover:bg-slate-50'} aria-label={`Edit ${offering.name}`}><Edit3 className="h-4 w-4" />{mobile && 'Edit'}</button>
        {offering.active
            ? <button type="button" onClick={(event) => { triggerRef.current = event.currentTarget; setActionError(null); setConfirming(offering) }} className={`service-action service-action--danger ${mobile ? 'min-h-11 flex-1 text-sm' : ''}`}>Deactivate</button>
            : <button type="button" disabled={stateMutation.isPending} onClick={() => stateMutation.mutate({ offering, active: true })} className={`service-action service-action--success ${mobile ? 'min-h-11 flex-1 text-sm' : ''}`}>Reactivate</button>}
    </div>

    return <main className="min-w-0 px-4 py-7 sm:px-6 lg:px-8">
        <section className="mb-7 flex flex-col justify-between gap-4 sm:flex-row sm:items-end">
            <div><p className="text-sm font-semibold text-primary">Catalog management</p><h1 className="mt-1 text-2xl font-bold tracking-tight text-slate-950 sm:text-3xl">Services</h1><p className="mt-2 max-w-2xl text-sm text-slate-500">Manage the services available for new leads while preserving every historical lead snapshot.</p></div>
            <button type="button" onClick={(event) => openCreate(event.currentTarget)} className="flex h-11 items-center justify-center gap-2 rounded-xl bg-primary px-4 text-sm font-semibold text-white shadow-sm shadow-primary-200 hover:bg-primary-700 focus:outline-none focus:ring-4 focus:ring-primary-200"><Plus className="h-4 w-4" />Create Service</button>
        </section>

        <section className="overflow-hidden rounded-2xl border border-slate-200/80 bg-white shadow-sm" aria-busy={query.isFetching}>
            <div className="border-b border-slate-100 p-5 sm:p-6"><div className="flex flex-col gap-3 lg:flex-row lg:items-center">
                <label className="relative flex-1"><span className="sr-only">Search services</span><Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" /><input type="search" maxLength={100} value={searchInput} onChange={(event) => setSearchInput(event.target.value)} placeholder="Search name or description..." className="h-10 w-full rounded-xl border border-slate-200 pl-9 pr-9 text-sm outline-none focus:border-primary-300 focus:ring-4 focus:ring-primary-100" />{searchInput && <button type="button" aria-label="Clear service search" onClick={() => setSearchInput('')} className="absolute right-2 top-1/2 -translate-y-1/2 rounded p-1 text-slate-400 hover:bg-slate-100"><X className="h-4 w-4" /></button>}</label>
                <label><span className="sr-only">Filter services by status</span><select value={status} onChange={(event) => { setStatus(event.target.value as StatusFilter); setPage(0) }} className="h-10 rounded-xl border border-slate-200 bg-white px-3 text-sm text-slate-600 outline-none focus:ring-4 focus:ring-primary-100"><option value="">All statuses</option><option value="active">Active</option><option value="inactive">Inactive</option></select></label>
                <label><span className="sr-only">Sort services</span><select value={sort} onChange={(event) => { setSort(event.target.value as ServiceSort); setPage(0) }} className="h-10 rounded-xl border border-slate-200 bg-white px-3 text-sm text-slate-600 outline-none focus:ring-4 focus:ring-primary-100"><option value="name,asc">Name A–Z</option><option value="name,desc">Name Z–A</option><option value="createdAt,desc">Newest</option><option value="createdAt,asc">Oldest</option></select></label>
                {hasControls && <button type="button" onClick={() => { setSearchInput(''); setStatus(''); setSort('name,asc'); setPage(0) }} className="h-10 text-sm font-semibold text-primary">Clear filters</button>}
            </div><span className="mt-3 block h-4 text-xs font-medium text-primary" role="status" aria-live="polite">{query.isFetching && !query.isLoading ? 'Updating services...' : ''}</span></div>

            {actionError && <div role="alert" className="mx-5 mt-5 flex items-center justify-between gap-3 rounded-xl border border-rose-100 bg-rose-50 p-4 text-sm text-rose-700"><span>{actionError}</span><button type="button" onClick={() => query.refetch()} className="font-semibold underline">Retry</button></div>}
            {query.isLoading && !displayedPage && <div className="px-6 py-16 text-center"><Clock3 className="mx-auto h-7 w-7 animate-spin text-primary-500" /><p className="mt-3 text-sm text-slate-500">Loading services...</p></div>}
            {query.isError && !displayedPage && <div className="px-6 py-16 text-center"><AlertTriangle className="mx-auto h-7 w-7 text-rose-500" /><p className="mt-3 text-sm text-slate-600">Services could not be loaded.</p><button type="button" onClick={() => query.refetch()} className="mt-4 rounded-xl bg-primary px-4 py-2 text-sm font-semibold text-white">Retry</button></div>}
            {!query.isLoading && displayedPage?.content.length === 0 && <div className="px-6 py-16 text-center"><BriefcaseBusiness className="mx-auto h-9 w-9 text-slate-300" /><h2 className="mt-3 font-semibold text-slate-800">{hasControls ? 'No services match these controls' : 'No services yet'}</h2><p className="mt-1 text-sm text-slate-500">{hasControls ? 'Try a different search or status.' : 'Create the first catalog service when you are ready.'}</p></div>}
            {displayedPage && displayedPage.content.length > 0 && <>
                <div className="divide-y divide-slate-100 md:hidden" aria-label="Service records">
                    {displayedPage.content.map((offering) => <article key={offering.id} aria-label={`Service ${offering.name}`} className="min-w-0 px-4 py-4">
                        <div className="flex flex-wrap items-start justify-between gap-2"><h3 className="min-w-0 break-words font-semibold text-slate-800">{offering.name}</h3><span className={`service-status ${offering.active ? 'service-status--active' : 'service-status--inactive'}`}>{offering.active ? 'Active' : 'Inactive'}</span></div>
                        <p className="mt-2 whitespace-pre-wrap break-words text-sm text-slate-600">{offering.description ?? 'No description'}</p>
                        <p className="mt-3 text-xs text-slate-500">Updated {formatDate(offering.updatedAt)}</p>
                        <div className="mt-4 border-t border-slate-100 pt-3">{serviceActions(offering, true)}</div>
                    </article>)}
                </div>
                <div className="hidden overflow-x-auto md:block"><table className="w-full min-w-[850px] text-left"><thead><tr className="border-b border-slate-100 bg-slate-50/60 text-xs font-semibold uppercase tracking-wider text-slate-400"><th className="px-6 py-4">Service</th><th className="px-6 py-4">Status</th><th className="px-6 py-4">Created</th><th className="px-6 py-4">Updated</th><th className="relative px-6 py-4"><span className="sr-only">Actions</span></th></tr></thead><tbody className="divide-y divide-slate-100">{displayedPage.content.map((offering) => <tr key={offering.id} className="hover:bg-slate-50/60"><td className="px-6 py-4"><p className="font-semibold text-slate-800">{offering.name}</p><p className="mt-1 max-w-xl whitespace-pre-wrap text-sm text-slate-500">{offering.description ?? 'No description'}</p></td><td className="px-6 py-4"><span className={`service-status ${offering.active ? 'service-status--active' : 'service-status--inactive'}`}>{offering.active ? 'Active' : 'Inactive'}</span></td><td className="px-6 py-4 text-sm text-slate-500">{formatDate(offering.createdAt)}</td><td className="px-6 py-4 text-sm text-slate-500">{formatDate(offering.updatedAt)}</td><td className="px-6 py-4">{serviceActions(offering)}</td></tr>)}</tbody></table></div>
            </>}
            {displayedPage && (displayedPage.totalElements > 0 || hasControls) && <div className="flex flex-col gap-3 border-t border-slate-100 px-5 py-4 text-sm sm:flex-row sm:items-center sm:justify-between"><p className="text-slate-500">{displayedPage.totalElements.toLocaleString()} {displayedPage.totalElements === 1 ? 'service' : 'services'}</p><div className="flex items-center gap-3"><button type="button" disabled={displayedPage.first} onClick={() => setPage(page - 1)} className="h-9 rounded-lg border border-slate-200 px-3 disabled:opacity-40">Previous</button><span className="min-w-24 text-center text-slate-500">Page {displayedPage.totalPages === 0 ? 0 : displayedPage.number + 1} of {displayedPage.totalPages}</span><button type="button" disabled={displayedPage.last || displayedPage.totalPages === 0} onClick={() => setPage(page + 1)} className="h-9 rounded-lg border border-slate-200 px-3 disabled:opacity-40">Next</button></div></div>}
        </section>

        {editing !== undefined && <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/50 p-4 backdrop-blur-sm"><div ref={dialogRef} tabIndex={-1} role="dialog" aria-modal="true" aria-labelledby="service-form-title" className="w-full max-w-lg rounded-2xl bg-white p-6 shadow-2xl outline-none"><div className="flex items-start justify-between"><div><h2 id="service-form-title" className="text-xl font-bold text-slate-950">{editing ? 'Edit service' : 'Create service'}</h2>{editing && <p className="mt-1 text-sm text-slate-500">Existing leads keep their original requested-service value.</p>}</div><button type="button" aria-label="Close service form" disabled={saveMutation.isPending} onClick={() => setEditing(undefined)} className="rounded-lg p-2 text-slate-400 hover:bg-slate-100"><X className="h-5 w-5" /></button></div><form onSubmit={(event) => { event.preventDefault(); if (!saveMutation.isPending) saveMutation.mutate() }} className="mt-5"><label className="block text-sm font-medium text-slate-700">Name <span className="text-rose-500">*</span><input autoFocus maxLength={500} value={form.name} onChange={(event) => setForm((current) => ({ ...current, name: event.target.value }))} className="mt-2 h-11 w-full rounded-xl border border-slate-200 px-3 text-sm outline-none focus:ring-4 focus:ring-primary-100" /></label><label className="mt-4 block text-sm font-medium text-slate-700">Description <span className="text-slate-400">(optional)</span><textarea rows={5} maxLength={1000} value={form.description} onChange={(event) => setForm((current) => ({ ...current, description: event.target.value }))} className="mt-2 w-full resize-none rounded-xl border border-slate-200 px-3 py-2 text-sm outline-none focus:ring-4 focus:ring-primary-100" /></label>{formError && <p role="alert" className="mt-4 rounded-xl bg-rose-50 p-3 text-sm text-rose-700">{formError}</p>}<div className="mt-6 flex justify-end gap-3"><button type="button" disabled={saveMutation.isPending} onClick={() => setEditing(undefined)} className="h-10 rounded-xl border border-slate-200 px-4 text-sm font-semibold text-slate-600">Cancel</button><button type="submit" disabled={saveMutation.isPending} className="h-10 rounded-xl bg-primary px-4 text-sm font-semibold text-white disabled:opacity-60">{saveMutation.isPending ? 'Saving...' : 'Save service'}</button></div></form></div></div>}
        {confirming && <div className="fixed inset-0 z-[60] flex items-center justify-center bg-slate-950/50 p-4 backdrop-blur-sm"><div ref={dialogRef} tabIndex={-1} role="alertdialog" aria-modal="true" aria-labelledby="deactivate-title" className="w-full max-w-md rounded-2xl bg-white p-6 shadow-2xl outline-none"><h2 id="deactivate-title" className="text-lg font-bold text-slate-950">Deactivate {confirming.name}?</h2><p className="mt-3 text-sm leading-6 text-slate-600">This service will no longer be available for new catalog-linked leads. Existing leads remain unchanged, and the service can be reactivated later.</p><div className="mt-6 flex justify-end gap-3"><button type="button" disabled={stateMutation.isPending} onClick={() => setConfirming(null)} className="h-10 rounded-xl border border-slate-200 px-4 text-sm font-semibold text-slate-600">Cancel</button><button type="button" disabled={stateMutation.isPending} onClick={() => stateMutation.mutate({ offering: confirming, active: false })} className="service-action service-action--danger h-10 px-4 text-sm">{stateMutation.isPending ? 'Deactivating...' : 'Deactivate'}</button></div></div></div>}
    </main>
}
