import { test, expect } from '@playwright/test'

// Segmented in a real browser: genuine DOM focus and roving tabindex under the arrow keys, which
// jsdom can only approximate. Options: Grid, List, Map (disabled), Card.

test.beforeEach(async ({ page }) => {
  await page.goto('/salle-e2e/index.html?case=segmented')
  await expect(page.getByRole('radio', { name: 'Grid' })).toBeVisible()
})

test('first segment is selected by default', async ({ page }) => {
  await expect(page.getByRole('radio', { name: 'Grid' })).toHaveAttribute('aria-checked', 'true')
  await expect(page.getByRole('radio', { name: 'List' })).toHaveAttribute('aria-checked', 'false')
  await expect(page.locator('[data-part=segmented]')).toHaveAttribute('data-value', 'grid')
})

test('clicking a segment selects it', async ({ page }) => {
  await page.getByRole('radio', { name: 'List' }).click()
  await expect(page.getByRole('radio', { name: 'List' })).toHaveAttribute('aria-checked', 'true')
  await expect(page.getByRole('radio', { name: 'Grid' })).toHaveAttribute('aria-checked', 'false')
  await expect(page.locator('[data-part=segmented]')).toHaveAttribute('data-value', 'list')
})

test('ArrowRight moves focus and selects, skipping the disabled segment', async ({ page }) => {
  const grid = page.getByRole('radio', { name: 'Grid' })
  await grid.focus()
  await expect(grid).toBeFocused()

  await page.keyboard.press('ArrowRight')
  const list = page.getByRole('radio', { name: 'List' })
  await expect(list).toBeFocused()
  await expect(page.locator('[data-part=segmented]')).toHaveAttribute('data-value', 'list')

  // Map is disabled — ArrowRight skips it and lands on Card.
  await page.keyboard.press('ArrowRight')
  const card = page.getByRole('radio', { name: 'Card' })
  await expect(card).toBeFocused()
  await expect(page.locator('[data-part=segmented]')).toHaveAttribute('data-value', 'card')
})

test('ArrowRight wraps from the last enabled segment back to the first', async ({ page }) => {
  await page.getByRole('radio', { name: 'Card' }).click()
  await page.getByRole('radio', { name: 'Card' }).focus()
  await page.keyboard.press('ArrowRight')
  await expect(page.getByRole('radio', { name: 'Grid' })).toBeFocused()
})

test('roving tabindex: only the selected segment is tabbable', async ({ page }) => {
  await expect(page.getByRole('radio', { name: 'Grid' })).toHaveAttribute('tabindex', '0')
  await expect(page.getByRole('radio', { name: 'List' })).toHaveAttribute('tabindex', '-1')
})

test('Home and End jump to the first and last enabled segments', async ({ page }) => {
  await page.getByRole('radio', { name: 'Grid' }).focus()
  await page.keyboard.press('End')
  await expect(page.getByRole('radio', { name: 'Card' })).toBeFocused()
  await page.keyboard.press('Home')
  await expect(page.getByRole('radio', { name: 'Grid' })).toBeFocused()
})
