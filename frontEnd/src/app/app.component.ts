import {
	Component,
	computed,
	signal,
	inject,
} from '@angular/core';
import { YearBreakdown, Milestone, CompoundingFrequency, TaxStrategy } from './app.types';
import { calculateFutureBalance } from './app.utils';
import { ThemeService } from './services/theme.service';
import { NavigationService } from './services/navigation.service';
import * as Const from './app.constants';

// --- Sub-components ---
import { NavbarComponent } from './layout/navbar/navbar.component';
import { InvestmentFormComponent } from './components/investment-form/investment-form.component';
import { SummaryMetricsComponent } from './components/summary-metrics/summary-metrics.component';
import { YearBreakdownComponent } from './components/year-breakdown/year-breakdown.component';
import { MilestonesComponent } from './components/milestones/milestones.component';
import { InvestmentChartComponent } from './components/investment-chart/investment-chart.component';
import { TaxCalculatorComponent } from './components/tax-calculator/tax-calculator.component';

@Component({
	selector: 'app-root',
	standalone: true,
	imports: [
		NavbarComponent,
		InvestmentFormComponent,
		SummaryMetricsComponent,
		YearBreakdownComponent,
		MilestonesComponent,
		InvestmentChartComponent,
		TaxCalculatorComponent,
	],
	templateUrl: './app.component.html',
	styleUrl: './app.component.scss',
})
export class AppComponent {
	private readonly themeService = inject(ThemeService);
	private readonly navService = inject(NavigationService);

	// --- App State (Signals) ---
	protected readonly activeTab = this.navService.activeTab;
	protected readonly theme = this.themeService.getTheme();

	protected principal = signal(Const.DEFAULT_PRINCIPAL);
	protected rate = signal(Const.DEFAULT_RATE);
	protected years = signal(Const.DEFAULT_YEARS);
	protected monthly = signal(Const.DEFAULT_MONTHLY);
	protected frequency = signal<CompoundingFrequency>(Const.DEFAULT_FREQUENCY);
	protected taxStrategy = signal<TaxStrategy>('wht');
	protected annualIncome = signal(Const.DEFAULT_ANNUAL_INCOME);
	protected selectedYear = signal<number | null>(null);

	protected readonly periods = computed(() => {
		const f = this.frequency();
		return Const.FREQUENCY_OPTIONS.find(o => o.value === f)?.periods || 12;
	});

	// --- Computed Results ---

	protected readonly results = computed(() => {
		return calculateFutureBalance(
			this.principal(),
			this.rate(),
			this.years(),
			this.monthly(),
			this.periods(),
			this.taxStrategy(),
			this.annualIncome()
		);
	});

	// --- Computed Metrics ---

	/**
	 * Final gross balance (before tax) at the end of the full term.
	 */
	protected readonly balance = computed(() => this.results().totalBalance);

	/**
	 * Total amount of money put into the investment.
	 */
	protected readonly totalInvested = computed(() => this.results().totalInvested);

	/**
	 * Total net interest earned (after tax).
	 */
	protected readonly interestEarned = computed(() => this.results().netInterest);

	/**
	 * Breakdown for the currently selected year (from chart interaction).
	 */
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
				this.annualIncome()
			)
		};
	});

	/**
	 * Significant financial milestones where gross balance reaches multiples of the principal.
	 */
	protected readonly milestones = computed<Milestone[]>(() => {
		const P = this.principal();
		const years = this.years();
		const results: Milestone[] = [];
		const targets = Const.MILESTONE_TARGETS;
		let targetIndex = 0;

		for (let y = 1; y <= years; y++) {
			const res = calculateFutureBalance(P, this.rate(), y, this.monthly(), this.periods(), this.taxStrategy(), this.annualIncome());
			const bal = res.netBalance;
			// Find all milestones reached in this year
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

	// --- Chart Data Streams ---

	protected readonly investedData = computed(() => {
		return Array.from({ length: this.years() + 1 }, (_, y) =>
			calculateFutureBalance(this.principal(), this.rate(), y, this.monthly(), this.periods(), this.taxStrategy(), this.annualIncome()).totalInvested
		);
	});

	protected readonly interestData = computed(() => {
		return Array.from({ length: this.years() + 1 }, (_, y) =>
			calculateFutureBalance(this.principal(), this.rate(), y, this.monthly(), this.periods(), this.taxStrategy(), this.annualIncome()).netInterest
		);
	});

	protected readonly chartLabels = computed(() =>
		Array.from({ length: this.years() + 1 }, (_, y) => (y === 0 ? 'Now' : `Y${y}`)),
	);
}
