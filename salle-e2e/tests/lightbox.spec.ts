import { test, expect } from '@playwright/test'

// Lightbox in a real browser: a preview group of images that opens one shared, navigable
// viewer on click, with genuine arrow-key navigation, click-to-zoom, focus moving into the
// overlay, and Escape to close — behaviour jsdom cannot faithfully exercise (real image
// loading, real key events, real focus).

test.beforeEach(async ({ page }) => {
  await page.goto('/salle-e2e/index.html?case=lightbox')
  await expect(page.locator('img[alt=L1]')).toBeVisible()
})

test('clicking an image opens the shared lightbox at that image', async ({ page }) => {
  // No overlay until a click.
  await expect(page.locator('[data-part=overlay]')).toHaveCount(0)
  await page.locator('img[alt=L2]').click()
  const overlay = page.locator('[data-part=overlay]')
  await expect(overlay).toBeVisible()
  await expect(overlay).toHaveAttribute('role', 'dialog')
  await expect(page.locator('[data-part=counter]')).toHaveText('2 / 3')
  // Focus moved into the overlay.
  const inside = await overlay.evaluate(o => o.contains(document.activeElement))
  expect(inside).toBe(true)
})

test('arrow keys navigate through the whole set with wrap', async ({ page }) => {
  await page.locator('img[alt=L1]').click()
  await expect(page.locator('[data-part=counter]')).toHaveText('1 / 3')
  await page.keyboard.press('ArrowRight')
  await expect(page.locator('[data-part=counter]')).toHaveText('2 / 3')
  await page.keyboard.press('ArrowRight')
  await expect(page.locator('[data-part=counter]')).toHaveText('3 / 3')
  // wrap past the end back to the first
  await page.keyboard.press('ArrowRight')
  await expect(page.locator('[data-part=counter]')).toHaveText('1 / 3')
  // and backwards wraps to the last
  await page.keyboard.press('ArrowLeft')
  await expect(page.locator('[data-part=counter]')).toHaveText('3 / 3')
})

test('the next control advances the image', async ({ page }) => {
  await page.locator('img[alt=L1]').click()
  await page.locator('[data-part=next]').click()
  await expect(page.locator('[data-part=counter]')).toHaveText('2 / 3')
})

test('clicking the image toggles zoom; navigating resets it', async ({ page }) => {
  await page.locator('img[alt=L1]').click()
  const image = page.locator('[data-part=image]')
  await expect(image).toHaveAttribute('data-zoom', 'out')
  await image.click()
  await expect(image).toHaveAttribute('data-zoom', 'in')
  await page.keyboard.press('ArrowRight')
  await expect(page.locator('[data-part=image]')).toHaveAttribute('data-zoom', 'out')
})

test('Escape closes the lightbox', async ({ page }) => {
  await page.locator('img[alt=L1]').click()
  await expect(page.locator('[data-part=overlay]')).toBeVisible()
  await page.keyboard.press('Escape')
  await expect(page.locator('[data-part=overlay]')).toHaveCount(0)
})

test('the close button closes the lightbox', async ({ page }) => {
  await page.locator('img[alt=L1]').click()
  await page.locator('[data-part=close]').click()
  await expect(page.locator('[data-part=overlay]')).toHaveCount(0)
})

test('clicking the scrim closes; clicking the image does not', async ({ page }) => {
  await page.locator('img[alt=L1]').click()
  // a click on the image keeps it open
  await page.locator('[data-part=image]').click()
  await expect(page.locator('[data-part=overlay]')).toBeVisible()
  // a click on the scrim corner closes
  await page.locator('[data-part=overlay]').click({ position: { x: 5, y: 5 } })
  await expect(page.locator('[data-part=overlay]')).toHaveCount(0)
})
