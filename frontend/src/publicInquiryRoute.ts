const CANONICAL_PUBLIC_SLUG = /^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$/

export function getInquiryWorkspaceSlug(pathname: string): string | undefined | null {
  if (pathname === '/inquiry') return undefined
  const match = /^\/inquiry\/([^/]+)$/.exec(pathname)
  if (!match || !CANONICAL_PUBLIC_SLUG.test(match[1])) return null
  return match[1]
}
