import { Component, computed, model, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CurrencyPipe, DecimalPipe, TitleCasePipe } from '@angular/common';
import { calculateDetailedPIT, calculateIncomeFromTax } from '@app/app.utils';
import * as Const from '../../app.constants';
import { IncomeFrequency, PITResult } from '@app/app.types';

@Component({
	selector: 'app-tax-calculator',
	imports: [FormsModule, CurrencyPipe, DecimalPipe, TitleCasePipe],
	templateUrl: './tax-calculator.component.html',
	styleUrl: './tax-calculator.component.scss',
})
export class TaxCalculatorComponent {
	// --- Input State (Synchronized with AppRoot) ---
	annualIncome = model(Const.DEFAULT_ANNUAL_INCOME);

	// --- UI State ---
	protected readonly calculationMode = signal<'incomeToTax' | 'taxToIncome'>('incomeToTax');
	protected readonly incomeFrequency = signal<IncomeFrequency>(Const.DEFAULT_INCOME_FREQUENCY);
	protected readonly editingField = signal<string | null>(null);

	// --- Periodic Inputs (used to drive annualIncome) ---
	protected readonly periodicValue = signal<number>(Const.DEFAULT_ANNUAL_INCOME);

	constructor() {
		// Sync periodicValue with annualIncome on init based on default frequency
		const periods = this.getPeriods(Const.DEFAULT_INCOME_FREQUENCY);
		this.periodicValue.set(this.annualIncome() / periods);
	}

	// --- Computed Results ---
	protected readonly pitResult = computed<PITResult>(() => {
		return calculateDetailedPIT(this.annualIncome(), Const.NIGERIA_PIT_BANDS_2026);
	});

	protected readonly periodicTax = computed(() => {
		const periods = this.getPeriods(this.incomeFrequency());
		return this.pitResult().totalTax / periods;
	});

	protected readonly bands = Const.NIGERIA_PIT_BANDS_2026;
	protected readonly frequencyOptions = Const.INCOME_FREQUENCY_OPTIONS;

	protected readonly periodicMax = computed(() => {
		const isIncome = this.calculationMode() === 'incomeToTax';
		const freq = this.incomeFrequency();

		if (isIncome) {
			if (freq === 'weekly') return Const.MAX_WEEKLY_INCOME;
			if (freq === 'monthly') return Const.MAX_MONTHLY_INCOME;
			return Const.MAX_ANNUAL_INCOME;
		} else {
			// For tax target, use 25% of the corresponding income max as a sensible upper bound
			if (freq === 'weekly') return Const.MAX_WEEKLY_INCOME * 0.25;
			if (freq === 'monthly') return Const.MAX_MONTHLY_INCOME * 0.25;
			return Const.MAX_ANNUAL_INCOME * 0.25;
		}
	});

	protected readonly periodicStep = computed(() => {
		const max = this.periodicMax();
		if (max >= 100000000) return 1000000;
		if (max >= 1000000) return 10000;
		if (max >= 100000) return 1000;
		return 100;
	});

	protected getBandLabel(index: number): string {
		const thresholds = Const.NIGERIA_PIT_THRESHOLDS_2026;
		if (index >= thresholds.length) return 'Above ₦50,000,000';
		if (index === 0) return 'First ₦800,000 (Exempt)';
		const current = thresholds[index];
		const next = thresholds[index + 1];
		if (next === undefined) return `Above ₦${current.toLocaleString()}`;
		return `Next ₦${this.bands[index].limit.toLocaleString()}`;
	}

	protected setCalculationMode(mode: 'incomeToTax' | 'taxToIncome'): void {
		this.calculationMode.set(mode);
		// When switching mode, sync the current result to the periodic input
		const periods = this.getPeriods(this.incomeFrequency());
		if (mode === 'incomeToTax') {
			this.periodicValue.set(this.annualIncome() / periods);
		} else {
			this.periodicValue.set(this.pitResult().totalTax / periods);
		}
	}

	protected setFrequency(freq: IncomeFrequency): void {
		const oldPeriods = this.getPeriods(this.incomeFrequency());
		const newPeriods = this.getPeriods(freq);

		// Adjust periodic value so the annual amount stays the same
		this.periodicValue.set((this.periodicValue() * oldPeriods) / newPeriods);
		this.incomeFrequency.set(freq);
	}

	protected onPeriodicInputChange(value: number): void {
		this.periodicValue.set(value);
		const periods = this.getPeriods(this.incomeFrequency());
		const annualValue = value * periods;

		if (this.calculationMode() === 'incomeToTax') {
			this.annualIncome.set(annualValue);
		} else {
			// Extrapolate income from tax
			const extrapolatedIncome = calculateIncomeFromTax(
				annualValue,
				Const.NIGERIA_PIT_BANDS_2026,
			);
			this.annualIncome.set(extrapolatedIncome);
		}
	}

	protected setEditingField(field: string | null): void {
		this.editingField.set(field);
	}

	protected getPeriods(freq: IncomeFrequency): number {
		return Const.INCOME_FREQUENCY_OPTIONS.find((o) => o.value === freq)?.periods || 1;
	}
}
