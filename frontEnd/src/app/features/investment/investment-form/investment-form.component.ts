import { Component, model, signal, computed, input, output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CurrencyPipe } from '@angular/common';
import { CompoundingFrequency, FrequencyOption } from '@app/shared/types/app.types';
import * as Const from '@app/shared/utils/app.constants';

@Component({
	selector: 'app-investment-form',
	imports: [FormsModule, CurrencyPipe],
	templateUrl: './investment-form.component.html',
	styleUrl: './investment-form.component.scss',
})
export class InvestmentFormComponent {
	// --- Two-way State (Model Signals) ---
	principal = model.required<number>();
	rate = model.required<number>();
	years = model.required<number>();
	monthly = model.required<number>();
	frequency = model.required<CompoundingFrequency>();

	// --- Save Scenario ---
	saving = input(false);
	saveMessage = input<string | null>(null);
	saveScenario = output<string>();

	// --- UI Configuration ---
	protected readonly frequencyOptions: FrequencyOption[] = Const.FREQUENCY_OPTIONS;
	protected readonly maxPrincipal = Const.MAX_PRINCIPAL;

	protected readonly activeFrequencyLabel = computed(() => {
		return this.frequencyOptions.find((o) => o.value === this.frequency())?.label || 'Monthly';
	});

	// --- Local UI State ---
	protected readonly editingField = signal<string | null>(null);
	protected readonly showSaveForm = signal(false);
	protected readonly scenarioName = signal('');

	protected setEditingField(field: string | null): void {
		this.editingField.set(field);
	}

	protected toggleSaveForm(): void {
		this.showSaveForm.update((v) => !v);
	}

	protected onSave(): void {
		const name = this.scenarioName().trim();
		if (!name) return;
		this.saveScenario.emit(name);
		this.scenarioName.set('');
		this.showSaveForm.set(false);
	}
}
