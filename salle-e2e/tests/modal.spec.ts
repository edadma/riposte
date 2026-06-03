import { test, expect } from '@playwright/test'

// Modal in a real browser: genuine focus move into the dialog, Tab focus-trap, Esc to close,
// and focus restoration to the opener — all behaviour jsdom cannot faithfully exercise.

test.beforeEach(async ({ page }) => {
  await page.goto('/salle-e2e/index.html?case=modal')
  await expect(page.locator('#open')).toBeVisible()
})

test('opening moves focus into the dialog; Escape closes and restores it', async ({ page }) => {
  await page.locator('#open').click()
  const dialog = page.getByRole('dialog')
  await expect(dialog).toBeVisible()

  // Focus has moved into the dialog (away from the opener button).
  await expect(page.locator('#open')).not.toBeFocused()
  const focusedInDialog = await dialog.evaluate(d => d.contains(document.activeElement))
  expect(focusedInDialog).toBe(true)

  await page.keyboard.press('Escape')
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await expect(page.locator('#open')).toBeFocused()
})

test('clicking the scrim closes the dialog', async ({ page }) => {
  await page.locator('#open').click()
  await expect(page.getByRole('dialog')).toBeVisible()
  // Click the overlay at a corner, away from the centred box.
  await page.locator('[data-part=overlay]').click({ position: { x: 5, y: 5 } })
  await expect(page.getByRole('dialog')).toHaveCount(0)
})

test('Tab focus stays trapped inside the dialog', async ({ page }) => {
  await page.locator('#open').click()
  const dialog = page.getByRole('dialog')
  await expect(dialog).toBeVisible()
  // Cycle Tab several times; focus must never escape the dialog.
  for (let i = 0; i < 6; i++) {
    await page.keyboard.press('Tab')
    const inside = await dialog.evaluate(d => d.contains(document.activeElement))
    expect(inside).toBe(true)
  }
})
