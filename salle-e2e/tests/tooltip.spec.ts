import { test, expect } from '@playwright/test'

// Tooltip's jsdom specs fake the Timers/Transition seams; here we exercise the real thing —
// genuine pointer hover, the real enter/leave delay, and focus-shows-too (the accessible
// default for the Hover trigger).

test.beforeEach(async ({ page }) => {
  await page.goto('/salle-e2e/index.html?case=tooltip')
  await expect(page.locator('#tt-trigger')).toBeVisible()
})

test('hovering the trigger shows the tip, leaving hides it', async ({ page }) => {
  await expect(page.locator('[data-part=tip]')).toHaveCount(0)
  await page.locator('#tt-trigger').hover()
  const tip = page.locator('[data-part=tip]')
  await expect(tip).toBeVisible()
  await expect(tip).toHaveText('Helpful hint')
  await expect(tip).toHaveAttribute('role', 'tooltip')
  // Move the pointer well away from the trigger; after the leave delay the tip unmounts.
  await page.mouse.move(0, 0)
  await expect(page.locator('[data-part=tip]')).toHaveCount(0)
})

test('focusing the trigger also shows the tip (accessible hover default)', async ({ page }) => {
  await page.locator('#tt-trigger').focus()
  await expect(page.locator('[data-part=tip]')).toBeVisible()
  // The wrapper advertises the tip while it is shown.
  const tipId = await page.locator('[data-part=tip]').getAttribute('id')
  await expect(page.locator('[data-part=tooltip]')).toHaveAttribute('aria-describedby', tipId!)
})

test('Escape dismisses a shown tip', async ({ page }) => {
  await page.locator('#tt-trigger').focus()
  await expect(page.locator('[data-part=tip]')).toBeVisible()
  await page.keyboard.press('Escape')
  await expect(page.locator('[data-part=tip]')).toHaveCount(0)
})
