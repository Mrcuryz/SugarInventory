import { defineConfig } from '@playwright/test'

const appBaseUrl = process.env.AGENT_E2E_BASE_URL || 'http://127.0.0.1:5173'
const shouldStartWebServer = process.env.AGENT_E2E_START_WEB_SERVER !== '0' && !process.env.AGENT_E2E_BASE_URL

export default defineConfig({
  testDir: './e2e',
  testMatch: /agent-hitl\.spec\.ts/,
  fullyParallel: false,
  workers: 1,
  timeout: 45_000,
  expect: {
    timeout: 12_000
  },
  use: {
    baseURL: appBaseUrl,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure'
  },
  webServer: shouldStartWebServer
    ? {
        command: 'npm run dev -- --host 127.0.0.1',
        url: appBaseUrl,
        reuseExistingServer: true,
        timeout: 120_000,
        stdout: 'pipe',
        stderr: 'pipe'
      }
    : undefined
})
