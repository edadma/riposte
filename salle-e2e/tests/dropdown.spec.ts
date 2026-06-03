import { test, expect } from '@playwright/test'

// Dropdown in a real browser: real pointer click to open, a real outside click to dismiss
// (the jsdom spec dispatches a synthetic pointerdown), and keyboard open.

test.beforeEach(async ({ page }) => {
  await page.goto('/salle-e2e/index.html?case=dropdown')
  await expect(page.locator('[data-part=trigger]')).toBeVisible()
})

test('clicking the trigger opens the menu; an outside click dismisses it', async ({ page }) => {
  await expect(page.getByRole('menu')).toHaveCount(0)
  await page.locator('[data-part=trigger]').click()
  await expect(page.getByRole('menu')).toBeVisible()
  await expect(page.getByRole('menuitem', { name: 'Edit' })).toBeVisible()

  await page.locator('#outside').click()
  await expect(page.getByRole('menu')).toHaveCount(0)
})

test('choosing an item runs it and closes the menu', async ({ page }) => {
  await page.locator('[data-part=trigger]').click()
  await page.getByRole('menuitem', { name: 'Duplicate' }).click()
  await expect(page.getByRole('menu')).toHaveCount(0)
})

test('ArrowDown opens the menu from the trigger', async ({ page }) => {
  await page.locator('[data-part=trigger]').focus()
  await page.keyboard.press('ArrowDown')
  await expect(page.getByRole('menu')).toBeVisible()
})
