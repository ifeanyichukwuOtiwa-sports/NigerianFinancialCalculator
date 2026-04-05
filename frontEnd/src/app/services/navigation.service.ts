import { Injectable, signal } from '@angular/core';

export type AuthModal = 'login' | 'register' | null;

@Injectable({ providedIn: 'root' })
export class NavigationService {
	readonly authModal = signal<AuthModal>(null);

	openAuth(mode: 'login' | 'register'): void {
		this.authModal.set(mode);
	}

	closeAuth(): void {
		this.authModal.set(null);
	}
}
