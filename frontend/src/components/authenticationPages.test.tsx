import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { StrictMode, type ReactElement } from 'react'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import LoginPage from './LoginPage'
import ForgotPasswordPage, { RESET_REQUEST_SUCCESS } from './ForgotPasswordPage'
import ResetPasswordPage from './ResetPasswordPage'
import { captureResetToken, clearResetToken } from '../auth/resetTokenVault'
import { classifyPasswordResetError, confirmPasswordReset, requestPasswordReset } from '../auth/anonymousAuthApi'
import { getPublicInquiryConfiguration } from '../services/publicInquiryApi'

vi.mock('../services/publicInquiryApi', () => ({ getPublicInquiryConfiguration: vi.fn().mockRejectedValue(new Error('branding unavailable')) }))
vi.mock('../auth/anonymousAuthApi', async (importOriginal) => {
  const original = await importOriginal<typeof import('../auth/anonymousAuthApi')>()
  return { ...original, requestPasswordReset: vi.fn(), confirmPasswordReset: vi.fn(), classifyPasswordResetError: vi.fn(() => 'unexpected') }
})

const requestMock = vi.mocked(requestPasswordReset)
const confirmMock = vi.mocked(confirmPasswordReset)
const classifyMock = vi.mocked(classifyPasswordResetError)
const brandingMock = vi.mocked(getPublicInquiryConfiguration)
function renderPage(page: ReactElement, strict = false) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const content = <QueryClientProvider client={queryClient}><MemoryRouter>{page}</MemoryRouter></QueryClientProvider>
  return render(strict ? <StrictMode>{content}</StrictMode> : content)
}
function deferred() { let resolve!: () => void; const promise = new Promise<void>((done) => { resolve = done }); return { promise, resolve } }

beforeEach(() => { clearResetToken(); vi.clearAllMocks(); brandingMock.mockRejectedValue(new Error('branding unavailable')); classifyMock.mockReturnValue('unexpected'); window.history.replaceState(null, '', '/login') })

describe('login page', () => {
  it('preserves submission, prevents duplicates, toggles visibility, and falls back branding', async () => {
    const pending = deferred(); const submit = vi.fn(() => pending.promise); const user = userEvent.setup()
    renderPage(<LoginPage onSubmit={submit} isPending={false} error={null} />)
    expect(screen.getAllByText('Murravo')).not.toHaveLength(0)
    expect(screen.getByText('A lead, from start to follow-up')).toBeInTheDocument()
    expect(screen.getByText('Qualification completed')).toBeInTheDocument()
    const email = screen.getByLabelText('Email address')
    expect(email).toHaveAttribute('placeholder', 'name@company.com')
    expect(email).toHaveAttribute('aria-describedby', 'login-email-hint')
    expect(email).toHaveAccessibleDescription('Use the email address assigned to your administrator account.')
    await user.type(email, 'admin@example.invalid')
    const password = screen.getByLabelText('Password'); await user.type(password, 'Secure-password-42!')
    expect(password).toHaveAttribute('placeholder', 'Your password')
    expect(password).toHaveAttribute('aria-describedby', 'login-password-hint')
    expect(password).toHaveAccessibleDescription('Enter the password for this administrator account.')
    await user.click(screen.getByRole('button', { name: 'Show password' })); expect(password).toHaveAttribute('type', 'text')
    const form = screen.getByRole('button', { name: 'Sign in securely' }).closest('form')!; fireEvent.submit(form); fireEvent.submit(form)
    expect(submit).toHaveBeenCalledTimes(1); expect(submit).toHaveBeenCalledWith({ email: 'admin@example.invalid', password: 'Secure-password-42!' })
    pending.resolve(); await waitFor(() => expect(password).toHaveValue(''))
    expect(screen.getByRole('link', { name: 'Forgot password?' })).toHaveAttribute('href', '/forgot-password')
  })

  it('focuses a generic authentication error', () => {
    renderPage(<LoginPage onSubmit={vi.fn()} isPending={false} error="Invalid email or password." />)
    expect(screen.getByRole('alert')).toHaveFocus()
  })
})

describe('forgot password page', () => {
  it('submits once and shows the exact enumeration-safe state without changing the URL', async () => {
    const pending = deferred(); requestMock.mockReturnValue(pending.promise); const user = userEvent.setup()
    renderPage(<ForgotPasswordPage />)
    const email = screen.getByLabelText('Administrator email')
    expect(email).toHaveAttribute('placeholder', 'name@company.com')
    expect(email).toHaveAttribute('aria-describedby', 'forgot-email-hint')
    expect(email).toHaveAccessibleDescription('If the account is eligible, we will send a password-reset link to this address.')
    await user.type(email, 'admin@example.invalid')
    const form = screen.getByRole('button', { name: 'Send reset instructions' }).closest('form')!; fireEvent.submit(form); fireEvent.submit(form); expect(requestMock).toHaveBeenCalledTimes(1)
    pending.resolve(); expect(await screen.findByText(RESET_REQUEST_SUCCESS)).toBeVisible(); expect(window.location.href).not.toContain('admin@example.invalid')
  })

  it('uses a fixed safe error and remains usable when branding fails', async () => {
    requestMock.mockRejectedValue(new Error('private response')); const user = userEvent.setup(); renderPage(<ForgotPasswordPage />)
    await user.type(screen.getByLabelText('Administrator email'), 'admin@example.invalid'); await user.click(screen.getByRole('button', { name: 'Send reset instructions' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Password reset could not be requested. Please try again later.'); expect(document.body).not.toHaveTextContent('private response')
  })

  it('releases its lock after a handled request failure', async () => {
    requestMock.mockRejectedValueOnce(new Error('first failure')).mockResolvedValueOnce()
    const user = userEvent.setup(); renderPage(<ForgotPasswordPage />)
    await user.type(screen.getByLabelText('Administrator email'), 'admin@example.invalid')
    await user.click(screen.getByRole('button', { name: 'Send reset instructions' }))
    expect(await screen.findByRole('alert')).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'Send reset instructions' }))
    await waitFor(() => expect(requestMock).toHaveBeenCalledTimes(2))
  })
})

describe('reset password token lifecycle', () => {
  it('captures a canonical fragment once through Strict Mode, clears the URL, and submits once', async () => {
    const token = 'A'.repeat(43); window.history.replaceState(null, '', `/reset-password#token=${token}`)
    const storage = vi.spyOn(Storage.prototype, 'setItem'); const pending = deferred(); confirmMock.mockReturnValue(pending.promise); const user = userEvent.setup()
    renderPage(<ResetPasswordPage />, true); expect(window.location.hash).toBe(''); expect(document.body).not.toHaveTextContent(token); expect(storage).not.toHaveBeenCalled()
    await user.type(screen.getByLabelText('New password'), 'New-password-42!'); await user.type(screen.getByLabelText('Confirm new password'), 'New-password-42!')
    await user.dblClick(screen.getByRole('button', { name: 'Update password' })); expect(confirmMock).toHaveBeenCalledTimes(1); expect(confirmMock).toHaveBeenCalledWith(token, 'New-password-42!')
    pending.resolve(); expect(await screen.findByRole('heading', { name: 'Password updated' })).toBeVisible(); expect(screen.queryByLabelText('New password')).not.toBeInTheDocument(); expect(captureResetToken()).toBeNull(); storage.mockRestore()
  })

  it.each(['', '#token=short', '#token=' + 'A'.repeat(42) + '=', '#token=' + 'A'.repeat(42) + '+', '#token=' + 'A'.repeat(42) + '/', '#token=' + 'A'.repeat(42) + '%20', '#token=' + 'A'.repeat(43) + '&token=' + 'B'.repeat(43), '#token=' + 'A'.repeat(43) + '&extra=1'])('rejects missing or ambiguous fragment %s', (fragment) => {
    window.history.replaceState(null, '', `/reset-password${fragment}`); renderPage(<ResetPasswordPage />)
    expect(screen.getByRole('heading', { name: 'This reset link is invalid or expired' })).toBeVisible(); expect(confirmMock).not.toHaveBeenCalled(); expect(window.location.hash).toBe('')
  })

  it.each([['invalid', 'This reset link is invalid or expired'], ['policy', 'Choose a different password that meets every security requirement.'], ['unexpected', 'Your password could not be updated. Please try again later.']] as const)('maps %s failures to a fixed safe outcome', async (kind, message) => {
    window.history.replaceState(null, '', `/reset-password#token=${'A'.repeat(43)}`); classifyMock.mockReturnValue(kind); confirmMock.mockRejectedValue(new Error('private backend body')); const user = userEvent.setup(); renderPage(<ResetPasswordPage />)
    await user.type(screen.getByLabelText('New password'), 'New-password-42!'); await user.type(screen.getByLabelText('Confirm new password'), 'New-password-42!'); await user.click(screen.getByRole('button', { name: 'Update password' }))
    if (kind === 'invalid') { expect(await screen.findByRole('heading', { name: message })).toBeVisible(); expect(captureResetToken()).toBeNull() } else { expect(await screen.findByRole('alert')).toHaveTextContent(message); expect(screen.getByRole('alert')).toHaveFocus(); expect(captureResetToken()).toBe('A'.repeat(43)) }
    expect(document.body).not.toHaveTextContent('private backend body')
  })

  it('validates mismatch and password visibility without making a request', async () => {
    window.history.replaceState(null, '', `/reset-password#token=${'A'.repeat(43)}`); const user = userEvent.setup(); renderPage(<ResetPasswordPage />)
    const password = screen.getByLabelText('New password'); await user.click(screen.getByRole('button', { name: 'Show new password' })); expect(password).toHaveAttribute('type', 'text')
    await user.type(password, 'New-password-42!'); await user.type(screen.getByLabelText('Confirm new password'), 'Different-password-42!'); await user.click(screen.getByRole('button', { name: 'Update password' }))
    expect(screen.getByRole('alert')).toHaveTextContent('Passwords do not match.'); expect(screen.getByLabelText('Confirm new password')).toHaveFocus(); expect(confirmMock).not.toHaveBeenCalled()
    expect(password).toHaveAttribute('aria-describedby', 'new-password-hint password-requirements reset-error')
    expect(screen.getByLabelText('Confirm new password')).toHaveAttribute('aria-describedby', 'confirm-password-hint reset-error')
  })

  it('references only mounted descriptions before an error exists', () => {
    window.history.replaceState(null, '', `/reset-password#token=${'A'.repeat(43)}`); renderPage(<ResetPasswordPage />)
    const password = screen.getByLabelText('New password')
    const confirmation = screen.getByLabelText('Confirm new password')
    expect(password).toHaveAttribute('placeholder', 'Create a new password')
    expect(password).toHaveAttribute('aria-describedby', 'new-password-hint password-requirements')
    expect(password).toHaveAccessibleDescription(/Use 12–128 characters.*account email or its local part.*current password/)
    expect(confirmation).toHaveAttribute('placeholder', 'Re-enter your new password')
    expect(confirmation).toHaveAttribute('aria-describedby', 'confirm-password-hint')
    expect(confirmation).toHaveAccessibleDescription('Enter the same password again.')
    expect(document.querySelectorAll('#password-requirements')).toHaveLength(1)
    expect(document.querySelectorAll('#reset-error')).toHaveLength(0)
  })
})
