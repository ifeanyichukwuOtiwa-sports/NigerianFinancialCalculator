import { Component, model, signal, computed } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CurrencyPipe } from '@angular/common';
import { CompoundingFrequency, FrequencyOption, TaxStrategy, TaxStrategyOption } from '../../app.types';
import * as Const from '../../app.constants';

@Component({
  selector: 'app-investment-form',
  standalone: true,
  imports: [FormsModule, CurrencyPipe],
  templateUrl: './investment-form.component.html',
  styleUrl: './investment-form.component.scss'
})
export class InvestmentFormComponent {
  // --- Two-way State (Model Signals) ---
  principal = model.required<number>();
  rate = model.required<number>();
  years = model.required<number>();
  monthly = model.required<number>();
  frequency = model.required<CompoundingFrequency>();

  // --- UI Configuration ---
  protected readonly frequencyOptions: FrequencyOption[] = Const.FREQUENCY_OPTIONS;
  protected readonly maxPrincipal = Const.MAX_PRINCIPAL;

  protected readonly activeFrequencyLabel = computed(() => {
    return this.frequencyOptions.find(o => o.value === this.frequency())?.label || 'Monthly';
  });

  // --- Local UI State ---
  protected readonly editingField = signal<string | null>(null);

  protected setEditingField(field: string | null): void {
    this.editingField.set(field);
  }
}
