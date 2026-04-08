import { Component, input } from '@angular/core';
import { CurrencyPipe } from '@angular/common';

@Component({
	selector: 'app-summary-metrics',
	imports: [CurrencyPipe],
	templateUrl: './summary-metrics.component.html',
	styleUrl: './summary-metrics.component.scss',
})
export class SummaryMetricsComponent {
	totalInvested = input.required<number>();
	interestEarned = input.required<number>();
	balance = input.required<number>();
}
