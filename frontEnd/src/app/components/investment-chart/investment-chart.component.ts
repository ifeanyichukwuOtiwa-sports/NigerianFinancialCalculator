import {
	Component,
	ElementRef,
	input,
	OnDestroy,
	output,
	ViewChild,
	effect,
	AfterViewInit,
	Inject,
	PLATFORM_ID,
	signal,
	inject,
} from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { Chart } from 'chart.js/auto';
import { ThemeService } from '../../services/theme.service';

@Component({
	selector: 'app-investment-chart',
	standalone: true,
	imports: [],
	templateUrl: './investment-chart.component.html',
	styleUrl: './investment-chart.component.scss'
})
export class InvestmentChartComponent implements AfterViewInit, OnDestroy {
	@ViewChild('chartCanvas') protected readonly chartCanvas!: ElementRef<HTMLCanvasElement>;

	// --- Inputs ---
	labels = input.required<string[]>();
	investedData = input.required<number[]>();
	interestData = input.required<number[]>();

	// --- Outputs ---
	yearSelected = output<number | null>();

	private chart: Chart | null = null;
	private readonly isBrowser: boolean;
	private readonly chartReady = signal(false);
	private readonly themeService = inject(ThemeService);

	constructor(@Inject(PLATFORM_ID) private platformId: object) {
		this.isBrowser = isPlatformBrowser(this.platformId);
		this.setupChartLogic();
	}

	ngAfterViewInit(): void {
		if (this.isBrowser) {
			this.initChart();
		}
	}

	ngOnDestroy(): void {
		this.chart?.destroy();
		this.chart = null;
	}

	private getThemeColors() {
		if (!this.isBrowser) return { primary: '#10b981', secondary: '#3b82f6', muted: '#64748b', grid: 'rgba(0,0,0,0.05)' };
		const styles = getComputedStyle(document.documentElement);
		return {
			primary: styles.getPropertyValue('--primary').trim() || '#10b981',
			secondary: styles.getPropertyValue('--secondary').trim() || '#3b82f6',
			muted: styles.getPropertyValue('--text-muted').trim() || '#64748b',
			grid: styles.getPropertyValue('--chart-grid').trim() || 'rgba(0,0,0,0.05)',
		};
	}

	private setupChartLogic(): void {
		// Reactive effect to keep chart in sync with application signals
		effect(() => {
			if (!this.isBrowser || !this.chartReady()) return;

			// Synchronously read signals to ensure correct reactive tracking
			const labels = this.labels();
			const invested = this.investedData();
			const interest = this.interestData();

			// Subscribe to theme and intensity changes
			this.themeService.getEffectiveTheme()();
			this.themeService.getTimeIntensity()();

			const chartInstance = this.chart;
			if (!chartInstance) return;

			// Small delay to ensure the DOM attribute 'data-theme' has been applied
			// and CSS variables have been recalculated by the browser.
			Promise.resolve().then(() => {
				const colors = this.getThemeColors();

				// Update colors based on current theme
				chartInstance.data.datasets[0].backgroundColor = colors.secondary;
				chartInstance.data.datasets[1].backgroundColor = colors.primary;

				if (chartInstance.options.scales?.['x']) {
					chartInstance.options.scales['x'].grid!.color = colors.grid;
					chartInstance.options.scales['x'].ticks!.color = colors.muted;
				}
				if (chartInstance.options.scales?.['y']) {
					chartInstance.options.scales['y'].grid!.color = colors.grid;
					chartInstance.options.scales['y'].ticks!.color = colors.muted;
				}

				// Update chart data and labels
				chartInstance.data.labels = labels;
				chartInstance.data.datasets[0].data = invested;
				chartInstance.data.datasets[1].data = interest;

				chartInstance.update();
			});
		});
	}

	private initChart(): void {
		const canvas = this.chartCanvas?.nativeElement;
		if (!canvas) return;

		const colors = this.getThemeColors();

		this.chart = new Chart(canvas, {
			type: 'bar',
			data: {
				labels: this.labels(),
				datasets: [
					{
						label: 'Total Invested',
						data: this.investedData(),
						backgroundColor: colors.secondary,
						stack: 's',
						borderRadius: 0,
					},
					{
						label: 'Interest earned',
						data: this.interestData(),
						backgroundColor: colors.primary,
						stack: 's',
						borderRadius: 0,
					},
				],
			},
			options: {
				onClick: (_, elements) => {
					this.yearSelected.emit(elements.length > 0 ? elements[0].index : null);
				},
				animation: {
					duration: 800,
					easing: 'easeOutQuart',
				},
				responsive: true,
				maintainAspectRatio: false,
				plugins: {
					legend: { display: false },
					tooltip: {
						callbacks: {
							label: (ctx) => `${ctx.dataset.label}: ₦${Math.round(ctx.raw as number).toLocaleString()}`,
						},
					},
				},
				scales: {
					x: {
						stacked: true,
						grid: { color: colors.grid },
						ticks: {
							color: colors.muted,
							callback: (_, index) => {
								const labels = this.labels();
								return index % 2 === 0 ? (labels[index] || '') : '';
							},
						},
					},
					y: {
						stacked: true,
						grid: { color: colors.grid },
						min: 0,
						grace: '10%',
						ticks: {
							color: colors.muted,
							callback: (v) => '₦' + (Number(v) >= 1000 ? (Number(v) / 1000).toFixed(0) + 'k' : v),
						},
					},
				},
			},
		});

		// Mark the chart as ready to trigger reactive updates
		this.chartReady.set(true);
	}
}
