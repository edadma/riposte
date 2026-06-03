import { defineConfig, devices } from '@playwright/test'

// Playwright e2e for salle. The specs drive the harness app in salle-e2e/, which mounts one
// component fixture per `?case=` query param (see salle-e2e/src/main/scala/SalleE2E.scala).
//
// The harness bundle must be built first:  sbt salleE2E/fastLinkJS  (npm run e2e:build).
// `npm run e2e` does the build then runs this. The webServer below serves the repo ROOT, so
// the page's `../salle/css/salle.css` and the compiled bundle both resolve.
const PORT = 4317

export default defineConfig({
  testDir: './salle-e2e/tests',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? 'github' : 'list',
  use: {
    baseURL: `http://localhost:${PORT}`,
    trace: 'on-first-retry',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    // Serve the repo root statically; npx pulls in `http-server` on demand.
    command: `npx --yes http-server -p ${PORT} -c-1 --silent .`,
    url: `http://localhost:${PORT}/salle-e2e/index.html`,
    reuseExistingServer: !process.env.CI,
    timeout: 60_000,
  },
})
