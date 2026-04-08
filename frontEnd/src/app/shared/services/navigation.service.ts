import { Injectable, signal } from '@angular/core';
import { AuthModal } from '@app/shared/types/nav.types';

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
