import {
  Bot,
  BriefcaseBusiness,
  Clock3,
  LayoutDashboard,
  LogOut,
  Menu,
  Plus,
  Search,
  Settings,
  Sparkles,
  TrendingUp,
  Users,
  X,
} from 'lucide-react'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import axios from 'axios'
import { lazy, useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { createLead, getLeads } from './services/leadApi'
import { getDashboardStats } from './services/dashboardApi'
import type { DashboardRange } from './types/dashboard'
import type { CreateLeadRequest, Lead, QualificationState } from './types/lead'
import type { PageResponse } from './types/page'
import NotificationBell from './components/NotificationBell'
import RouteLoadBoundary from './components/RouteLoadBoundary'
import { getAnalytics } from './services/analyticsApi'
import { getActiveServices } from './services/serviceApi'
import { getWorkspaceSettings } from './services/settingsApi'
import type { LeadSort } from './components/LeadsPage'
import {
  getLocalDateString,
  normalizeLeadCreationError,
  validateLeadForm,
} from './utils/leadForm'
import type { LeadFormErrors, LeadFormField } from './utils/leadForm'
import type { InterfacePreferences, LeadPageSize } from './types/settings'
import { readInterfacePreferences } from './utils/interfacePreferences'
import { useAuth } from './auth/auth'

const navigation: Array<{
  name: string
  icon: typeof LayoutDashboard
  path?: string
  disabled?: boolean
}> = [
  { name: 'Overview', icon: LayoutDashboard, path: '/' },
  { name: 'Leads', icon: Users, path: '/leads' },
  { name: 'AI Qualification', icon: Bot, path: '/ai-qualification' },
  { name: 'Analytics', icon: TrendingUp, path: '/analytics' },
]

const OverviewPage = lazy(() => import('./components/OverviewPage'))
const LeadsPage = lazy(() => import('./components/LeadsPage'))
const AiQualificationPage = lazy(() => import('./components/AiQualificationPage'))
const AnalyticsPage = lazy(() => import('./components/AnalyticsPage'))
const ServicesPage = lazy(() => import('./components/ServicesPage'))
const SettingsPage = lazy(() => import('./components/SettingsPage'))

function getInitials(fullName: string) {
  return fullName
      .trim()
      .split(/\s+/)
      .slice(0, 2)
      .map((name) => name.charAt(0))
      .join('')
      .toUpperCase()
}

function App() {
  const { user, signOut } = useAuth()
  const [interfacePreferences, setInterfacePreferences] = useState<InterfacePreferences>(readInterfacePreferences)
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const [addLeadOpen, setAddLeadOpen] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<LeadFormErrors>({})
  const [selectedRange, setSelectedRange] = useState<DashboardRange>(interfacePreferences.defaultAnalyticsRange)
  const [analyticsRange, setAnalyticsRange] = useState<DashboardRange>(interfacePreferences.defaultAnalyticsRange)
  const [leadSearchInput, setLeadSearchInput] = useState('')
  const [debouncedLeadSearch, setDebouncedLeadSearch] = useState('')
  const [leadPage, setLeadPage] = useState(0)
  const [leadStatus, setLeadStatus] = useState<Lead['status'] | ''>('')
  const [qualificationState, setQualificationState] = useState<QualificationState | ''>(interfacePreferences.defaultQualificationState)
  const [leadSort, setLeadSort] = useState<LeadSort>(interfacePreferences.defaultLeadSort)
  const [leadPageSize, setLeadPageSize] = useState<LeadPageSize>(interfacePreferences.defaultLeadPageSize)
  const [lastSuccessfulLeadsPage, setLastSuccessfulLeadsPage] = useState<PageResponse<Lead> | null>(null)
  const formRef = useRef<HTMLFormElement>(null)
  const previousLeadStatuses = useRef<Map<string, Lead['status']>>(new Map())
  const [form, setForm] = useState({
    fullName: '',
    email: '',
    phone: '',
    company: '',
    serviceMode: 'catalog' as 'catalog' | 'custom',
    serviceId: '',
    requestedService: '',
    estimatedBudget: '',
    desiredStartDate: '',
    message: '',
  })
  const queryClient = useQueryClient()
  const location = useLocation()
  const isLeadsPage = location.pathname === '/leads'
  const isQualificationPage = location.pathname === '/ai-qualification'
  const isAnalyticsPage = location.pathname === '/analytics'
  const isServicesPage = location.pathname === '/services'
  const isSettingsPage = location.pathname === '/settings'
  const isOverviewPage = location.pathname === '/'
  const previousPath = useRef(location.pathname)

  const workspaceQuery = useQuery({
    queryKey: ['settings', 'workspace'],
    queryFn: getWorkspaceSettings,
  })

  useEffect(() => {
    if (previousPath.current === location.pathname) return
    previousPath.current = location.pathname
    const applyDefaults = window.setTimeout(() => {
      if (location.pathname === '/') setSelectedRange(interfacePreferences.defaultAnalyticsRange)
      if (location.pathname === '/analytics') setAnalyticsRange(interfacePreferences.defaultAnalyticsRange)
      if (location.pathname === '/leads') {
        setLeadSort(interfacePreferences.defaultLeadSort)
        setLeadPageSize(interfacePreferences.defaultLeadPageSize)
        setLeadPage(0)
      }
      if (location.pathname === '/ai-qualification') {
        setQualificationState(interfacePreferences.defaultQualificationState)
        setLeadPageSize(interfacePreferences.defaultLeadPageSize)
        setLeadPage(0)
      }
    }, 0)
    return () => window.clearTimeout(applyDefaults)
  }, [location.pathname, interfacePreferences])

  const activeServicesQuery = useQuery({
    queryKey: ['active-services', { page: 0, size: 200 }],
    queryFn: () => getActiveServices(undefined, 0, 200),
    enabled: addLeadOpen,
    staleTime: 30_000,
  })

  const createLeadMutation = useMutation({
    mutationFn: createLead,
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['leads'] }),
        queryClient.invalidateQueries({ queryKey: ['dashboard-stats', selectedRange] }),
        queryClient.invalidateQueries({ queryKey: ['analytics'] }),
      ])
      setAddLeadOpen(false)
      setSubmitError(null)
      setFieldErrors({})
      setForm({
        fullName: '',
        email: '',
        phone: '',
        company: '',
        serviceMode: 'catalog',
        serviceId: '',
        requestedService: '',
        estimatedBudget: '',
        desiredStartDate: '',
        message: '',
      })
    },
    onError: (mutationError) => {
      const normalized = normalizeLeadCreationError(mutationError)
      const responseMessage = axios.isAxiosError(mutationError)
          && typeof mutationError.response?.data === 'object'
          && mutationError.response.data !== null
          && 'message' in mutationError.response.data
          && typeof mutationError.response.data.message === 'string'
          ? mutationError.response.data.message : ''
      if (mutationError && axios.isAxiosError(mutationError) && mutationError.response?.status === 409
          && responseMessage.includes('no longer active')) {
        setSubmitError('The selected service is no longer active. Choose another service or use Custom / not listed.')
        setFieldErrors({ serviceId: 'Choose an active catalog service.' })
        void activeServicesQuery.refetch()
        return
      }
      setSubmitError(normalized.formError)
      setFieldErrors(normalized.fieldErrors)
      focusFirstInvalidField(normalized.fieldErrors)
    },
  })

  useEffect(() => {
    if (!addLeadOpen) return

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !createLeadMutation.isPending) {
        setAddLeadOpen(false)
        setSubmitError(null)
        setFieldErrors({})
      }
    }

    document.addEventListener('keydown', handleKeyDown)
    document.body.style.overflow = 'hidden'

    return () => {
      document.removeEventListener('keydown', handleKeyDown)
      document.body.style.overflow = ''
    }
  }, [addLeadOpen, createLeadMutation.isPending])

  useEffect(() => {
    const debounceTimer = window.setTimeout(() => {
      const normalizedSearch = leadSearchInput.trim()
      setDebouncedLeadSearch(normalizedSearch)
      setLeadPage(0)
    }, 350)

    return () => window.clearTimeout(debounceTimer)
  }, [leadSearchInput])

  function openAddLeadModal() {
    setSubmitError(null)
    setFieldErrors({})
    setAddLeadOpen(true)
  }

  function closeAddLeadModal() {
    if (createLeadMutation.isPending) return
    setSubmitError(null)
    setFieldErrors({})
    setAddLeadOpen(false)
  }

  function updateFormField(field: keyof typeof form, value: string) {
    setForm((current) => ({ ...current, [field]: value }))
    setFieldErrors((current) => {
      if (!current[field]) return current
      const next = { ...current }
      delete next[field]
      return next
    })
  }

  function focusFirstInvalidField(errors: LeadFormErrors) {
    const firstField = Object.keys(errors)[0] as LeadFormField | undefined
    if (!firstField) return
    requestAnimationFrame(() => {
      formRef.current?.querySelector<HTMLElement>(`[name="${firstField}"]`)?.focus()
    })
  }

  function handleCreateLead(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (createLeadMutation.isPending) return
    setSubmitError(null)

    const errors = validateLeadForm(form)
    if (Object.keys(errors).length > 0) {
      setFieldErrors(errors)
      focusFirstInvalidField(errors)
      return
    }
    setFieldErrors({})

    const budget = form.estimatedBudget.trim()
        ? Number(form.estimatedBudget)
        : null

    const common = {
      fullName: form.fullName.trim(),
      email: form.email.trim().toLowerCase(),
      phone: form.phone.trim() || null,
      company: form.company.trim() || null,
      estimatedBudget: budget,
      desiredStartDate: form.desiredStartDate || null,
      message: form.message.trim(),
      source: 'dashboard',
    }
    const payload: CreateLeadRequest = form.serviceMode === 'catalog'
        ? { ...common, serviceId: form.serviceId }
        : { ...common, requestedService: form.requestedService.trim() }

    createLeadMutation.mutate(payload)
  }

  const requestedLeadPage = isLeadsPage || isQualificationPage ? leadPage : 0
  const requestedLeadPageSize = isLeadsPage || isQualificationPage ? leadPageSize : 10
  const requestedLeadStatus = isLeadsPage && leadStatus ? leadStatus : undefined
  const requestedQualificationState = isQualificationPage && qualificationState
      ? qualificationState
      : undefined
  const requestedLeadSort = isLeadsPage || isQualificationPage ? leadSort : 'createdAt,desc'
  const leadQueryScope = isQualificationPage ? 'qualification' : isLeadsPage ? 'all' : 'recent'

  const {
    data: leadsPage,
    isLoading,
    isFetching: isLeadsFetching,
    isPlaceholderData: isLeadsPlaceholderData,
    isError,
    refetch: refetchLeads,
  } = useQuery({
    queryKey: [
      'leads',
      leadQueryScope,
      requestedLeadStatus ?? '',
      requestedQualificationState ?? '',
      requestedLeadPage,
      requestedLeadPageSize,
      requestedLeadSort,
      debouncedLeadSearch,
    ],
    queryFn: async () => {
      const page = await getLeads({
          page: requestedLeadPage,
          size: requestedLeadPageSize,
          status: requestedLeadStatus,
          qualificationState: requestedQualificationState,
          sort: requestedLeadSort,
          search: debouncedLeadSearch || undefined,
        })
      setLastSuccessfulLeadsPage(page)
      if ((isLeadsPage || isQualificationPage) && requestedLeadPage > 0) {
        const nearestPage = page.totalPages === 0 ? 0 : Math.min(requestedLeadPage, page.totalPages - 1)
        if (nearestPage !== requestedLeadPage) setLeadPage(nearestPage)
      }
      return page
    },
    placeholderData: keepPreviousData,
    enabled: !isAnalyticsPage && !isServicesPage && !isSettingsPage,
    refetchInterval: (query) => {
      const page = query.state.data

      return page?.content.some(
          (lead: Lead) => lead.status === 'QUALIFYING',
      )
          ? 3_000
          : false
    },
  })

  const displayedLeadsPage = leadsPage ?? lastSuccessfulLeadsPage
  const leads = displayedLeadsPage?.content ?? []
  const totalLeadCount = displayedLeadsPage?.totalElements ?? 0
  const isLeadSearchActive = debouncedLeadSearch.length > 0

  const {
    data: dashboardStats,
    isLoading: isDashboardLoading,
    isFetching: isDashboardFetching,
    isPlaceholderData: isDashboardPlaceholderData,
    isError: isDashboardError,
    refetch: refetchDashboard,
  } = useQuery({
    queryKey: ['dashboard-stats', selectedRange],
    queryFn: () => getDashboardStats(selectedRange),
    placeholderData: keepPreviousData,
    enabled: isOverviewPage,
  })

  const {
    data: qualificationSummary,
    isLoading: isQualificationSummaryLoading,
    isError: isQualificationSummaryError,
    refetch: refetchQualificationSummary,
  } = useQuery({
    queryKey: ['dashboard-stats', 'all'],
    queryFn: () => getDashboardStats('all'),
    placeholderData: keepPreviousData,
    enabled: isQualificationPage,
  })

  const {
    data: analytics,
    isLoading: isAnalyticsLoading,
    isFetching: isAnalyticsFetching,
    isPlaceholderData: isAnalyticsPlaceholderData,
    isError: isAnalyticsError,
    refetch: refetchAnalytics,
  } = useQuery({
    queryKey: ['analytics', analyticsRange],
    queryFn: () => getAnalytics(analyticsRange),
    placeholderData: keepPreviousData,
    enabled: isAnalyticsPage,
  })

  useEffect(() => {
    if (!leadsPage) return

    const previous = previousLeadStatuses.current
    const current = new Map(leadsPage.content.map((lead) => [lead.id, lead.status]))
    const visiblyCompletedLeadIds = leadsPage.content
      .filter((lead) => previous.get(lead.id) === 'QUALIFYING' && lead.status !== 'QUALIFYING')
      .map((lead) => lead.id)
    const filteredCompletedLeadIds = isQualificationPage
        && requestedQualificationState === 'PROCESSING'
      ? [...previous]
        .filter(([id, status]) => status === 'QUALIFYING' && !current.has(id))
        .map(([id]) => id)
      : []
    const completedLeadIds = [...new Set([...visiblyCompletedLeadIds, ...filteredCompletedLeadIds])]

    previousLeadStatuses.current = current

    if (completedLeadIds.length > 0) {
      void Promise.all([
        queryClient.invalidateQueries({ queryKey: ['dashboard-stats'] }),
        queryClient.invalidateQueries({ queryKey: ['analytics'] }),
        ...completedLeadIds.flatMap((id) => [
          queryClient.invalidateQueries({ queryKey: ['lead-details', id] }),
          queryClient.invalidateQueries({ queryKey: ['qualification-attempts', id] }),
        ]),
      ])
    }
  }, [isQualificationPage, leadsPage, queryClient, requestedQualificationState])

  const workspaceName = workspaceQuery.data?.workspaceName ?? 'My Workspace'
  const workspaceDescription = workspaceQuery.data?.description
      ?? `Here’s what’s happening with ${workspaceName}’s sales pipeline today.`

  return (
      <div className="min-h-screen bg-[#f6f7fb] text-slate-900">
        {sidebarOpen && (
            <button
                type="button"
                aria-label="Close sidebar"
                className="fixed inset-0 z-30 bg-slate-950/40 lg:hidden"
                onClick={() => setSidebarOpen(false)}
            />
        )}

        <aside
            className={`fixed inset-y-0 left-0 z-40 flex w-72 flex-col border-r border-slate-200 bg-white transition-transform duration-300 lg:translate-x-0 ${
                sidebarOpen ? 'translate-x-0' : '-translate-x-full'
            }`}
        >
          <div className="flex h-20 items-center justify-between border-b border-slate-100 px-6">
            <div className="flex items-center gap-3">
              <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-gradient-to-br from-indigo-600 to-violet-600 shadow-lg shadow-indigo-200">
                <Sparkles className="h-5 w-5 text-white" />
              </div>

              <div>
                <p className="text-lg font-bold tracking-tight text-slate-950">
                  LeadFlow
                  <span className="text-indigo-600"> AI</span>
                </p>
                <p className="max-w-36 truncate text-xs text-slate-400" title={workspaceName}>{workspaceName}</p>
              </div>
            </div>

            <button
                type="button"
                aria-label="Close sidebar"
                className="rounded-lg p-2 text-slate-500 hover:bg-slate-100 lg:hidden"
                onClick={() => setSidebarOpen(false)}
            >
              <X className="h-5 w-5" />
            </button>
          </div>

          <div className="flex-1 overflow-y-auto px-4 py-6">
            <p className="mb-3 px-3 text-xs font-semibold uppercase tracking-[0.16em] text-slate-400">
              Workspace
            </p>

            <nav className="space-y-1">
              {navigation.map((item) => {
                const active = item.path === location.pathname
                const content = (
                    <>
                      <item.icon className="h-[19px] w-[19px]" />
                      <span>{item.name}</span>
                      {item.name === 'Leads' && (
                          <span className="ml-auto rounded-full bg-indigo-100 px-2 py-0.5 text-xs font-semibold text-indigo-700">{totalLeadCount}</span>
                      )}
                      {item.disabled && (
                          <span className="ml-auto text-[10px] font-semibold uppercase tracking-wide text-slate-400">Coming soon</span>
                      )}
                    </>
                )

                return item.path ? (
                    <Link
                        key={item.name}
                        to={item.path}
                        onClick={() => setSidebarOpen(false)}
                        className={`flex w-full items-center gap-3 rounded-xl px-3 py-3 text-sm font-medium transition ${active ? 'bg-indigo-50 text-indigo-700' : 'text-slate-600 hover:bg-slate-50 hover:text-slate-950'}`}
                    >
                      {content}
                    </Link>
                ) : (
                    <button
                        type="button"
                        key={item.name}
                        disabled
                        aria-disabled="true"
                        title="Coming soon"
                        className="flex w-full cursor-not-allowed items-center gap-3 rounded-xl px-3 py-3 text-sm font-medium text-slate-400"
                    >
                      {content}
                    </button>
                )
              })}
            </nav>

            <p className="mb-3 mt-8 px-3 text-xs font-semibold uppercase tracking-[0.16em] text-slate-400">
              Manage
            </p>

            <nav className="space-y-1">
              <Link
                  to="/services"
                  onClick={() => setSidebarOpen(false)}
                  className={`flex w-full items-center gap-3 rounded-xl px-3 py-3 text-sm font-medium transition ${isServicesPage ? 'bg-indigo-50 text-indigo-700' : 'text-slate-600 hover:bg-slate-50 hover:text-slate-950'}`}
              >
                <BriefcaseBusiness className="h-[19px] w-[19px]" />
                Services
              </Link>

              <Link
                  to="/settings"
                  onClick={() => setSidebarOpen(false)}
                  className={`flex w-full items-center gap-3 rounded-xl px-3 py-3 text-sm font-medium transition ${isSettingsPage ? 'bg-indigo-50 text-indigo-700' : 'text-slate-600 hover:bg-slate-50 hover:text-slate-950'}`}
              >
                <Settings className="h-[19px] w-[19px]" />
                Settings
              </Link>
            </nav>

            <div className="mt-8 overflow-hidden rounded-2xl bg-gradient-to-br from-indigo-600 to-violet-700 p-5 text-white shadow-lg shadow-indigo-100">
              <div className="mb-4 flex h-9 w-9 items-center justify-center rounded-lg bg-white/15">
                <Bot className="h-5 w-5" />
              </div>

              <p className="font-semibold">Automation configuration</p>
              <p className="mt-1 text-xs leading-5 text-indigo-100">
                Qualification reliability is managed by backend configuration.
              </p>

              <div className="mt-4 flex items-center gap-2 text-xs font-medium">
                <span className="h-2 w-2 rounded-full bg-indigo-200" />
                View configuration in Settings
              </div>
            </div>
          </div>

          <div className="border-t border-slate-100 p-4">
            <div className="flex w-full items-center gap-3 rounded-xl p-2">
              <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-slate-900 text-sm font-semibold text-white">
                {getInitials(user.displayName)}
              </div>

              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-semibold text-slate-800">
                  {user.displayName}
                </p>
                <p className="truncate text-xs text-slate-400">
                  Administrator
                </p>
              </div>
              <button type="button" onClick={() => void signOut()} aria-label="Sign out" title="Sign out" className="rounded-lg p-2 text-slate-400 transition hover:bg-red-50 hover:text-red-600 focus:outline-none focus:ring-4 focus:ring-red-100">
                <LogOut className="h-4 w-4" />
              </button>
            </div>
          </div>
        </aside>

        <div className="lg:pl-72">
          <header className="sticky top-0 z-20 flex h-20 items-center border-b border-slate-200/80 bg-white/90 px-4 backdrop-blur-xl sm:px-6 lg:px-8">
            <button
                type="button"
                aria-label="Open sidebar"
                className="mr-4 rounded-lg p-2 text-slate-600 hover:bg-slate-100 lg:hidden"
                onClick={() => setSidebarOpen(true)}
            >
              <Menu className="h-5 w-5" />
            </button>

            <label className="relative hidden w-full max-w-md sm:block">
              <span className="sr-only">Search all leads</span>
              <Search className="absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />

              <input
                  type="search"
                  maxLength={100}
                  value={leadSearchInput}
                  onChange={(event) => setLeadSearchInput(event.target.value)}
                  placeholder="Search leads, companies, or services..."
                  className="h-11 w-full rounded-xl border border-slate-200 bg-slate-50 pl-10 pr-10 text-sm outline-none transition placeholder:text-slate-400 focus:border-indigo-300 focus:bg-white focus:ring-4 focus:ring-indigo-100"
              />
              {leadSearchInput && (
                  <button
                      type="button"
                      aria-label="Clear global lead search"
                      onClick={() => setLeadSearchInput('')}
                      className="absolute right-2.5 top-1/2 -translate-y-1/2 rounded-md p-1 text-slate-400 transition hover:bg-slate-200 hover:text-slate-700"
                  >
                    <X className="h-4 w-4" />
                  </button>
              )}
            </label>

            <div className="ml-auto flex items-center gap-2 sm:gap-3">
              <NotificationBell />

              <button
                  type="button"
                  onClick={openAddLeadModal}
                  className="flex h-11 items-center gap-2 rounded-xl bg-indigo-600 px-4 text-sm font-semibold text-white shadow-sm shadow-indigo-200 transition hover:bg-indigo-700"
              >
                <Plus className="h-4 w-4" />
                <span className="hidden sm:inline">Add Lead</span>
              </button>
            </div>
          </header>

          <RouteLoadBoundary mode="content" resetKey={location.pathname}>
          {isLeadsPage ? (
              <LeadsPage
                  page={displayedLeadsPage}
                  leads={leads}
                  searchInput={leadSearchInput}
                  status={leadStatus}
                  sort={leadSort}
                  isLoading={isLoading}
                  isFetching={isLeadsFetching}
                  isPlaceholderData={isLeadsPlaceholderData}
                  isError={isError}
                  onSearchChange={setLeadSearchInput}
                  onStatusChange={(status) => {
                    setLeadStatus(status)
                    setLeadPage(0)
                  }}
                  onSortChange={(sort) => {
                    setLeadSort(sort)
                    setLeadPage(0)
                  }}
                  onPageChange={setLeadPage}
                  onRetry={() => void refetchLeads()}
              />
          ) : isQualificationPage ? (
              <AiQualificationPage
                  page={displayedLeadsPage}
                  leads={leads}
                  summary={qualificationSummary}
                  searchInput={leadSearchInput}
                  qualificationState={qualificationState}
                  sort={leadSort}
                  isLoading={isLoading}
                  isFetching={isLeadsFetching}
                  isPlaceholderData={isLeadsPlaceholderData}
                  isError={isError}
                  isSummaryLoading={isQualificationSummaryLoading}
                  isSummaryError={isQualificationSummaryError}
                  onSearchChange={setLeadSearchInput}
                  onStateChange={(state) => {
                    setQualificationState(state)
                    setLeadPage(0)
                  }}
                  onSortChange={(sort) => {
                    setLeadSort(sort)
                    setLeadPage(0)
                  }}
                  onPageChange={setLeadPage}
                  onRetry={() => void refetchLeads()}
                  onSummaryRetry={() => void refetchQualificationSummary()}
              />
          ) : isAnalyticsPage ? (
              <AnalyticsPage
                  data={analytics}
                  range={analyticsRange}
                  isLoading={isAnalyticsLoading}
                  isFetching={isAnalyticsFetching}
                  isPlaceholderData={isAnalyticsPlaceholderData}
                  isError={isAnalyticsError}
                  onRangeChange={setAnalyticsRange}
                  onRetry={() => void refetchAnalytics()}
              />
          ) : isServicesPage ? (
              <ServicesPage />
          ) : isSettingsPage ? (
              <SettingsPage preferences={interfacePreferences} onPreferencesSaved={setInterfacePreferences} />
          ) : (
              <OverviewPage
                  userDisplayName={user.displayName}
                  workspaceDescription={workspaceDescription}
                  selectedRange={selectedRange}
                  setSelectedRange={setSelectedRange}
                  dashboardStats={dashboardStats}
                  isDashboardLoading={isDashboardLoading}
                  isDashboardFetching={isDashboardFetching}
                  isDashboardPlaceholderData={isDashboardPlaceholderData}
                  isDashboardError={isDashboardError}
                  refetchDashboard={() => void refetchDashboard()}
                  displayedLeadsPage={displayedLeadsPage}
                  leads={leads}
                  leadSearchInput={leadSearchInput}
                  setLeadSearchInput={setLeadSearchInput}
                  isLeadSearchActive={isLeadSearchActive}
                  isLoading={isLoading}
                  isLeadsFetching={isLeadsFetching}
                  isLeadsPlaceholderData={isLeadsPlaceholderData}
                  isError={isError}
                  refetchLeads={() => void refetchLeads()}
                  getInitials={getInitials}
              />
          )}
          </RouteLoadBoundary>
        </div>

        {addLeadOpen && (
            <div
                className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/50 p-4 backdrop-blur-sm"
                onMouseDown={(event) => {
                  if (event.target === event.currentTarget) closeAddLeadModal()
                }}
            >
              <div
                  role="dialog"
                  aria-modal="true"
                  aria-labelledby="add-lead-title"
                  className="max-h-[92vh] w-full max-w-2xl overflow-y-auto rounded-2xl bg-white shadow-2xl"
              >
                <div className="sticky top-0 z-10 flex items-start justify-between border-b border-slate-100 bg-white px-6 py-5">
                  <div>
                    <h2 id="add-lead-title" className="text-xl font-bold text-slate-950">
                      Add a new lead
                    </h2>
                    <p className="mt-1 text-sm text-slate-500">
                      The lead will be sent to the AI qualification workflow.
                    </p>
                  </div>
                  <button
                      type="button"
                      aria-label="Close add lead form"
                      disabled={createLeadMutation.isPending}
                      onClick={closeAddLeadModal}
                      className="rounded-xl p-2 text-slate-400 transition hover:bg-slate-100 hover:text-slate-700 disabled:cursor-not-allowed disabled:opacity-50"
                  >
                    <X className="h-5 w-5" />
                  </button>
                </div>

                <form ref={formRef} onSubmit={handleCreateLead} noValidate className="p-6">
                  <div className="grid gap-5 sm:grid-cols-2">
                    <label className="text-sm font-medium text-slate-700">
                      Full name <span className="text-rose-500">*</span>
                      <input
                          autoFocus
                          name="fullName"
                          maxLength={100}
                          aria-invalid={Boolean(fieldErrors.fullName)}
                          aria-describedby={fieldErrors.fullName ? 'fullName-error' : undefined}
                          value={form.fullName}
                          onChange={(event) => updateFormField('fullName', event.target.value)}
                          placeholder="e.g. Sarah Johnson"
                          className={`mt-2 h-11 w-full rounded-xl border px-3.5 text-sm outline-none transition placeholder:text-slate-400 focus:ring-4 ${fieldErrors.fullName ? 'border-rose-400 focus:border-rose-400 focus:ring-rose-100' : 'border-slate-200 focus:border-indigo-400 focus:ring-indigo-100'}`}
                      />
                      {fieldErrors.fullName && <span id="fullName-error" className="mt-1.5 block text-xs font-medium text-rose-600">{fieldErrors.fullName}</span>}
                    </label>

                    <label className="text-sm font-medium text-slate-700">
                      Email <span className="text-rose-500">*</span>
                      <input
                          name="email"
                          type="email"
                          maxLength={180}
                          aria-invalid={Boolean(fieldErrors.email)}
                          aria-describedby={fieldErrors.email ? 'email-error' : undefined}
                          value={form.email}
                          onChange={(event) => updateFormField('email', event.target.value)}
                          placeholder="sarah@company.com"
                          className={`mt-2 h-11 w-full rounded-xl border px-3.5 text-sm outline-none transition placeholder:text-slate-400 focus:ring-4 ${fieldErrors.email ? 'border-rose-400 focus:border-rose-400 focus:ring-rose-100' : 'border-slate-200 focus:border-indigo-400 focus:ring-indigo-100'}`}
                      />
                      {fieldErrors.email && <span id="email-error" className="mt-1.5 block text-xs font-medium text-rose-600">{fieldErrors.email}</span>}
                    </label>

                    <label className="text-sm font-medium text-slate-700">
                      Phone
                      <input
                          name="phone"
                          type="tel"
                          maxLength={30}
                          aria-invalid={Boolean(fieldErrors.phone)}
                          aria-describedby={fieldErrors.phone ? 'phone-error' : undefined}
                          value={form.phone}
                          onChange={(event) => updateFormField('phone', event.target.value)}
                          placeholder="+1 202 555 0147"
                          className={`mt-2 h-11 w-full rounded-xl border px-3.5 text-sm outline-none transition placeholder:text-slate-400 focus:ring-4 ${fieldErrors.phone ? 'border-rose-400 focus:border-rose-400 focus:ring-rose-100' : 'border-slate-200 focus:border-indigo-400 focus:ring-indigo-100'}`}
                      />
                      {fieldErrors.phone && <span id="phone-error" className="mt-1.5 block text-xs font-medium text-rose-600">{fieldErrors.phone}</span>}
                    </label>

                    <label className="text-sm font-medium text-slate-700">
                      Company
                      <input
                          name="company"
                          maxLength={140}
                          aria-invalid={Boolean(fieldErrors.company)}
                          aria-describedby={fieldErrors.company ? 'company-error' : undefined}
                          value={form.company}
                          onChange={(event) => updateFormField('company', event.target.value)}
                          placeholder="BrightPath Logistics"
                          className={`mt-2 h-11 w-full rounded-xl border px-3.5 text-sm outline-none transition placeholder:text-slate-400 focus:ring-4 ${fieldErrors.company ? 'border-rose-400 focus:border-rose-400 focus:ring-rose-100' : 'border-slate-200 focus:border-indigo-400 focus:ring-indigo-100'}`}
                      />
                      {fieldErrors.company && <span id="company-error" className="mt-1.5 block text-xs font-medium text-rose-600">{fieldErrors.company}</span>}
                    </label>

                    <fieldset className="sm:col-span-2">
                      <legend className="text-sm font-medium text-slate-700">Requested service <span className="text-rose-500">*</span></legend>
                      <div className="mt-2 flex flex-wrap gap-4 text-sm text-slate-600">
                        <label className="flex items-center gap-2"><input type="radio" name="serviceMode" value="catalog" checked={form.serviceMode === 'catalog'} onChange={() => setForm((current) => ({ ...current, serviceMode: 'catalog' }))} className="h-4 w-4 accent-indigo-600" />Catalog service</label>
                        <label className="flex items-center gap-2"><input type="radio" name="serviceMode" value="custom" checked={form.serviceMode === 'custom'} onChange={() => { setForm((current) => ({ ...current, serviceMode: 'custom', serviceId: '' })); setFieldErrors((current) => { const next = { ...current }; delete next.serviceId; return next }) }} className="h-4 w-4 accent-indigo-600" />Custom / not listed</label>
                      </div>
                      {form.serviceMode === 'catalog' ? <div className="mt-3">
                        {activeServicesQuery.isLoading ? <p role="status" className="rounded-xl bg-slate-50 px-4 py-3 text-sm text-slate-500">Loading active services...</p> : activeServicesQuery.isError ? <div role="alert" className="flex items-center justify-between gap-3 rounded-xl border border-rose-100 bg-rose-50 px-4 py-3 text-sm text-rose-700"><span>Active services could not be loaded.</span><button type="button" onClick={() => activeServicesQuery.refetch()} className="font-semibold underline">Retry</button></div> : <>
                          <select name="serviceId" value={form.serviceId} onChange={(event) => updateFormField('serviceId', event.target.value)} aria-invalid={Boolean(fieldErrors.serviceId)} aria-describedby={fieldErrors.serviceId ? 'serviceId-error' : activeServicesQuery.data && !activeServicesQuery.data.last ? 'service-options-bounded' : undefined} className={`h-11 w-full rounded-xl border bg-white px-3.5 text-sm outline-none focus:ring-4 ${fieldErrors.serviceId ? 'border-rose-400 focus:ring-rose-100' : 'border-slate-200 focus:ring-indigo-100'}`}><option value="">Choose an active service</option>{activeServicesQuery.data?.content.map((service) => <option key={service.id} value={service.id}>{service.name}</option>)}</select>
                          {activeServicesQuery.data?.content.length === 0 && <p className="mt-2 text-sm text-slate-500">No active catalog services are available. You can still choose Custom / not listed.</p>}
                          {activeServicesQuery.data && !activeServicesQuery.data.last && <p id="service-options-bounded" className="mt-2 text-xs text-amber-700">Showing the first 200 active services. Use Custom / not listed if the required service is not shown.</p>}
                        </>}
                        {fieldErrors.serviceId && <span id="serviceId-error" className="mt-1.5 block text-xs font-medium text-rose-600">{fieldErrors.serviceId}</span>}
                      </div> : <label className="mt-3 block"><span className="sr-only">Custom requested service</span><input name="requestedService" maxLength={120} aria-invalid={Boolean(fieldErrors.requestedService)} aria-describedby={fieldErrors.requestedService ? 'requestedService-error' : undefined} value={form.requestedService} onChange={(event) => updateFormField('requestedService', event.target.value)} placeholder="Describe the requested service" className={`h-11 w-full rounded-xl border px-3.5 text-sm outline-none transition placeholder:text-slate-400 focus:ring-4 ${fieldErrors.requestedService ? 'border-rose-400 focus:ring-rose-100' : 'border-slate-200 focus:ring-indigo-100'}`} />{fieldErrors.requestedService && <span id="requestedService-error" className="mt-1.5 block text-xs font-medium text-rose-600">{fieldErrors.requestedService}</span>}</label>}
                    </fieldset>

                    <label className="text-sm font-medium text-slate-700">
                      Estimated budget
                      <div className="relative mt-2">
                        <span className="absolute left-3.5 top-1/2 -translate-y-1/2 text-sm text-slate-400">$</span>
                        <input
                            name="estimatedBudget"
                            type="number"
                            min="0"
                            step="0.01"
                            aria-invalid={Boolean(fieldErrors.estimatedBudget)}
                            aria-describedby={fieldErrors.estimatedBudget ? 'estimatedBudget-error' : undefined}
                            value={form.estimatedBudget}
                            onChange={(event) => updateFormField('estimatedBudget', event.target.value)}
                            placeholder="4500"
                            className={`h-11 w-full rounded-xl border pl-8 pr-3.5 text-sm outline-none transition placeholder:text-slate-400 focus:ring-4 ${fieldErrors.estimatedBudget ? 'border-rose-400 focus:border-rose-400 focus:ring-rose-100' : 'border-slate-200 focus:border-indigo-400 focus:ring-indigo-100'}`}
                        />
                      </div>
                      {fieldErrors.estimatedBudget && <span id="estimatedBudget-error" className="mt-1.5 block text-xs font-medium text-rose-600">{fieldErrors.estimatedBudget}</span>}
                    </label>

                    <label className="text-sm font-medium text-slate-700">
                      Desired start date
                      <input
                          name="desiredStartDate"
                          type="date"
                          min={getLocalDateString()}
                          aria-invalid={Boolean(fieldErrors.desiredStartDate)}
                          aria-describedby={fieldErrors.desiredStartDate ? 'desiredStartDate-error' : undefined}
                          value={form.desiredStartDate}
                          onChange={(event) => updateFormField('desiredStartDate', event.target.value)}
                          className={`mt-2 h-11 w-full rounded-xl border px-3.5 text-sm text-slate-600 outline-none transition focus:ring-4 ${fieldErrors.desiredStartDate ? 'border-rose-400 focus:border-rose-400 focus:ring-rose-100' : 'border-slate-200 focus:border-indigo-400 focus:ring-indigo-100'}`}
                      />
                      {fieldErrors.desiredStartDate && <span id="desiredStartDate-error" className="mt-1.5 block text-xs font-medium text-rose-600">{fieldErrors.desiredStartDate}</span>}
                    </label>

                    <label className="text-sm font-medium text-slate-700 sm:col-span-2">
                      Message <span className="text-rose-500">*</span>
                      <textarea
                          name="message"
                          rows={4}
                          maxLength={3000}
                          aria-invalid={Boolean(fieldErrors.message)}
                          aria-describedby={fieldErrors.message ? 'message-error' : undefined}
                          value={form.message}
                          onChange={(event) => updateFormField('message', event.target.value)}
                          placeholder="Describe what the lead needs, their goals, and any relevant details..."
                          className={`mt-2 w-full resize-none rounded-xl border px-3.5 py-3 text-sm outline-none transition placeholder:text-slate-400 focus:ring-4 ${fieldErrors.message ? 'border-rose-400 focus:border-rose-400 focus:ring-rose-100' : 'border-slate-200 focus:border-indigo-400 focus:ring-indigo-100'}`}
                      />
                      {fieldErrors.message && <span id="message-error" className="mt-1.5 block text-xs font-medium text-rose-600">{fieldErrors.message}</span>}
                    </label>
                  </div>

                  {submitError && (
                      <div role="alert" className="mt-5 rounded-xl border border-rose-100 bg-rose-50 px-4 py-3 text-sm text-rose-700">
                        {submitError}
                      </div>
                  )}

                  <div className="mt-6 flex flex-col-reverse gap-3 border-t border-slate-100 pt-5 sm:flex-row sm:justify-end">
                    <button
                        type="button"
                        disabled={createLeadMutation.isPending}
                        onClick={closeAddLeadModal}
                        className="h-11 rounded-xl border border-slate-200 px-5 text-sm font-semibold text-slate-600 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50"
                    >
                      Cancel
                    </button>
                    <button
                        type="submit"
                        disabled={createLeadMutation.isPending}
                        className="flex h-11 items-center justify-center gap-2 rounded-xl bg-indigo-600 px-5 text-sm font-semibold text-white shadow-sm shadow-indigo-200 transition hover:bg-indigo-700 disabled:cursor-not-allowed disabled:opacity-60"
                    >
                      {createLeadMutation.isPending ? (
                          <>
                            <Clock3 className="h-4 w-4 animate-spin" />
                            Creating lead...
                          </>
                      ) : (
                          <>
                            <Plus className="h-4 w-4" />
                            Create lead
                          </>
                      )}
                    </button>
                  </div>
                </form>
              </div>
            </div>
        )}
      </div>
  )
}

export default App
