import { test, expect } from '@playwright/test'

// Carousel in a real browser: genuine autoplay timing (the jsdom specs fake the Timers seam),
// pause-on-hover, and real arrow/dot/keyboard/pointer-swipe navigation. Three slides: zero, one,
// two. `&autoplay=off` freezes it so the manual-navigation specs are deterministic.

const region = (page) => page.locator('[data-part=carousel]')

test('autoplay advances through the slides and wraps', async ({ page }) => {
  await page.goto('/salle-e2e/index.html?case=carousel')
  await expect(region(page)).toHaveAttribute('data-active-index', '0')
  // autoplaySpeed is 600ms; toHaveAttribute polls, so these catch each tick.
  await expect(region(page)).toHaveAttribute('data-active-index', '1')
  await expect(region(page)).toHaveAttribute('data-active-index', '2')
  await expect(region(page)).toHaveAttribute('data-active-index', '0')
})

test('pause-on-hover halts autoplay while the pointer is over the carousel', async ({ page }) => {
  await page.goto('/salle-e2e/index.html?case=carousel')
  await region(page).hover()
  // Stays put well past one interval while hovered.
  await page.waitForTimeout(1000)
  await expect(region(page)).toHaveAttribute('data-active-index', '0')
})

test('the next and prev arrows navigate, wrapping at the ends', async ({ page }) => {
  await page.goto('/salle-e2e/index.html?case=carousel&autoplay=off')
  await expect(region(page)).toHaveAttribute('data-active-index', '0')
  await page.getByRole('button', { name: 'Next slide' }).click()
  await expect(region(page)).toHaveAttribute('data-active-index', '1')
  await page.getByRole('button', { name: 'Previous slide' }).click()
  await expect(region(page)).toHaveAttribute('data-active-index', '0')
  // Prev from the first wraps to the last (infinite is the default).
  await page.getByRole('button', { name: 'Previous slide' }).click()
  await expect(region(page)).toHaveAttribute('data-active-index', '2')
})

test('a dot jumps directly to its slide', async ({ page }) => {
  await page.goto('/salle-e2e/index.html?case=carousel&autoplay=off')
  await page.getByRole('tab', { name: 'Go to slide 3' }).click()
  await expect(region(page)).toHaveAttribute('data-active-index', '2')
  await expect(page.getByRole('tab', { name: 'Go to slide 3' })).toHaveAttribute('aria-selected', 'true')
})

test('arrow keys step the focused carousel', async ({ page }) => {
  await page.goto('/salle-e2e/index.html?case=carousel&autoplay=off')
  await region(page).focus()
  await page.keyboard.press('ArrowRight')
  await expect(region(page)).toHaveAttribute('data-active-index', '1')
  await page.keyboard.press('ArrowLeft')
  await expect(region(page)).toHaveAttribute('data-active-index', '0')
})

test('a pointer swipe flips the slide', async ({ page }) => {
  await page.goto('/salle-e2e/index.html?case=carousel&autoplay=off')
  const box = await page.locator('[data-part=viewport]').boundingBox()
  if (!box) throw new Error('viewport not found')
  const cy = box.y + box.height / 2
  // Drag left past the threshold → next slide.
  await page.mouse.move(box.x + box.width - 20, cy)
  await page.mouse.down()
  await page.mouse.move(box.x + 20, cy, { steps: 8 })
  await page.mouse.up()
  await expect(region(page)).toHaveAttribute('data-active-index', '1')
})
