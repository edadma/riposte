import { test, expect } from '@playwright/test'

// ImageCard's lazy-load rides a real IntersectionObserver — the one piece its jsdom spec
// cannot exercise (jsdom has no IO, so the unit path falls back to eager load). Here the card
// sits far below the fold: it stays "not in view" until we scroll to it, then loads.

test.beforeEach(async ({ page }) => {
  await page.goto('/salle-e2e/index.html?case=imagecard')
  await expect(page.locator('#card-wrap')).toBeAttached()
})

test('the card loads only once scrolled into view', async ({ page }) => {
  const card = page.locator('[data-inview]')
  // Out of view at the top of the page (rootMargin keeps it from pre-loading at 1500px down).
  await expect(card).toHaveAttribute('data-inview', 'false')

  await page.locator('#card-wrap').scrollIntoViewIfNeeded()

  await expect(card).toHaveAttribute('data-inview', 'true')
  await expect(card).toHaveAttribute('data-state', 'loaded')
})
