import { expect, test } from '@playwright/test'

const panelUrl = process.env.PANEL_URL ?? 'http://127.0.0.1:8080'

async function expectNoPageOverflow(page: import('@playwright/test').Page) {
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth),
  ).toBe(true)
}

test('login, roles, reports, inventory and server state work in a real browser', async ({ page }) => {
  const consoleErrors: string[] = []
  const staticErrors: string[] = []
  page.on('console', (message) => {
    if (/Content Security Policy|OTS parsing/i.test(message.text())) {
      consoleErrors.push(message.text())
    }
  })
  page.on('response', (response) => {
    const path = new URL(response.url()).pathname
    if (response.status() >= 400 && /^\/(assets|fonts)\//.test(path)) {
      staticErrors.push(`${response.status()} ${path}`)
    }
  })

  const font = await page.request.get(`${panelUrl}/fonts/Monocraft.ttf`)
  expect(font.status()).toBe(200)
  expect(font.headers()['content-type']).toMatch(/font|octet-stream/)
  expect(font.headers()['cache-control']).toContain('max-age=31536000')

  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto(`${panelUrl}/login`)
  await page.evaluate(() => document.fonts.ready)
  await expectNoPageOverflow(page)
  await page.locator('#username').fill(process.env.BOOTSTRAP_ADMIN_USERNAME ?? 'admin')
  await page.locator('#password').fill(process.env.BOOTSTRAP_ADMIN_PASSWORD ?? 'change-me-now')
  await page.locator('form.login button[type="submit"]').click()
  await expect(page.locator('.board')).toBeVisible()
  await expect(page.locator('.card-status-control select').first()).toBeVisible()

  for (const width of [390, 720, 1440]) {
    await page.setViewportSize({ width, height: 900 })
    await expectNoPageOverflow(page)
  }
  await expect(page.locator('.app-nav a')).toHaveCount(5)
  for (const link of await page.locator('.app-nav a').all()) {
    await expect(link).toHaveAttribute('aria-label', /.+/)
  }

  const reportPath = await page.locator('.card-cat').first().getAttribute('href')
  expect(reportPath).toMatch(/^\/reports\//)
  await page.goto(`${panelUrl}${reportPath}`)
  await expect(page.locator('.inv-slot').first()).toBeVisible()
  await page.reload()
  await expect(page.locator('.inv-slot').first()).toBeVisible()

  await page.goto(`${panelUrl}/roles`)
  await expect(page.getByText(/Smoke role/).first()).toBeVisible()
  await page.goto(`${panelUrl}/servers`)
  await expect(page.locator('progress.mc-xp').first()).toBeVisible()
  await expectNoPageOverflow(page)
  expect(consoleErrors).toEqual([])
  expect(staticErrors).toEqual([])
})
