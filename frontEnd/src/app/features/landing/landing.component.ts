import { Component, inject, ChangeDetectionStrategy } from '@angular/core';
import { RouterLink } from '@angular/router';
import { NavigationService } from '@app/shared/services/navigation.service';
import { AuthService } from '@app/shared/services/auth.service';

@Component({
	selector: 'app-component-page',
	imports: [RouterLink],
	changeDetection: ChangeDetectionStrategy.OnPush,
	templateUrl: './landing.component.html',
	styleUrl: './landing.component.scss',
})
export class LandingComponent {
	private readonly navService = inject(NavigationService);
	private readonly authService = inject(AuthService);

	protected readonly isLoggedIn = this.authService.isLoggedIn;
	protected readonly user = this.authService.user;

	protected openAuth(mode: 'login' | 'register'): void {
		this.navService.openAuth(mode);
	}
}
