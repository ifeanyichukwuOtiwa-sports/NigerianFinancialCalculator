import { Component, inject, ChangeDetectionStrategy } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { NavigationService } from '@app/shared/services/navigation.service';
import { NavbarComponent } from '@app/layout/navbar/navbar.component';
import { AuthComponent } from '@app/features/auth/auth-components/auth.component';
import { AuthService } from '@app/shared/services/auth.service';

@Component({
	selector: 'app-root',
	imports: [RouterOutlet, NavbarComponent, AuthComponent],
	changeDetection: ChangeDetectionStrategy.OnPush,
	templateUrl: './app.component.html',
	styleUrl: './app.component.scss',
})
export class AppComponent {
	private readonly navService = inject(NavigationService);
	private readonly authService = inject(AuthService);

	protected readonly authModal = this.navService.authModal;

	constructor() {
		// Restore the session after first render instead of blocking app bootstrap.
		this.authService.checkSession().subscribe();
	}
}
