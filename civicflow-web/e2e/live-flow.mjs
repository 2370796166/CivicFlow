// Synthetic credentials arrive on stdin only. Never enable traces or log QR tokens.
import { chromium, expect } from '@playwright/test'
let input = ''
for await (const chunk of process.stdin) input += chunk
const seed = JSON.parse(input)
const browser = await chromium.launch({ headless: true })
const base = seed.baseUrl || 'http://localhost:5173'
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
  return { page, token: auth.accessToken, userId: auth.user.id }
}
try {
  const { page: admin } = await login(seed.admin, '/admin/outlets')
  await expect(admin.getByText(seed.outletName || 'E2E synthetic outlet', { exact: true })).toBeVisible()
  await admin.getByRole('link', { name: '窗口', exact: true }).click()
  const windowRow = admin.locator('.el-table__row').filter({ hasText: seed.windowName || 'E2E window' }).first()
  await windowRow.getByRole('button', { name: '绑定事项', exact: true }).click()
  await expect(admin.getByRole('checkbox', { name: seed.itemName || 'E2E synthetic item', exact: true })).toBeChecked()
  await admin.getByRole('button', { name: '取消', exact: true }).click()
  await windowRow.getByRole('button', { name: '人员授权', exact: true }).click()
  await admin.getByPlaceholder('搜索员工用户名或显示名称').fill(seed.staff)
  await admin.getByRole('button', { name: '搜索', exact: true }).click()
  const staffChoice = admin.locator('.el-checkbox').filter({ hasText: seed.staff })
  await expect(staffChoice.getByRole('checkbox')).toBeEnabled()
  await staffChoice.click()
  await expect(staffChoice.getByRole('checkbox')).toBeChecked()
  await admin.getByRole('button', { name: '保存授权', exact: true }).click()
  const grantResponse = admin.waitForResponse(r => r.url().endsWith(`/windows/${seed.windowId}/staff`) && r.request().method() === 'PUT')
  await admin.getByRole('button', { name: '确认授权', exact: true }).click()
  expect((await grantResponse).status()).toBe(200)
  steps.push({ step: 'window-staff-ui', status: 200 })
  const destination = seed.appointmentId ? `/user/appointments/${seed.appointmentId}` : `/user/outlets/${seed.outletId}`
  const { page: user, token: userToken, userId } = await login(seed.user, destination)
  let appointmentId = seed.appointmentId
  let reservationId = null
  if (!appointmentId) {
    await user.locator(`input[type="radio"][value="${seed.itemId}"]`).check()
    await user.getByLabel('选择服务日期').fill(seed.serviceDate)
    await expect(user.getByRole('button', { name: '预约此时段', exact: true })).toBeEnabled({ timeout: 30000 })
    if (seed.screenshotDir) await user.screenshot({ path: `${seed.screenshotDir}/booking.png`, fullPage: true })
    const accepted = user.waitForResponse(r => r.url().endsWith('/appointments/reservations') && r.request().method() === 'POST')
    await user.getByRole('button', { name: '预约此时段', exact: true }).click()
    const response = await accepted
    expect(response.status()).toBe(202)
    reservationId = (await response.json()).data.reservationId
    await expect(user).toHaveURL(/\/user\/appointments\/\d+$/, { timeout: 30000 })
    appointmentId = user.url().split('/').pop()
    steps.push({ step: 'reservation-ui', status: 202 })
  }
  await expect(user.getByText(appointmentId, { exact: true })).toBeVisible()
  await user.getByRole('button', { name: '确认预约', exact: true }).click()
  await expect(user.getByText('已确认', { exact: true })).toBeVisible()
  steps.push({ step: 'confirm', status: 'CONFIRMED' })
  const qrResponse = user.waitForResponse(r => r.url().endsWith('/check-in-token') && r.request().method() === 'POST')
  await user.getByRole('button', { name: '获取二维码', exact: true }).click()
  const qr = (await (await qrResponse).json()).data
  await expect(user.getByAltText('签到二维码')).toBeVisible()
  steps.push({ step: 'check-in-token', status: 200, qrVisible: true })
  const { page: staff } = await login(seed.staff, '/staff')
  const scanResponse = staff.waitForResponse(r => r.url().endsWith('/staff/check-ins') && r.request().method() === 'POST')
  await staff.getByLabel('现场签到', { exact: true }).fill(qr.token)
  await staff.getByRole('button', { name: '确认签到', exact: true }).click()
  const signedIn = await scanResponse
  expect(signedIn.status()).toBe(200)
  const ticket = (await signedIn.json()).data
  await expect(staff.getByLabel('现场签到', { exact: true })).toHaveValue('')
  steps.push({ step: 'staff-check-in-ui', status: 200 })
  // An additional API replay checks idempotency after the primary UI flow.
  async function scan(role, token) {
    const response = await staff.request.post(`${base}/api/v1/${role}/check-ins`, {
      headers: { Authorization: `Bearer ${token}` },
      data: { checkInToken: qr.token, outletId: seed.outletId },
    })
    expect(response.status()).toBe(200)
    steps.push({ step: `${role}/check-ins`, status: response.status() })
    return (await response.json()).data
  }
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
  await admin.getByRole('link', { name: '预约查询', exact: true }).click()
  await admin.getByPlaceholder('用户编号').fill(userId)
  await admin.getByRole('button', { name: '查询预约', exact: true }).click()
  const appointmentRow = admin.locator('.el-table__row').filter({ hasText: appointmentId })
  await expect(appointmentRow).toContainText('已完成')
  await appointmentRow.getByRole('link', { name: '操作记录', exact: true }).click()
  await expect(admin.getByRole('heading', { name: '预约操作日志', exact: true })).toBeVisible()
  await expect(admin.locator('.el-table__body')).toContainText('完成办理')
  if (seed.screenshotDir) {
    await admin.screenshot({ path: `${seed.screenshotDir}/audit.png`, fullPage: true })
    await staff.screenshot({ path: `${seed.screenshotDir}/workbench.png`, fullPage: true })
  }
  steps.push({ step: 'admin-query-and-audit-ui', status: 200 })
  console.log(JSON.stringify({ appointmentId, reservationId, ticketId: ticket.id, sessionId: work.id, status: completed.status, frontend: 'PASS', steps }))
} finally {
  await browser.close()
}
