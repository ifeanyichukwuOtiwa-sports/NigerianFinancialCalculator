import { Component, signal, inject, output, ChangeDetectionStrategy } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { ScenarioApiService, ScenarioResponse } from '../../services/scenario-api.service';

@Component({
	selector: 'app-my-scenarios',
	imports: [CurrencyPipe],
	changeDetection: ChangeDetectionStrategy.OnPush,
	templateUrl: './my-scenarios.component.html',
	styleUrl: './my-scenarios.component.scss'
})
export class MyScenariosComponent {
	private readonly scenarioApi = inject(ScenarioApiService);

	readonly loadScenario = output<ScenarioResponse>();

	protected readonly scenarios = signal<ScenarioResponse[]>([]);
	protected readonly loading = signal(false);
	protected readonly errorMessage = signal<string | null>(null);

	constructor() {
		this.refresh();
	}

	protected refresh(): void {
		this.loading.set(true);
		this.errorMessage.set(null);

		this.scenarioApi.list().subscribe({
			next: (data) => {
				this.scenarios.set(data);
				this.loading.set(false);
			},
			error: (err) => {
				this.errorMessage.set(err.error?.message ?? 'Failed to load scenarios.');
				this.loading.set(false);
			}
		});
	}

	protected onLoad(scenario: ScenarioResponse): void {
		this.loadScenario.emit(scenario);
	}

	protected onDelete(scenario: ScenarioResponse): void {
		this.scenarioApi.delete(scenario.id).subscribe({
			next: () => {
				this.scenarios.update(list => list.filter(s => s.id !== scenario.id));
			},
			error: (err) => {
				this.errorMessage.set(err.error?.message ?? 'Failed to delete scenario.');
			}
		});
	}
}
