import { test, expect } from '@playwright/test'

// Drawer in a real browser: it slides in from the edge, moves focus into the panel, traps Tab
// inside it, closes on Escape / scrim click / the close button, and restores focus to the
// opener — behaviour jsdom cannot faithfully exercise (real focus, real key events).

test.beforeEach(async ({ page }) => {
  await page.goto('/salle-e2e/index.html?case=drawer')
  await expect(page.locator('#open')).toBeVisible()
})

test('opening slides the panel in, moves focus into it, and mirrors the open state', async ({ page }) => {
  await expect(page.locator('[data-part=drawer-root]')).toHaveCount(0)
  await page.locator('#open').click()
  const panel = page.getByRole('dialog')
  await expect(panel).toBeVisible()
  await expect(page.locator('[data-part=panel]')).toHaveAttribute('data-state', 'open')
  await expect(page.locator('[data-part=panel]')).toHaveAttribute('data-placement', 'right')

  // Focus has moved off the opener and into the panel.
  await expect(page.locator('#open')).not.toBeFocused()
  const inside = await panel.evaluate(d => d.contains(document.activeElement))
  expect(inside).toBe(true)
})

test('Escape closes the drawer and restores focus to the opener', async ({ page }) => {
  await page.locator('#open').click()
  await expect(page.getByRole('dialog')).toBeVisible()
  await page.keyboard.press('Escape')
  await expect(page.locator('[data-part=drawer-root]')).toHaveCount(0)
  await expect(page.locator('#open')).toBeFocused()
})

test('clicking the scrim closes the drawer', async ({ page }) => {
  await page.locator('#open').click()
  await expect(page.getByRole('dialog')).toBeVisible()
  // Click the mask at the far-left corner, away from the right-docked panel.
  await page.locator('[data-part=mask]').click({ position: { x: 5, y: 5 } })
  await expect(page.locator('[data-part=drawer-root]')).toHaveCount(0)
})

test('the close button closes the drawer', async ({ page }) => {
  await page.locator('#open').click()
  await expect(page.getByRole('dialog')).toBeVisible()
  await page.locator('[data-part=close]').click()
  await expect(page.locator('[data-part=drawer-root]')).toHaveCount(0)
})

test('Tab focus stays trapped inside the panel', async ({ page }) => {
  await page.locator('#open').click()
  const panel = page.getByRole('dialog')
  await expect(panel).toBeVisible()
  for (let i = 0; i < 6; i++) {
    await page.keyboard.press('Tab')
    const inside = await panel.evaluate(d => d.contains(document.activeElement))
    expect(inside).toBe(true)
  }
})
