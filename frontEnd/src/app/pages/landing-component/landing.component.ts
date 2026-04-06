import { Component, inject, ChangeDetectionStrategy } from '@angular/core';
import { NavigationService } from '@app/services/navigation.service';

@Component({
	selector: 'app-component-page',
	changeDetection: ChangeDetectionStrategy.OnPush,
	templateUrl: './landing.component.html',
	styleUrl: './landing.component.scss',
})
export class LandingComponent {
	private readonly navService = inject(NavigationService);

	protected openAuth(mode: 'login' | 'register'): void {
		this.navService.openAuth(mode);
	}
}
