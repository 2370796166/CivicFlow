import { createRequire } from 'node:module'
import { readFileSync, existsSync, writeFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { URL } from 'node:url'
const webRequire = createRequire(
  new URL('../../civicflow-web/package.json', import.meta.url),
)
const { chromium, expect } = webRequire('@playwright/test')
const fixture = JSON.parse(readFileSync(process.argv[2], 'utf8'))
const ticketId = process.argv[3]
const defaultBrowser = existsSync(chromium.executablePath())
const edgeAvailable = existsSync(
  'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
)
const browser = await chromium.launch({
  headless: true,
  ...(!defaultBrowser ? { channel: edgeAvailable ? 'msedge' : 'chrome' } : {}),
})
const output = dirname(resolve(process.argv[2]))
try {
  const page = await browser.newPage()
  await page.goto(fixture.webBase + '/login?redirect=%2Fstaff')
  await page.getByLabel('用户名或手机号').fill(fixture.users.staff.name)
  await page.getByLabel('密码', { exact: true }).fill(fixture.password)
  const loginResponse = page.waitForResponse(
    (r) =>
      r.url().endsWith('/api/v1/auth/login') && r.request().method() === 'POST',
  )
  await page.getByRole('button', { name: '登录', exact: true }).click()
  expect((await loginResponse).status()).toBe(200)
  await expect(page).toHaveURL(fixture.webBase + '/staff')
  const steps = []
  for (const [label, suffix] of [
    ['开始工作', '/work-sessions'],
    ['叫下一号', '/call-next'],
    ['开始办理', '/start'],
    ['完成办理', '/complete'],
  ]) {
    await page.getByRole('button', { name: label, exact: true }).click()
    const response = page.waitForResponse(
      (r) => r.url().endsWith(suffix) && r.request().method() === 'POST',
    )
    await page
      .getByRole('button', { name: '确认' + label, exact: true })
      .click()
    const r = await response
    expect(r.status()).toBe(200)
    const body = await r.json()
    expect(body.code).toBe('OK')
    if (suffix !== '/work-sessions') expect(body.data.id).toBe(ticketId)
    if (suffix === '/complete') expect(body.data.status).toBe('COMPLETED')
    steps.push({ label, status: r.status() })
    if (suffix === '/call-next' || suffix === '/start')
      await page.waitForTimeout(1800)
  }
  await expect(
    page.getByRole('button', { name: '确认完成办理', exact: true }),
  ).toBeHidden()
  await expect(
    page.getByRole('button', { name: '叫下一号', exact: true }),
  ).toBeEnabled()
  await expect(page.getByText('小程序合成测试', { exact: true })).toBeVisible()
  await page.screenshot({
    path: resolve(output, 'staff-workbench.png'),
    fullPage: true,
  })
  writeFileSync(
    resolve(output, 'staff-web-result.json'),
    JSON.stringify({ steps, screenshot: 'staff-workbench.png' }, null, 2),
  )
  console.log(JSON.stringify({ passed: true, steps: steps.length }))
} finally {
  await browser.close()
}
