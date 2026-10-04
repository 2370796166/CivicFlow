// Synthetic credentials arrive on stdin only. Never enable traces or log QR tokens.
import { chromium, expect } from '@playwright/test'
let input = ''
for await (const chunk of process.stdin) input += chunk
const seed = JSON.parse(input)
const browser = await chromium.launch({ headless: true })
const base = 'http://localhost:5173'
const steps = []
async function login(name, destination) {
  const context = await browser.newContext()
  const page = await context.newPage()
  await page.goto(`${base}/login?redirect=${encodeURIComponent(destination)}`)
  await page.getByLabel('用户名或手机号').fill(name)
  await page.getByLabel('密码', { exact: true }).fill(seed.password)
  const response = page.waitForResponse(r => r.url().endsWith('/api/v1/auth/login') && r.request().method() === 'POST')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  const auth = (await (await response).json()).data
  steps.push({ step: 'login', destination, status: 200 })
  await expect(page).toHaveURL(base + destination)
  return { page, token: auth.accessToken }
}
try {
  const { page: admin } = await login(seed.admin, '/admin/outlets')
  await expect(admin.getByText(seed.outletName || 'E2E synthetic outlet', { exact: true })).toBeVisible()
  const { page: user, token: userToken } = await login(seed.user, `/user/appointments/${seed.appointmentId}`)
  await expect(user.getByText(seed.appointmentId, { exact: true })).toBeVisible()
  await user.getByRole('button', { name: '确认预约', exact: true }).click()
  await expect(user.getByText('已确认', { exact: true })).toBeVisible()
  steps.push({ step: 'confirm', status: 'CONFIRMED' })
  const qrResponse = user.waitForResponse(r => r.url().endsWith('/check-in-token') && r.request().method() === 'POST')
  await user.getByRole('button', { name: '获取二维码', exact: true }).click()
  const qr = (await (await qrResponse).json()).data
  await expect(user.getByAltText('签到二维码')).toBeVisible()
  steps.push({ step: 'check-in-token', status: 200, qrVisible: true })
  const { page: staff, token: staffToken } = await login(seed.staff, '/staff')
  // The deployed UI displays a QR; an external authenticated scanner submits it.
  async function scan(role, token) {
    const response = await staff.request.post(`${base}/api/v1/${role}/check-ins`, {
      headers: { Authorization: `Bearer ${token}` },
      data: { checkInToken: qr.token, outletId: seed.outletId },
    })
    expect(response.status()).toBe(200)
    steps.push({ step: `${role}/check-ins`, status: response.status() })
    return (await response.json()).data
  }
  const ticket = await scan('staff', staffToken)
  const repeated = await scan('user', userToken)
  expect(repeated.id).toBe(ticket.id)
  expect(typeof ticket.id).toBe('string')
  await user.bringToFront()
  await expect(user.getByText('排队进度', { exact: true })).toBeVisible({ timeout: 30000 })
  await expect(user.getByAltText('签到二维码')).toHaveCount(0)
  await staff.bringToFront()
  async function command(label, path) {
    await staff.getByRole('button', { name: label, exact: true }).click()
    const response = staff.waitForResponse(r => r.url().endsWith(path) && r.request().method() === 'POST')
    await staff.getByRole('button', { name: `确认${label}`, exact: true }).click()
    const result = await response
    expect(result.status()).toBe(200)
    steps.push({ step: path, status: result.status() })
    return (await result.json()).data
  }
  const work = await command('开始工作', '/work-sessions')
  const called = await command('叫下一号', '/call-next')
  expect(called.id).toBe(ticket.id)
  await command('开始办理', '/start')
  const completed = await command('完成办理', '/complete')
  expect(completed.status).toBe('COMPLETED')
  await user.bringToFront()
  await expect(user.getByText('已完成', { exact: true })).toBeVisible({ timeout: 90000 })
  await expect(user.getByText('排队进度', { exact: true })).toHaveCount(0)
  console.log(JSON.stringify({ ticketId: ticket.id, sessionId: work.id, status: completed.status, frontend: 'PASS', steps }))
} finally {
  await browser.close()
}
