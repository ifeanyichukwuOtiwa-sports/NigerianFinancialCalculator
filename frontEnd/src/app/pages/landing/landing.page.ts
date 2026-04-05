import { Component, inject, ChangeDetectionStrategy } from '@angular/core';
import { NavigationService } from '../../services/navigation.service';

@Component({
	selector: 'app-landing-page',
	changeDetection: ChangeDetectionStrategy.OnPush,
	template: `
		<main class="landing">
			<div class="landing-hero glass-card">
				<h2 class="landing-title gradient-text">Nigerian Financial Calculator</h2>
				<p class="landing-subtitle">Plan your financial future with tax-aware compound interest calculations, built for Nigeria's 2026 tax framework.</p>
				<div class="landing-actions">
					<button class="btn-primary btn-lg" (click)="openAuth('register')">Get Started</button>
					<button class="btn-secondary btn-lg" (click)="openAuth('login')">I have an account</button>
				</div>
			</div>
		</main>
	`,
	styleUrl: './landing.page.scss',
})
export class LandingPage {
	private readonly navService = inject(NavigationService);

	protected openAuth(mode: 'login' | 'register'): void {
		this.navService.openAuth(mode);
	}
}
