import { Component, inject, ChangeDetectionStrategy } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { NavigationService } from './services/navigation.service';
import { NavbarComponent } from './layout/navbar/navbar.component';
import { AuthComponent } from './components/auth/auth.component';

@Component({
	selector: 'app-root',
	imports: [RouterOutlet, NavbarComponent, AuthComponent],
	changeDetection: ChangeDetectionStrategy.OnPush,
	templateUrl: './app.component.html',
	styleUrl: './app.component.scss',
})
export class AppComponent {
	private readonly navService = inject(NavigationService);

	protected readonly authModal = this.navService.authModal;
}
