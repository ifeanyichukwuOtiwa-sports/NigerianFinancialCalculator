import {
	CompoundingFrequency,
	FrequencyOption,
	IncomeFrequency,
	TaxBand,
	TaxStrategyOption,
} from '../types/app.types';

/**
 * Initial State Defaults
 */
export const DEFAULT_PRINCIPAL = 10000;
export const DEFAULT_RATE = 8;
export const DEFAULT_YEARS = 20;
export const DEFAULT_MONTHLY = 0;
export const DEFAULT_FREQUENCY: CompoundingFrequency = 'monthly';
export const DEFAULT_CURRENCY_CODE = 'NGN';
export const DEFAULT_CURRENCY_SYMBOL = '₦';
export const DEFAULT_ANNUAL_INCOME = 5000000;
export const DEFAULT_TAX_AMOUNT = 500000;
export const DEFAULT_INCOME_FREQUENCY: IncomeFrequency = 'annually';
export const APP_TITLE = 'Nigerian Financial Calculator';

/**
 * Maximum Range Limits
 */
export const MAX_PRINCIPAL = 1000000;
export const MAX_WEEKLY_INCOME = 1000000;
export const MAX_MONTHLY_INCOME = 100000000;
export const MAX_ANNUAL_INCOME = 1000000000;

/**
 * Milestone Targets (Multiples of principal)
 */
export const MILESTONE_TARGETS = [2, 5, 10, 20, 40, 50, 100];

/**
 * Compounding Frequency Definitions
 */
export const FREQUENCY_OPTIONS: FrequencyOption[] = [
	{ label: 'Monthly', value: 'monthly', periods: 12 },
	{ label: 'Quarterly', value: 'quarterly', periods: 4 },
	{ label: 'Bi-Annually', value: 'bi-annually', periods: 2 },
	{ label: 'Annually', value: 'annually', periods: 1 },
];

/**
 * Income Frequency Definitions (for Tax)
 */
export const INCOME_FREQUENCY_OPTIONS: {
	label: string;
	value: IncomeFrequency;
	periods: number;
}[] = [
	{ label: 'Weekly', value: 'weekly', periods: 52 },
	{ label: 'Monthly', value: 'monthly', periods: 12 },
	{ label: 'Annually', value: 'annually', periods: 1 },
];

/**
 * Nigeria Tax Act 2025 (Effective Jan 1, 2026) Configuration
 */
export const NIGERIA_WHT_RATE = 0.1; // 10% Withholding Tax

export const NIGERIA_PIT_BANDS_2026: TaxBand[] = [
	{ limit: 800000, rate: 0.0 }, // First ₦800,000 (Exempt)
	{ limit: 2200000, rate: 0.15 }, // Next ₦2,200,000 (15%)
	{ limit: 9000000, rate: 0.18 }, // Next ₦9,000,000 (18%)
	{ limit: 13000000, rate: 0.21 }, // Next ₦13,000,000 (21%)
	{ limit: 25000000, rate: 0.23 }, // Next ₦25,000,000 (23%)
	{ limit: Infinity, rate: 0.25 }, // Above ₦50,000,000 (25%)
];

/**
 * Historical/Projected Thresholds for Nigeria 2026 (for labeling)
 */
export const NIGERIA_PIT_THRESHOLDS_2026 = [0, 800000, 3000000, 12000000, 25000000, 50000000];

export const TAX_STRATEGY_OPTIONS: TaxStrategyOption[] = [
	{ label: 'Exempt (FGN Bonds)', value: 'none', description: 'No tax applied to interest' },
	{
		label: '10% WHT (Standard)',
		value: 'wht',
		description: 'Flat 10% Withholding Tax on interest',
	},
	{
		label: 'Progressive PIT',
		value: 'progressive',
		description: 'Personal Income Tax based on 2026 bands',
	},
];

/**
 * Theme Time Intensity Thresholds
 */
export const TIME_PERIODS = {
	COOL: { start: 0, end: 6 },
	WARM: { start: 6, end: 12 },
	HOT: { start: 12, end: 15 },
};
