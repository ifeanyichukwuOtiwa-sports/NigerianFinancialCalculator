import {
	CalculationResult,
	TaxBand,
	TaxStrategy,
	PITResult,
	TaxBandResult,
} from '../types/app.types';
import { NIGERIA_PIT_BANDS_2026, NIGERIA_WHT_RATE } from './app.constants';

/**
 * Calculates progressive tax based on configurable bands.
 */
export function calculateProgressiveTax(income: number, bands: TaxBand[]): number {
	return calculateDetailedPIT(income, bands).totalTax;
}

/**
 * Provides a detailed breakdown of Personal Income Tax.
 */
export function calculateDetailedPIT(income: number, bands: TaxBand[]): PITResult {
	if (income <= 0) {
		return {
			totalIncome: 0,
			totalTax: 0,
			netIncome: 0,
			effectiveRate: 0,
			bandResults: bands.map((band) => ({ band, taxableAmount: 0, tax: 0 })),
		};
	}

	let totalTax = 0;
	let remaining = income;
	const bandResults: TaxBandResult[] = [];

	for (const band of bands) {
		const taxableAmount = Math.min(remaining, band.limit);
		const tax = taxableAmount * band.rate;
		totalTax += tax;
		remaining -= taxableAmount;
		bandResults.push({ band, taxableAmount, tax });
	}

	return {
		totalIncome: income,
		totalTax,
		netIncome: income - totalTax,
		effectiveRate: (totalTax / income) * 100,
		bandResults,
	};
}

/**
 * Extrapolates gross annual income from a given annual tax amount using progressive bands.
 */
export function calculateIncomeFromTax(taxAmount: number, bands: TaxBand[]): number {
	if (taxAmount < 0) return 0;

	let remainingTax = taxAmount;
	let totalIncome = 0;

	for (const band of bands) {
		if (band.rate === 0) {
			totalIncome += band.limit;
			continue;
		}

		const maxTaxInBand = band.limit * band.rate;
		if (remainingTax <= maxTaxInBand) {
			totalIncome += remainingTax / band.rate;
			remainingTax = 0;
			break;
		} else {
			totalIncome += band.limit;
			remainingTax -= maxTaxInBand;
		}
	}

	if (remainingTax > 0) {
		const lastBand = bands[bands.length - 1];
		if (lastBand.rate > 0) {
			totalIncome += remainingTax / lastBand.rate;
		}
	}

	return totalIncome;
}

/**
 * Enhanced calculator that includes Nigeria-specific tax logic.
 * Formula: A = P(1 + r/n)^(nt) + PMT * [((1 + r/n)^(nt) - 1) / (r/n)]
 */
export function calculateFutureBalance(
	principal: number,
	annualRate: number,
	years: number,
	contribution: number,
	periodsPerYear: number = 12,
	taxStrategy: TaxStrategy = 'none',
	annualIncome: number = 0,
): CalculationResult {
	const r = annualRate / 100 / periodsPerYear; // periodic interest rate
	const n = periodsPerYear;
	const t = years;
	const PMT = contribution;

	const compoundInterest = principal * Math.pow(1 + r, n * t);
	const futureValueSeries = r > 0 ? PMT * ((Math.pow(1 + r, n * t) - 1) / r) : PMT * n * t;

	const totalBalance = compoundInterest + futureValueSeries;
	const totalInvested = principal + PMT * n * t;
	const totalInterest = totalBalance - totalInvested;

	let estimatedTax = 0;
	if (taxStrategy === 'wht') {
		estimatedTax = totalInterest * NIGERIA_WHT_RATE;
	} else if (taxStrategy === 'progressive') {
		const taxWithInterest = calculateProgressiveTax(
			annualIncome + totalInterest,
			NIGERIA_PIT_BANDS_2026,
		);
		const taxBase = calculateProgressiveTax(annualIncome, NIGERIA_PIT_BANDS_2026);
		estimatedTax = taxWithInterest - taxBase;
	}

	return {
		totalBalance,
		totalInvested,
		totalInterest,
		estimatedTax,
		netInterest: totalInterest - estimatedTax,
		netBalance: totalBalance - estimatedTax,
	};
}
