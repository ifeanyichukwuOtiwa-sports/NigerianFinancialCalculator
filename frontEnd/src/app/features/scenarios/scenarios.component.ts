import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { CurrencyPipe } from '@angular/common';
import { ScenarioApiService } from '@app/shared/services/scenario-api.service';
import { ScenarioResponse } from '@app/shared/types/scenario.types';

@Component({
	selector: 'app-scenarios-page',
	imports: [CurrencyPipe],
	changeDetection: ChangeDetectionStrategy.OnPush,
	templateUrl: './scenarios.component.html',
	styleUrl: './scenarios.component.scss',
})
export class ScenariosComponent {
	private readonly scenarioApi = inject(ScenarioApiService);
	private readonly router = inject(Router);

	protected readonly scenarios = signal<ScenarioResponse[]>([]);
	protected readonly loading = signal(false);
	protected readonly errorMessage = signal<string | null>(null);

	protected readonly taxScenarios = computed(() =>
		this.scenarios().filter((s) => s.principal === 0 && s.annualRate === 0),
	);

	protected readonly investmentScenarios = computed(() =>
		this.scenarios().filter((s) => s.principal !== 0 || s.annualRate !== 0),
	);

	protected readonly selectedIds = signal<Set<number>>(new Set());
	protected readonly selectedScenarios = computed(() =>
		this.scenarios().filter((s) => this.selectedIds().has(s.id)),
	);
	protected readonly showComparison = signal(false);

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
		const isTaxPlan = scenario.principal === 0 && scenario.annualRate === 0;
		const targetRoute = isTaxPlan ? '/tax' : '/calculator';
		this.router.navigate([targetRoute], { state: { scenario } });
	}

	protected onDelete(scenario: ScenarioResponse): void {
		this.scenarioApi.delete(scenario.id).subscribe({
			next: () => {
				this.scenarios.update((list) => list.filter((s) => s.id !== scenario.id));
			},
			error: () => {
				this.errorMessage.set('Failed to delete scenario.');
			},
		});
	}

	protected onExportPdf(scenario: ScenarioResponse): void {
		this.scenarioApi.exportPdf(scenario.id).subscribe({
			next: (blob) => this.downloadFile(blob, `scenario_${scenario.name}.pdf`),
			error: () => this.errorMessage.set('Failed to export PDF.'),
		});
	}

	protected onExportCsv(scenario: ScenarioResponse): void {
		this.scenarioApi.exportCsv(scenario.id).subscribe({
			next: (blob) => this.downloadFile(blob, `scenario_${scenario.name}.csv`),
			error: () => this.errorMessage.set('Failed to export CSV.'),
		});
	}

	protected toggleSelection(id: number): void {
		this.selectedIds.update((ids) => {
			const next = new Set(ids);
			if (next.has(id)) {
				next.delete(id);
			} else {
				next.add(id);
			}
			return next;
		});
	}

	protected compare(): void {
		if (this.selectedIds().size >= 2) {
			this.showComparison.set(true);
		}
	}

	private downloadFile(blob: Blob, filename: string): void {
		const url = window.URL.createObjectURL(blob);
		const link = document.createElement('a');
		link.href = url;
		link.download = filename;
		link.click();
		window.URL.revokeObjectURL(url);
	}
}
