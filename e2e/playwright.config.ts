import { defineConfig, devices } from '@playwright/test';

const baseURL = process.env.E2E_BASE_URL ?? 'http://localhost:14200';
const apiURL = process.env.E2E_API_URL ?? 'http://localhost:18081';

export default defineConfig({
  testDir: './tests',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  reporter: [['html', { open: 'never' }], ['list']],
  use: {
    baseURL,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: process.env.CI
    ? undefined
    : [
        {
          command: 'node ../scripts/wait-for-url.mjs ' + apiURL + '/actuator/health',
          url: apiURL + '/actuator/health',
          reuseExistingServer: true,
          timeout: 120_000,
        },
      ],
});
