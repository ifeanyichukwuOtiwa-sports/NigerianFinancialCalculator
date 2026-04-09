import { expect, Page, test } from '@playwright/test';

type AuthUser = {
	id: number;
	fullName: string;
	email: string;
};

type AuthMockOptions = {
	initialUser?: AuthUser | null;
	loginUser?: AuthUser;
	registerUser?: AuthUser;
	scenarios?: ScenarioRecord[];
	loginErrorMessage?: string;
	registerErrorMessage?: string;
};

type ScenarioRecord = {
	id: number;
	name: string;
	principal: number;
	annualRate: number;
	years: number;
	monthlyContribution: number;
	compoundingFrequency: string;
	taxStrategy: string;
	annualIncome: number;
	totalBalance: number;
	totalInterest: number;
	estimatedTax: number;
};

type MockApiState = {
	loginRequests: Array<Record<string, unknown>>;
	registerRequests: Array<Record<string, unknown>>;
	savedScenarios: Array<Record<string, unknown>>;
	deletedScenarioIds: number[];
	exportedScenarioPaths: string[];
	taxExportRequests: Array<Record<string, unknown>>;
	scenarios: ScenarioRecord[];
};

const defaultUser: AuthUser = {
	id: 7,
	fullName: 'Ada Lovelace',
	email: 'ada@example.com',
};

async function mockApi(page: Page, options: AuthMockOptions = {}): Promise<MockApiState> {
	let currentUser = options.initialUser ?? null;
	const loginUser = options.loginUser ?? defaultUser;
	const registerUser = options.registerUser ?? loginUser;
	const scenarios = [...(options.scenarios ?? [])];
	const state: MockApiState = {
		loginRequests: [],
		registerRequests: [],
		savedScenarios: [],
		deletedScenarioIds: [],
		exportedScenarioPaths: [],
		taxExportRequests: [],
		scenarios,
	};

	await page.route('**/api/auth/me', async (route) => {
		if (currentUser) {
			await route.fulfill({
				status: 200,
				contentType: 'application/json',
				body: JSON.stringify(currentUser),
			});
			return;
		}

		await route.fulfill({
			status: 401,
			contentType: 'application/json',
			body: JSON.stringify({ message: 'Unauthenticated' }),
		});
	});

	await page.route('**/api/auth/login', async (route) => {
		state.loginRequests.push(route.request().postDataJSON() as Record<string, unknown>);
		if (options.loginErrorMessage) {
			await route.fulfill({
				status: 401,
				contentType: 'application/json',
				body: JSON.stringify({ message: options.loginErrorMessage }),
			});
			return;
		}

		currentUser = loginUser;
		await route.fulfill({
			status: 200,
			contentType: 'application/json',
			body: JSON.stringify(loginUser),
		});
	});

	await page.route('**/api/auth/register', async (route) => {
		state.registerRequests.push(route.request().postDataJSON() as Record<string, unknown>);
		if (options.registerErrorMessage) {
			await route.fulfill({
				status: 400,
				contentType: 'application/json',
				body: JSON.stringify({ message: options.registerErrorMessage }),
			});
			return;
		}

		await route.fulfill({
			status: 200,
			contentType: 'application/json',
			body: JSON.stringify(registerUser),
		});
	});

	await page.route('**/api/auth/logout', async (route) => {
		currentUser = null;
		await route.fulfill({ status: 204, body: '' });
	});

	await page.route('**/api/scenarios', async (route) => {
		if (route.request().method() === 'GET') {
			await route.fulfill({
				status: 200,
				contentType: 'application/json',
				body: JSON.stringify(scenarios),
			});
			return;
		}

		const payload = route.request().postDataJSON() as Record<string, unknown>;
		state.savedScenarios.push(payload);
		const scenario: ScenarioRecord = {
			id: scenarios.length + 1,
			name: String(payload['name'] ?? `Scenario ${scenarios.length + 1}`),
			principal: Number(payload['principal'] ?? 0),
			annualRate: Number(payload['annualRate'] ?? 0),
			years: Number(payload['years'] ?? 1),
			monthlyContribution: Number(payload['monthlyContribution'] ?? 0),
			compoundingFrequency: String(payload['compoundingFrequency'] ?? 'monthly'),
			taxStrategy: String(payload['taxStrategy'] ?? 'wht'),
			annualIncome: Number(payload['annualIncome'] ?? 0),
			totalBalance: 123456,
			totalInterest: 23456,
			estimatedTax: 3456,
		};
		scenarios.push(scenario);

		await route.fulfill({
			status: 200,
			contentType: 'application/json',
			body: JSON.stringify(scenario),
		});
	});

	await page.route('**/api/scenarios/**', async (route) => {
		if (route.request().method() === 'DELETE') {
			const id = Number(route.request().url().split('/').pop());
			state.deletedScenarioIds.push(id);
			const index = scenarios.findIndex((scenario) => scenario.id === id);
			if (index >= 0) {
				scenarios.splice(index, 1);
			}
			await route.fulfill({ status: 204, body: '' });
			return;
		}

		state.exportedScenarioPaths.push(new URL(route.request().url()).pathname);

		await route.fulfill({
			status: 200,
			contentType: 'application/octet-stream',
			body: 'stub',
		});
	});

	await page.route('**/api/tax/export/pdf', async (route) => {
		state.taxExportRequests.push(route.request().postDataJSON() as Record<string, unknown>);
		await route.fulfill({
			status: 200,
			contentType: 'application/pdf',
			body: 'stub-pdf',
		});
	});

	return state;
}

test.describe('frontend smoke flows', () => {
	test('landing page loads for signed-out users', async ({ page }) => {
		await mockApi(page);

		await page.goto('/');

		await expect(
			page
				.locator('.landing')
				.getByRole('heading', { name: 'Nigerian Financial Calculator' }),
		).toBeVisible();
		await expect(page.getByRole('button', { name: 'Login' })).toBeVisible();
		await expect(page.getByRole('button', { name: 'Register' })).toBeVisible();
		await expect(page.getByRole('button', { name: 'Get Started' })).toBeVisible();
	});

	test('auth modal opens, traps focus, and returns focus to its trigger on close', async ({
		page,
	}) => {
		await mockApi(page);

		await page.goto('/');

		const loginTrigger = page.getByRole('button', { name: 'Login' }).first();
		await loginTrigger.click();

		const dialog = page.getByRole('dialog', { name: 'Authentication' });
		await expect(dialog).toBeVisible();

		for (let i = 0; i < 6; i++) {
			await page.keyboard.press('Tab');
			await expect
				.poll(async () => {
					return page.evaluate(() =>
						Boolean(document.activeElement?.closest('dialog[open]')),
					);
				})
				.toBe(true);
		}

		await page.getByRole('button', { name: 'Close' }).click();
		await expect(dialog).toBeHidden();
		await expect(loginTrigger).toBeFocused();
	});

	test('register and login flows behave correctly', async ({ page }) => {
		await mockApi(page);

		await page.goto('/');
		await page.getByRole('button', { name: 'Register' }).first().click();

		await page.getByRole('button', { name: 'Register' }).nth(1).click();
		await page.getByLabel('Full Name').fill('Ada Lovelace');
		await page.getByLabel('Email').fill('ada@example.com');
		await page.getByLabel('Password', { exact: true }).fill('secret12');
		await page.getByRole('button', { name: 'Register' }).last().click();

		await expect(page.getByText('Registration successful! Please log in.')).toBeVisible();
		await expect(page.getByRole('button', { name: 'Login' }).nth(1)).toHaveClass(/active/);

		await page.getByLabel('Email').fill('ada@example.com');
		await page.getByLabel('Password', { exact: true }).fill('secret12');
		await page.getByRole('button', { name: 'Login' }).last().click();

		await expect(page).toHaveURL(/\/calculator$/);
		await expect(page.getByRole('button', { name: 'Logout' })).toBeVisible();
		await expect(page.getByText('Ada Lovelace')).toBeVisible();
	});

	test('protected routes redirect signed-out users to landing', async ({ page }) => {
		await mockApi(page);

		for (const path of ['/calculator', '/tax', '/scenarios']) {
			await page.goto(path);
			await expect(page).toHaveURL(/\/$/);
			await expect(
				page
					.locator('.landing')
					.getByRole('heading', { name: 'Nigerian Financial Calculator' }),
			).toBeVisible();
		}
	});

	test('signed-in users can navigate between calculator, tax, and scenarios', async ({
		page,
	}) => {
		await mockApi(page, { initialUser: defaultUser, scenarios: [] });

		await page.goto('/calculator');

		await expect(page).toHaveURL(/\/calculator$/);
		await expect(page.getByLabel('Initial investment')).toBeVisible();

		await page.getByRole('link', { name: 'Tax Calculator' }).click();
		await expect(page).toHaveURL(/\/tax$/);
		await expect(
			page.getByRole('heading', { name: 'Tax Breakdown (Nigeria 2026)' }),
		).toBeVisible();

		await page.getByRole('link', { name: 'My Scenarios' }).click();
		await expect(page).toHaveURL(/\/scenarios$/);
		await expect(page.getByRole('heading', { name: 'My Saved Scenarios' })).toBeVisible();
		await expect(page.getByText('No saved scenarios yet.')).toBeVisible();

		await page.getByRole('link', { name: 'Compound Interest' }).click();
		await expect(page).toHaveURL(/\/calculator$/);
		await expect(page.getByLabel('Initial investment')).toBeVisible();
	});

	test('logout clears the session and returns the user to landing', async ({ page }) => {
		await mockApi(page, { initialUser: defaultUser });

		await page.goto('/calculator');
		await page.getByRole('button', { name: 'Logout' }).click();

		await expect(page).toHaveURL(/\/$/);
		await expect(page.getByRole('button', { name: 'Login' }).first()).toBeVisible();
		await expect(page.getByRole('button', { name: 'Register' }).first()).toBeVisible();
	});

	test('failed register and login show backend error messages', async ({ page }) => {
		const api = await mockApi(page, {
			loginErrorMessage: 'Invalid credentials.',
			registerErrorMessage: 'Email already exists.',
		});

		await page.goto('/');
		await page.getByRole('button', { name: 'Register' }).first().click();
		const dialog = page.getByRole('dialog', { name: 'Authentication' });

		await dialog.getByLabel('Full Name').fill('Ada Lovelace');
		await dialog.getByLabel('Email').fill('ada@example.com');
		await dialog.getByLabel('Password', { exact: true }).fill('secret12');
		await dialog.getByRole('button', { name: 'Register' }).last().click();
		await expect(page.getByText('Email already exists.')).toBeVisible();

		await dialog.locator('.tabs').getByRole('button', { name: 'Login' }).click();
		await expect(dialog.locator('#login-email')).toBeVisible();
		await expect(dialog.locator('#login-password')).toBeVisible();
		await dialog.locator('#login-email').fill('ada@example.com');
		await dialog.locator('#login-password').fill('wrongpass');
		await dialog.locator('form.auth-form').getByRole('button', { name: 'Login' }).click();
		await expect(page.getByText('Invalid credentials.')).toBeVisible();
		expect(api.registerRequests).toHaveLength(1);
		expect(api.loginRequests).toHaveLength(1);
		await expect(page).toHaveURL(/\/$/);
	});

	test('investment scenarios can be saved and later deleted', async ({ page }) => {
		const api = await mockApi(page, { initialUser: defaultUser, scenarios: [] });

		await page.goto('/calculator');
		await page.getByRole('button', { name: '💾 Save Scenario' }).click();
		await page.getByLabel('Scenario Name').fill('Retirement Plan');
		await page.getByRole('button', { name: 'Save' }).click();

		await expect(page.getByText('Scenario saved successfully!')).toBeVisible();
		expect(api.savedScenarios).toHaveLength(1);
		expect(api.savedScenarios[0]?.['name']).toBe('Retirement Plan');

		await page.getByRole('link', { name: 'My Scenarios' }).click();
		await expect(page.getByText('Retirement Plan')).toBeVisible();
		await page.getByRole('button', { name: 'Delete scenario Retirement Plan' }).click();
		await expect(page.getByText('Retirement Plan')).toHaveCount(0);
		expect(api.deletedScenarioIds).toEqual([1]);
	});

	test('scenario export buttons trigger PDF and CSV downloads', async ({ page }) => {
		const api = await mockApi(page, {
			initialUser: defaultUser,
			scenarios: [
				{
					id: 10,
					name: 'Income Builder',
					principal: 100000,
					annualRate: 12,
					years: 10,
					monthlyContribution: 5000,
					compoundingFrequency: 'monthly',
					taxStrategy: 'wht',
					annualIncome: 0,
					totalBalance: 250000,
					totalInterest: 50000,
					estimatedTax: 5000,
				},
			],
		});

		await page.goto('/scenarios');

		await page.getByRole('button', { name: 'Export PDF' }).click();
		await expect.poll(() => api.exportedScenarioPaths.length).toBe(1);

		await page.getByRole('button', { name: 'Export CSV' }).click();
		await expect.poll(() => api.exportedScenarioPaths.length).toBe(2);

		expect(api.exportedScenarioPaths).toEqual([
			'/api/scenarios/10/export/pdf',
			'/api/scenarios/10/export/csv',
		]);
	});

	test('tax plan save and PDF export are wired correctly', async ({ page }) => {
		const api = await mockApi(page, { initialUser: defaultUser });
		const dialogs: string[] = [];

		page.on('dialog', async (dialog) => {
			dialogs.push(dialog.message());
			await dialog.accept();
		});

		await page.goto('/tax');
		await page.getByRole('button', { name: 'Save as Scenario' }).click();
		await expect.poll(() => dialogs).toContain('Tax calculation saved to your scenarios!');
		expect(api.savedScenarios).toHaveLength(1);
		expect(api.savedScenarios[0]?.['taxStrategy']).toBe('progressive');
		expect(String(api.savedScenarios[0]?.['name'])).toContain('Tax Plan -');

		const download = page.waitForEvent('download');
		await page.getByRole('button', { name: 'Export PDF Report' }).click();
		await download;
		expect(api.taxExportRequests).toHaveLength(1);
		expect(Number(api.taxExportRequests[0]?.['annualIncome'])).toBeGreaterThan(0);
	});
});
