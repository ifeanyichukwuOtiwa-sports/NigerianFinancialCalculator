import { Component, ChangeDetectionStrategy } from '@angular/core';
import { TaxCalculatorComponent } from '../../components/tax-calculator/tax-calculator.component';

@Component({
	selector: 'app-tax-page',
	imports: [TaxCalculatorComponent],
	changeDetection: ChangeDetectionStrategy.OnPush,
	template: `<app-tax-calculator />`,
})
export class TaxPage {}
