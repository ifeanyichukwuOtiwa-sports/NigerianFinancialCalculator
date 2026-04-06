import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { CurrencyPipe } from '@angular/common';
import { ScenarioApiService } from '@app/services/scenario-api.service';
import { ScenarioResponse } from '@app/types/scenario.types';

@Component({
	selector: 'app-scenarios-page',
	imports: [CurrencyPipe],
	changeDetection: ChangeDetectionStrategy.OnPush,
	templateUrl: './scenarios.component.html',
	styleUrl: '../../components/my-scenarios/my-scenarios.component.scss',
})
export class ScenariosComponent {
	private readonly scenarioApi = inject(ScenarioApiService);
	private readonly router = inject(Router);

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
			},
		});
	}

	protected onLoad(scenario: ScenarioResponse): void {
		this.router.navigate(['/calculator'], { state: { scenario } });
	}

	protected onDelete(scenario: ScenarioResponse): void {
		this.scenarioApi.delete(scenario.id).subscribe({
			next: () => {
				this.scenarios.update((list) => list.filter((s) => s.id !== scenario.id));
			},
			error: (err) => {
				this.errorMessage.set(err.error?.message ?? 'Failed to delete scenario.');
			},
		});
	}
}
