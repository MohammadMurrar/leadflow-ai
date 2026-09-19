import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery } from '@tanstack/react-query'
import { CheckCircle2, Send, ShieldCheck, Workflow } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import type { FieldError } from 'react-hook-form'
import {
    getPublicInquiryConfiguration,
    submitPublicInquiry,
} from '../services/publicInquiryApi'
import {
    buildPublicLeadRequest,
    getLocalDateString,
    normalizePublicInquiryError,
    publicInquiryFormSchema,
} from '../utils/publicInquiryForm'
import type {
    PublicLeadRequest,
} from '../types/publicInquiry'
import type {
    PublicInquiryFormField,
    PublicInquiryFormValues,
    ValidatedPublicInquiryForm,
} from '../utils/publicInquiryForm'

const defaultValues: PublicInquiryFormValues = {
    fullName: '',
    email: '',
    phone: '',
    company: '',
    serviceId: '',
    estimatedBudget: '',
    desiredStartDate: '',
    message: '',
    website: '',
}

const inputClass = 'mt-2 h-12 w-full rounded-xl border border-slate-200 bg-white px-3.5 text-base text-slate-900 outline-none transition placeholder:text-slate-400 focus:border-primary-500 focus:ring-4 focus:ring-primary-100 disabled:cursor-not-allowed disabled:bg-slate-100 disabled:text-slate-500'
const invalidInputClass = 'border-rose-400 focus:border-rose-500 focus:ring-rose-100'

function ErrorText({ id, error }: { id: string; error?: FieldError }) {
    if (!error?.message) return null
    return <p id={id} className="mt-1.5 text-sm font-medium text-rose-700">{error.message}</p>
}

export default function InquiryPage({ workspaceSlug }: { workspaceSlug?: string | null }) {
    const [submitted, setSubmitted] = useState(false)
    const [formError, setFormError] = useState<string | null>(null)
    const formAlertRef = useRef<HTMLDivElement>(null)
    const successHeadingRef = useRef<HTMLHeadingElement>(null)

    const configuration = useQuery({
        queryKey: ['public-inquiry', 'configuration', workspaceSlug ?? 'legacy'],
        queryFn: ({ signal }) => getPublicInquiryConfiguration(workspaceSlug ?? undefined, signal),
        enabled: workspaceSlug !== null,
        retry: 1,
    })

    const {
        register,
        handleSubmit,
        control,
        setError,
        clearErrors,
        formState: { errors },
    } = useForm<PublicInquiryFormValues, unknown, ValidatedPublicInquiryForm>({
        resolver: zodResolver(publicInquiryFormSchema),
        defaultValues,
        shouldFocusError: true,
    })

    const submission = useMutation({
        mutationFn: (request: PublicLeadRequest) => submitPublicInquiry(request, workspaceSlug ?? undefined),
        retry: false,
        onSuccess: () => {
            setFormError(null)
            setSubmitted(true)
        },
        onError: (error: unknown) => {
            const normalized = normalizePublicInquiryError(error)
            const visibleFieldErrors = Object.entries(normalized.fieldErrors)
                .filter(([field]) => field !== 'website') as Array<[PublicInquiryFormField, string]>

            visibleFieldErrors.forEach(([field, message], index) => {
                setError(field, { type: 'server', message }, { shouldFocus: index === 0 })
            })

            const safeFormError = normalized.formError
                ?? (visibleFieldErrors.length === 0
                    ? 'Some details were not accepted. Review the form and try again.'
                    : null)
            setFormError(safeFormError)
        },
    })

    useEffect(() => {
        if (formError) formAlertRef.current?.focus()
    }, [formError])

    useEffect(() => {
        if (submitted) successHeadingRef.current?.focus()
    }, [submitted])

    const services = configuration.data?.services ?? []
    const canSubmit = configuration.isSuccess && services.length > 0
    const messageLength = useWatch({ control, name: 'message' }).length

    function beginSubmission() {
        setFormError(null)
        clearErrors()
        submission.reset()
    }

    function submit(values: ValidatedPublicInquiryForm) {
        submission.mutate(buildPublicLeadRequest(values))
    }

    return (
        <main className="min-h-screen overflow-x-hidden bg-slate-50 text-slate-900">
            <div className="pointer-events-none fixed inset-x-0 top-0 h-80 bg-gradient-to-br from-primary-100/80 via-primary-50 to-transparent" aria-hidden="true" />
            <div className="relative mx-auto w-full max-w-6xl px-4 py-8 sm:px-6 sm:py-12 lg:px-8 lg:py-16">
                <header className="mx-auto max-w-3xl text-center">
                    <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-2xl bg-primary text-white shadow-lg shadow-primary-200" aria-hidden="true">
                        <Workflow className="h-6 w-6" />
                    </div>
                    <p className="mt-5 text-sm font-bold uppercase tracking-[0.18em] text-primary-700">Start a conversation</p>
                    <h1 className="mt-3 text-3xl font-black tracking-tight text-slate-950 sm:text-4xl lg:text-5xl">
                        Tell us what you’re building
                    </h1>
                    <p className="mx-auto mt-4 max-w-2xl text-base leading-7 text-slate-600 sm:text-lg">
                        Share a few details about your goals. We’ll review your inquiry and follow up with the right next step.
                    </p>
                </header>

                <section className="mx-auto mt-8 grid max-w-5xl gap-6 lg:mt-12 lg:grid-cols-[minmax(0,0.72fr)_minmax(0,1.5fr)] lg:items-start">
                    <aside className="inquiry-brand-panel rounded-3xl border border-primary-100 bg-gradient-to-br from-primary-700 to-primary-700 p-6 text-white shadow-xl shadow-primary-100 sm:p-8">
                        {configuration.isPending && workspaceSlug !== null ? (
                            <div role="status" className="flex items-center gap-3 text-sm font-medium text-primary-100">
                                <span className="h-2.5 w-2.5 animate-pulse rounded-full bg-white" aria-hidden="true" />
                                Loading inquiry details…
                            </div>
                        ) : workspaceSlug === null || configuration.isError || !configuration.data ? (
                            <div role="alert">
                                <h2 className="text-xl font-bold">Inquiry details are unavailable</h2>
                                <p className="mt-2 text-sm leading-6 text-primary-100">We couldn’t load the available services. Please try again.</p>
                                {workspaceSlug !== null && (
                                    <button
                                        type="button"
                                        onClick={() => void configuration.refetch()}
                                        className="mt-5 min-h-11 rounded-xl bg-white px-5 py-2.5 text-sm font-bold text-primary-700 transition hover:bg-primary-50 focus:outline-none focus:ring-4 focus:ring-white/40"
                                    >
                                        Retry
                                    </button>
                                )}
                            </div>
                        ) : (
                            <>
                                <h2 className="text-2xl font-bold">{configuration.data.workspaceName}</h2>
                                {configuration.data.description && (
                                    <p className="mt-3 whitespace-pre-line text-sm leading-6 text-primary-100">{configuration.data.description}</p>
                                )}
                                <div className="mt-7 border-t border-white/20 pt-6">
                                    <div className="flex items-center gap-2 text-sm font-bold">
                                        <ShieldCheck className="h-5 w-5" aria-hidden="true" />
                                        Your details are submitted securely
                                    </div>
                                    <p className="mt-2 text-sm leading-6 text-primary-100">Only include information relevant to your inquiry.</p>
                                </div>
                            </>
                        )}
                    </aside>

                    <div className="min-w-0 rounded-3xl border border-slate-200 bg-white p-5 shadow-xl shadow-slate-200/60 sm:p-8">
                        {submitted ? (
                            <div role="status" aria-live="polite" className="flex min-h-96 flex-col items-center justify-center text-center">
                                <div className="flex h-16 w-16 items-center justify-center rounded-full bg-emerald-100 text-emerald-700" aria-hidden="true">
                                    <CheckCircle2 className="h-9 w-9" />
                                </div>
                                <h2 ref={successHeadingRef} tabIndex={-1} className="mt-6 text-2xl font-black text-slate-950 outline-none sm:text-3xl">
                                    Thank you. Your inquiry has been received.
                                </h2>
                                <p className="mt-3 max-w-md text-base leading-7 text-slate-600">Your details were submitted successfully. The team will review them and determine the next step.</p>
                            </div>
                        ) : (
                            <>
                                <div>
                                    <h2 className="text-2xl font-black text-slate-950">Inquiry details</h2>
                                    <p className="mt-2 text-sm leading-6 text-slate-600">Fields marked with an asterisk are required.</p>
                                </div>

                                {configuration.isSuccess && services.length === 0 && (
                                    <div role="alert" className="mt-6 rounded-2xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm leading-6 text-amber-900">
                                        Inquiries are temporarily unavailable because there are no services available to select.
                                    </div>
                                )}

                                <form
                                    noValidate
                                    onSubmit={(event) => {
                                        if (submission.isPending) {
                                            event.preventDefault()
                                            return
                                        }
                                        beginSubmission()
                                        void handleSubmit(submit)(event)
                                    }}
                                    className="mt-7"
                                >
                                    <div aria-hidden="true" className="fixed left-[-10000px] top-auto h-px w-px overflow-hidden">
                                        <label htmlFor="website">Website</label>
                                        <input
                                            id="website"
                                            type="text"
                                            tabIndex={-1}
                                            aria-hidden="true"
                                            autoComplete="off"
                                            maxLength={200}
                                            {...register('website')}
                                        />
                                    </div>

                                    <div className="grid gap-5 sm:grid-cols-2">
                                        <div>
                                            <label htmlFor="fullName" className="text-sm font-bold text-slate-700">Full name <span className="text-rose-600" aria-hidden="true">*</span></label>
                                            <input id="fullName" type="text" required autoComplete="name" maxLength={100} aria-invalid={Boolean(errors.fullName)} aria-describedby={errors.fullName ? 'fullName-error' : 'fullName-help'} className={`${inputClass} ${errors.fullName ? invalidInputClass : ''}`} placeholder="Your full name" {...register('fullName')} />
                                            <p id="fullName-help" className="mt-1.5 text-xs text-slate-500">How should we address you?</p>
                                            <ErrorText id="fullName-error" error={errors.fullName} />
                                        </div>

                                        <div>
                                            <label htmlFor="email" className="text-sm font-bold text-slate-700">Email <span className="text-rose-600" aria-hidden="true">*</span></label>
                                            <input id="email" type="email" inputMode="email" required autoComplete="email" maxLength={180} aria-invalid={Boolean(errors.email)} aria-describedby={errors.email ? 'email-error' : 'email-help'} className={`${inputClass} ${errors.email ? invalidInputClass : ''}`} placeholder="you@company.com" {...register('email')} />
                                            <p id="email-help" className="mt-1.5 text-xs text-slate-500">We’ll use this address to respond.</p>
                                            <ErrorText id="email-error" error={errors.email} />
                                        </div>

                                        <div>
                                            <label htmlFor="phone" className="text-sm font-bold text-slate-700">Phone <span className="font-normal text-slate-400">(optional)</span></label>
                                            <input id="phone" type="tel" inputMode="tel" autoComplete="tel" maxLength={30} aria-invalid={Boolean(errors.phone)} aria-describedby={errors.phone ? 'phone-error' : undefined} className={`${inputClass} ${errors.phone ? invalidInputClass : ''}`} placeholder="+1 202 555 0147" {...register('phone')} />
                                            <ErrorText id="phone-error" error={errors.phone} />
                                        </div>

                                        <div>
                                            <label htmlFor="company" className="text-sm font-bold text-slate-700">Company <span className="font-normal text-slate-400">(optional)</span></label>
                                            <input id="company" type="text" autoComplete="organization" maxLength={140} aria-invalid={Boolean(errors.company)} aria-describedby={errors.company ? 'company-error' : undefined} className={`${inputClass} ${errors.company ? invalidInputClass : ''}`} placeholder="Your organization" {...register('company')} />
                                            <ErrorText id="company-error" error={errors.company} />
                                        </div>

                                        <div className="sm:col-span-2">
                                            <label htmlFor="serviceId" className="text-sm font-bold text-slate-700">Service <span className="text-rose-600" aria-hidden="true">*</span></label>
                                            <select id="serviceId" required disabled={!canSubmit || submission.isPending} aria-invalid={Boolean(errors.serviceId)} aria-describedby={errors.serviceId ? 'serviceId-error' : 'serviceId-help'} className={`${inputClass} ${errors.serviceId ? invalidInputClass : ''}`} {...register('serviceId')}>
                                                <option value="">Choose a service</option>
                                                {services.map((service) => <option key={service.id} value={service.id}>{service.name}</option>)}
                                            </select>
                                            <p id="serviceId-help" className="mt-1.5 text-xs text-slate-500">Select the service that best matches your inquiry.</p>
                                            <ErrorText id="serviceId-error" error={errors.serviceId} />
                                        </div>

                                        <div>
                                            <label htmlFor="estimatedBudget" className="text-sm font-bold text-slate-700">Estimated budget{configuration.data ? ` (${configuration.data.currency})` : ''} <span className="font-normal text-slate-400">(optional)</span></label>
                                            <input id="estimatedBudget" type="text" inputMode="decimal" autoComplete="off" maxLength={13} aria-invalid={Boolean(errors.estimatedBudget)} aria-describedby={errors.estimatedBudget ? 'estimatedBudget-error' : 'estimatedBudget-help'} className={`${inputClass} ${errors.estimatedBudget ? invalidInputClass : ''}`} placeholder="5000.00" {...register('estimatedBudget')} />
                                            <p id="estimatedBudget-help" className="mt-1.5 text-xs text-slate-500">Enter an amount without currency symbols.</p>
                                            <ErrorText id="estimatedBudget-error" error={errors.estimatedBudget} />
                                        </div>

                                        <div>
                                            <label htmlFor="desiredStartDate" className="text-sm font-bold text-slate-700">Desired start date <span className="font-normal text-slate-400">(optional)</span></label>
                                            <input id="desiredStartDate" type="date" min={getLocalDateString()} autoComplete="off" aria-invalid={Boolean(errors.desiredStartDate)} aria-describedby={errors.desiredStartDate ? 'desiredStartDate-error' : undefined} className={`${inputClass} ${errors.desiredStartDate ? invalidInputClass : ''}`} {...register('desiredStartDate')} />
                                            <ErrorText id="desiredStartDate-error" error={errors.desiredStartDate} />
                                        </div>

                                        <div className="sm:col-span-2">
                                            <div className="flex items-end justify-between gap-4">
                                                <label htmlFor="message" className="text-sm font-bold text-slate-700">Inquiry message <span className="text-rose-600" aria-hidden="true">*</span></label>
                                                <span className="text-xs tabular-nums text-slate-500">{messageLength}/3000</span>
                                            </div>
                                            <textarea id="message" rows={6} required maxLength={3000} aria-invalid={Boolean(errors.message)} aria-describedby={errors.message ? 'message-error' : 'message-help'} className={`mt-2 w-full resize-y rounded-xl border border-slate-200 bg-white px-3.5 py-3 text-base text-slate-900 outline-none transition placeholder:text-slate-400 focus:border-primary-500 focus:ring-4 focus:ring-primary-100 ${errors.message ? invalidInputClass : ''}`} placeholder="Tell us about your goals, requirements, and timeline." {...register('message')} />
                                            <p id="message-help" className="mt-1.5 text-xs text-slate-500">Include enough detail for us to understand what you need.</p>
                                            <ErrorText id="message-error" error={errors.message} />
                                        </div>
                                    </div>

                                    {formError && (
                                        <div ref={formAlertRef} tabIndex={-1} role="alert" className="mt-6 rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm font-medium leading-6 text-rose-800 outline-none focus:ring-4 focus:ring-rose-100">
                                            {formError}
                                        </div>
                                    )}

                                    <button
                                        type="submit"
                                        disabled={!canSubmit || submission.isPending}
                                        className="mt-7 flex min-h-12 w-full items-center justify-center gap-2 rounded-xl bg-primary px-5 py-3 text-base font-bold text-white shadow-lg shadow-primary-200 transition hover:bg-primary-700 focus:outline-none focus:ring-4 focus:ring-primary-200 disabled:cursor-not-allowed disabled:bg-slate-300 disabled:shadow-none sm:w-auto sm:min-w-48"
                                    >
                                        {submission.isPending ? 'Sending…' : <><Send className="h-5 w-5" aria-hidden="true" /> Send inquiry</>}
                                    </button>
                                </form>
                            </>
                        )}
                    </div>
                </section>
            </div>
        </main>
    )
}
