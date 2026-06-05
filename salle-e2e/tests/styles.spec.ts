import { test, expect } from '@playwright/test'

// salle ships its CSS *inside* the compiled artifact and injects it at runtime (SalleStyles),
// so a consuming app needs no <link>. This proves the real-browser path the jsdom unit tests
// deliberately skip: the <style> is injected once, and a real browser parses + applies it
// (jsdom can't parse the cascade layers the stylesheet uses).

test.beforeEach(async ({ page }) => {
  await page.goto('/salle-e2e/index.html?case=styles')
  await expect(page.locator('button.salle-btn')).toBeVisible()
})

test('the salle stylesheet is injected into <head> exactly once', async ({ page }) => {
  await expect(page.locator('head style#salle-styles')).toHaveCount(1)
  const css = await page
    .locator('head style#salle-styles')
    .evaluate((el) => el.textContent ?? '')
  expect(css).toContain('@layer salle')
  expect(css).toContain('.salle-btn')
})

test('the browser parses and applies the injected CSS', async ({ page }) => {
  // A bare <button> defaults to display:inline-block / font-weight:normal; these values come
  // only from salle.css, so reading them back proves the injected stylesheet took effect.
  const styles = await page.locator('button.salle-btn').evaluate((el) => {
    const cs = getComputedStyle(el)
    return { display: cs.display, fontWeight: cs.fontWeight, cursor: cs.cursor }
  })
  expect(styles.display).toBe('inline-flex')
  expect(styles.fontWeight).toBe('600')
  expect(styles.cursor).toBe('pointer')
})
