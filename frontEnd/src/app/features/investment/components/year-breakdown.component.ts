import { Component, input, output } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { YearBreakdown } from '@app/shared/types/app.types';

@Component({
	selector: 'app-year-breakdown',
	imports: [CurrencyPipe],
	templateUrl: './year-breakdown.component.html',
	styleUrl: './year-breakdown.component.scss',
})
export class YearBreakdownComponent {
	data = input.required<YearBreakdown>();
	dismiss = output<void>();
}
