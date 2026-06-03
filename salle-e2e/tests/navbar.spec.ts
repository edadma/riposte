import { test, expect } from '@playwright/test'

// Navbar responsive collapse in a real browser: on a wide viewport all three zones sit on the
// bar and the hamburger is hidden; below the breakpoint the center/end zones fold away behind
// the hamburger and the toggle reveals them. This is driven by matchMedia, which only reports a
// real value in a browser — jsdom always sees "wide" — so the collapse can only be exercised
// here, by resizing the viewport.

test.beforeEach(async ({ page }) => {
  await page.goto('/salle-e2e/index.html?case=navbar')
  await expect(page.locator('[data-part=navbar]')).toBeVisible()
})

test('on a wide viewport all zones show and the toggle is hidden', async ({ page }) => {
  await page.setViewportSize({ width: 1100, height: 700 })
  const nav = page.locator('[data-part=navbar]')
  await expect(nav).toHaveAttribute('data-narrow', 'false')
  await expect(page.locator('#nav-brand')).toBeVisible()
  await expect(page.locator('#nav-link')).toBeVisible()
  await expect(page.locator('#nav-action')).toBeVisible()
  await expect(page.locator('[data-part=toggle]')).toBeHidden()
})

test('below the breakpoint the zones fold behind the hamburger', async ({ page }) => {
  await page.setViewportSize({ width: 500, height: 700 })
  const nav = page.locator('[data-part=navbar]')
  await expect(nav).toHaveAttribute('data-narrow', 'true')
  // brand stays in the bar; the toggle appears; center/end are folded away
  await expect(page.locator('#nav-brand')).toBeVisible()
  await expect(page.locator('[data-part=toggle]')).toBeVisible()
  await expect(page.locator('#nav-link')).toBeHidden()
  await expect(page.locator('#nav-action')).toBeHidden()
})

test('the hamburger opens and closes the folded menu', async ({ page }) => {
  await page.setViewportSize({ width: 500, height: 700 })
  const nav = page.locator('[data-part=navbar]')
  const toggle = page.locator('[data-part=toggle]')
  await expect(toggle).toBeVisible()
  await expect(nav).toHaveAttribute('data-open', 'false')

  await toggle.click()
  await expect(nav).toHaveAttribute('data-open', 'true')
  await expect(toggle).toHaveAttribute('aria-expanded', 'true')
  await expect(page.locator('#nav-link')).toBeVisible()
  await expect(page.locator('#nav-action')).toBeVisible()

  await toggle.click()
  await expect(nav).toHaveAttribute('data-open', 'false')
  await expect(page.locator('#nav-link')).toBeHidden()
})

test('resizing back to wide re-shows the zones and closes the menu', async ({ page }) => {
  // open the menu while narrow…
  await page.setViewportSize({ width: 500, height: 700 })
  await page.locator('[data-part=toggle]').click()
  await expect(page.locator('[data-part=navbar]')).toHaveAttribute('data-open', 'true')
  // …then widen: the bar leaves narrow mode, the menu auto-closes, and the zones return
  await page.setViewportSize({ width: 1100, height: 700 })
  await expect(page.locator('[data-part=navbar]')).toHaveAttribute('data-narrow', 'false')
  await expect(page.locator('[data-part=navbar]')).toHaveAttribute('data-open', 'false')
  await expect(page.locator('#nav-link')).toBeVisible()
  await expect(page.locator('[data-part=toggle]')).toBeHidden()
})
