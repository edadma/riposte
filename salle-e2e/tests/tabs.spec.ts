import { test, expect } from '@playwright/test'

// Tabs in a real browser: genuine DOM focus and roving tabindex under the arrow keys, which
// jsdom can only approximate. Items: Alpha, Beta, Gamma (disabled), Delta.

test.beforeEach(async ({ page }) => {
  await page.goto('/salle-e2e/index.html?case=tabs')
  await expect(page.getByRole('tab', { name: 'Alpha' })).toBeVisible()
})

test('first tab is active and its panel is shown', async ({ page }) => {
  await expect(page.locator('#panel-a')).toBeVisible()
  await expect(page.locator('#panel-b')).toHaveCount(0)
  await expect(page.getByRole('tab', { name: 'Alpha' })).toHaveAttribute('aria-selected', 'true')
})

test('clicking a tab switches the panel', async ({ page }) => {
  await page.getByRole('tab', { name: 'Beta' }).click()
  await expect(page.locator('#panel-b')).toBeVisible()
  await expect(page.locator('#panel-a')).toHaveCount(0)
})

test('ArrowRight moves focus and activates, skipping the disabled tab', async ({ page }) => {
  const alpha = page.getByRole('tab', { name: 'Alpha' })
  await alpha.focus()
  await expect(alpha).toBeFocused()

  await page.keyboard.press('ArrowRight')
  const beta = page.getByRole('tab', { name: 'Beta' })
  await expect(beta).toBeFocused()
  await expect(page.locator('#panel-b')).toBeVisible()

  // Gamma is disabled — ArrowRight skips it and lands on Delta.
  await page.keyboard.press('ArrowRight')
  const delta = page.getByRole('tab', { name: 'Delta' })
  await expect(delta).toBeFocused()
  await expect(page.locator('#panel-d')).toBeVisible()
})

test('roving tabindex: only the active tab is tabbable', async ({ page }) => {
  await expect(page.getByRole('tab', { name: 'Alpha' })).toHaveAttribute('tabindex', '0')
  await expect(page.getByRole('tab', { name: 'Beta' })).toHaveAttribute('tabindex', '-1')
})

test('Home and End jump to the first and last enabled tabs', async ({ page }) => {
  await page.getByRole('tab', { name: 'Alpha' }).focus()
  await page.keyboard.press('End')
  await expect(page.getByRole('tab', { name: 'Delta' })).toBeFocused()
  await page.keyboard.press('Home')
  await expect(page.getByRole('tab', { name: 'Alpha' })).toBeFocused()
})
