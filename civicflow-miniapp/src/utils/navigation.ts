const tabs = [
  '/pages/home/index',
  '/pages/appointments/index',
  '/pages/profile/index',
]
let loginOpen = false
export function currentUrl() {
  const pages = getCurrentPages()
  const page = pages[pages.length - 1] as unknown as
    | { route?: string; options?: Record<string, string> }
    | undefined
  if (!page?.route) return '/pages/home/index'
  const params = Object.entries(page.options || {})
    .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`)
    .join('&')
  return `/${page.route}${params ? '?' + params : ''}`
}
export function go(url: string, replace = false) {
  if (tabs.includes(url.split('?')[0]!))
    uni.switchTab({ url: url.split('?')[0]! })
  else if (replace) uni.redirectTo({ url })
  else uni.navigateTo({ url })
}
export function safeReturn(url?: string) {
  return url &&
    /^\/pages\/(home|appointments|profile|outlet|booking|reservation|detail|checkin|queue|notifications|preferences|help)\/index(?:\?[^\r\n]*)?$/.test(
      url,
    )
    ? url
    : '/pages/home/index'
}
export function loginRequired() {
  if (loginOpen || currentUrl().startsWith('/pages/login/')) return
  loginOpen = true
  uni.navigateTo({
    url: '/pages/login/index?returnTo=' + encodeURIComponent(currentUrl()),
    complete: () => {
      loginOpen = false
    },
  })
}
