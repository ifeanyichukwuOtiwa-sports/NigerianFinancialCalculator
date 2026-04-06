import { Component, inject, ChangeDetectionStrategy } from '@angular/core';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { ThemeService } from '@app/services/theme.service';
import { NavigationService } from '@app/services/navigation.service';
import { AuthService } from '@app/services/auth.service';
import * as Const from '../../app.constants';
import { Theme } from '@app/types/theme.types';

@Component({
	selector: 'app-navbar',
	imports: [RouterLink, RouterLinkActive],
	changeDetection: ChangeDetectionStrategy.OnPush,
	templateUrl: './navbar.component.html',
	styleUrl: './navbar.component.scss'
})
export class NavbarComponent {
	private readonly themeService = inject(ThemeService);
	private readonly navService = inject(NavigationService);
	private readonly authService = inject(AuthService);
	private readonly router = inject(Router);

	protected readonly title = Const.APP_TITLE;
	protected readonly theme = this.themeService.getTheme();
	protected readonly isLoggedIn = this.authService.isLoggedIn;
	protected readonly user = this.authService.user;

	protected setTheme(theme: Theme): void {
		this.themeService.setTheme(theme);
	}

	protected openAuth(mode: 'login' | 'register'): void {
		this.navService.openAuth(mode);
	}

	protected logout(): void {
		this.authService.logout().subscribe(() => {
			this.router.navigate(['/']);
		});
	}
}
