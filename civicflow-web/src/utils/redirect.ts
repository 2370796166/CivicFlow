// Only same-origin, role-authorized paths are accepted after login.
export function safeRedirect(value: unknown, allowed: string[]): string | null {
  if (typeof value !== 'string' || !value.startsWith('/') || value.startsWith('//') || value.includes('\\')) return null
  const path = value.split(/[?#]/, 1)[0]
  return allowed.some((base) => path === base || path.startsWith(`${base}/`)) ? value : null
}
