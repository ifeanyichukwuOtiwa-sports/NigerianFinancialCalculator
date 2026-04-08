import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import {
	CompoundingFrequency,
	Milestone,
	TaxStrategy,
	YearBreakdown,
} from '@app/shared/types/app.types';
import { calculateFutureBalance } from '@app/shared/utils/app.utils';
import { ScenarioApiService } from '@app/features/scenarios/scenario-api.service';
import * as Const from '@app/shared/utils/app.constants';

import { InvestmentFormComponent } from './components/investment-form.component';
import { SummaryMetricsComponent } from './components/summary-metrics.component';
import { YearBreakdownComponent } from './components/year-breakdown.component';
import { MilestonesComponent } from './components/milestones.component';
import { InvestmentChartComponent } from './components/investment-chart.component';
import { ScenarioResponse } from '@app/shared/types/scenario.types';

@Component({
	selector: 'app-calculator-page',
	imports: [
		InvestmentFormComponent,
		SummaryMetricsComponent,
		YearBreakdownComponent,
		MilestonesComponent,
		InvestmentChartComponent,
	],
	changeDetection: ChangeDetectionStrategy.OnPush,
	templateUrl: './calculator.component.html',
})
export class CalculatorComponent {
	private readonly scenarioApi = inject(ScenarioApiService);
	private readonly router = inject(Router);

	protected principal = signal(Const.DEFAULT_PRINCIPAL);
	protected rate = signal(Const.DEFAULT_RATE);
	protected years = signal(Const.DEFAULT_YEARS);
	protected monthly = signal(Const.DEFAULT_MONTHLY);
	protected frequency = signal<CompoundingFrequency>(Const.DEFAULT_FREQUENCY);
	protected taxStrategy = signal<TaxStrategy>('wht');
	protected annualIncome = signal(Const.DEFAULT_ANNUAL_INCOME);
	protected selectedYear = signal<number | null>(null);

	protected scenarioSaving = signal(false);
	protected scenarioMessage = signal<string | null>(null);

	constructor() {
		const nav = this.router.currentNavigation();

		const scenario = nav?.extras?.state?.['scenario'] as ScenarioResponse | undefined;
		if (scenario) {
			this.principal.set(scenario.principal);
			this.rate.set(scenario.annualRate);
			this.years.set(scenario.years);
			this.monthly.set(scenario.monthlyContribution);
			this.frequency.set(scenario.compoundingFrequency);
			this.taxStrategy.set(scenario.taxStrategy);
			this.annualIncome.set(scenario.annualIncome);
		}
	}

	protected readonly periods = computed(() => {
		const f = this.frequency();
		return Const.FREQUENCY_OPTIONS.find((o) => o.value === f)?.periods || 12;
	});

	protected readonly results = computed(() => {
		return calculateFutureBalance(
			this.principal(),
			this.rate(),
			this.years(),
			this.monthly(),
			this.periods(),
			this.taxStrategy(),
			this.annualIncome(),
		);
	});

	protected readonly balance = computed(() => this.results().totalBalance);
	protected readonly totalInvested = computed(() => this.results().totalInvested);
	protected readonly interestEarned = computed(() => this.results().netInterest);

	protected readonly selectedYearData = computed<YearBreakdown | null>(() => {
		const year = this.selectedYear();
		if (year === null) return null;
		return {
			year,
			...calculateFutureBalance(
				this.principal(),
				this.rate(),
				year,
				this.monthly(),
				this.periods(),
				this.taxStrategy(),
				this.annualIncome(),
			),
		};
	});

	protected readonly milestones = computed<Milestone[]>(() => {
		const P = this.principal();
		if (P <= 0) return []; // No milestones for zero principal

		const years = this.years();
		const results: Milestone[] = [];
		const targets = Const.MILESTONE_TARGETS;
		let targetIndex = 0;

		// Calculate Year 0 balance (just Principal) to skip already achieved milestones
		const initialBalance = P;
		while (targetIndex < targets.length && initialBalance >= P * targets[targetIndex]) {
			targetIndex++;
		}

		for (let y = 1; y <= years; y++) {
			const res = calculateFutureBalance(
				P,
				this.rate(),
				y,
				this.monthly(),
				this.periods(),
				this.taxStrategy(),
				this.annualIncome(),
			);
			const bal = res.netBalance;
			while (targetIndex < targets.length && bal >= P * targets[targetIndex]) {
				results.push({
					year: y,
					label: `${targets[targetIndex]}x Principal`,
					value: Math.round(bal),
				});
				targetIndex++;
			}
		}
		return results;
	});

	protected readonly investedData = computed(() => {
		return Array.from(
			{ length: this.years() + 1 },
			(_, y) =>
				calculateFutureBalance(
					this.principal(),
					this.rate(),
					y,
					this.monthly(),
					this.periods(),
					this.taxStrategy(),
					this.annualIncome(),
				).totalInvested,
		);
	});

	protected readonly interestData = computed(() => {
		return Array.from(
			{ length: this.years() + 1 },
			(_, y) =>
				calculateFutureBalance(
					this.principal(),
					this.rate(),
					y,
					this.monthly(),
					this.periods(),
					this.taxStrategy(),
					this.annualIncome(),
				).netInterest,
		);
	});

	protected readonly chartLabels = computed(() =>
		Array.from({ length: this.years() + 1 }, (_, y) => (y === 0 ? 'Now' : `Y${y}`)),
	);

	protected saveScenario(name: string): void {
		this.scenarioSaving.set(true);
		this.scenarioMessage.set(null);

		this.scenarioApi
			.save({
				name,
				principal: this.principal(),
				annualRate: this.rate(),
				years: this.years(),
				monthlyContribution: this.monthly(),
				compoundingFrequency: this.frequency(),
				taxStrategy: this.taxStrategy(),
				annualIncome: this.annualIncome(),
			})
			.subscribe({
				next: () => {
					this.scenarioSaving.set(false);
					this.scenarioMessage.set('Scenario saved successfully!');
				},
				error: (err) => {
					this.scenarioSaving.set(false);
					this.scenarioMessage.set(err.error?.message ?? 'Failed to save scenario.');
				},
			});
	}
}
