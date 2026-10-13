import automator from 'miniprogram-automator'
import { mkdir } from 'node:fs/promises'
import { resolve } from 'node:path'
// Attach only to a user-enabled automation session. Never changes IDE security settings.
const mini = await automator.connect({
  wsEndpoint: process.env.WECHAT_AUTOMATOR_WS || 'ws://127.0.0.1:9420',
})
const output = resolve('.local/wechat-smoke')
await mkdir(output, { recursive: true })
try {
  for (const [path, title] of [
    ['/pages/login/index', '账号登录'],
    ['/pages/help/index', '办理前，先看这里'],
  ]) {
    const page = await mini.reLaunch(path)
    if (!page) throw new Error('Page did not open: ' + path)
    await page.waitFor('.title')
    const content = await (await page.$('.page')).text()
    if (!content.includes(title))
      throw new Error('Expected title missing: ' + title)
    await mini.screenshot({
      path: resolve(output, path.includes('login') ? 'login.png' : 'help.png'),
    })
  }
  console.log(
    'Wechat simulator login/help navigation smoke passed; screenshots: .local/wechat-smoke',
  )
} finally {
  mini.disconnect()
}
