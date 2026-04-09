import { defineConfig } from '@playwright/test';

const port = 4200;
const baseURL = `http://127.0.0.1:${port}`;

export default defineConfig({
	testDir: './e2e',
	timeout: 30_000,
	expect: {
		timeout: 5_000,
	},
	fullyParallel: false,
	workers: process.env.CI ? 1 : undefined,
	reporter: [['list'], ['html', { open: 'never' }]],
	use: {
		baseURL,
		browserName: 'chromium',
		headless: true,
		trace: 'on-first-retry',
		screenshot: 'only-on-failure',
		video: 'off',
	},
	webServer: {
		command: `npm start -- --host 127.0.0.1 --port ${port}`,
		url: baseURL,
		reuseExistingServer: !process.env.CI,
		timeout: 120_000,
	},
});
