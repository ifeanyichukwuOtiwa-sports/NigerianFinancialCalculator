import { Component, input, output } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { YearBreakdown } from '../../app.types';

@Component({
  selector: 'app-year-breakdown',
  imports: [CurrencyPipe],
  templateUrl: './year-breakdown.component.html',
  styleUrl: './year-breakdown.component.scss'
})
export class YearBreakdownComponent {
  data = input.required<YearBreakdown>();
  close = output<void>();
}
