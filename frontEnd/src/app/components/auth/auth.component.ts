import { Component, signal, inject, ChangeDetectionStrategy, ElementRef, viewChild, afterNextRender } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { NavigationService } from '../../services/navigation.service';

@Component({
	selector: 'app-auth',
	imports: [ReactiveFormsModule],
	changeDetection: ChangeDetectionStrategy.OnPush,
	templateUrl: './auth.component.html',
	styleUrl: './auth.component.scss'
})
export class AuthComponent {
	private readonly fb = inject(FormBuilder);
	private readonly authService = inject(AuthService);
	private readonly navService = inject(NavigationService);
	private readonly router = inject(Router);

	/** Element that opened the modal — focus returns here on close */
	private triggerElement: HTMLElement | null = null;

	protected readonly activeTab = signal<'login' | 'register'>('login');
	protected readonly loading = signal(false);
	protected readonly errorMessage = signal<string | null>(null);
	protected readonly successMessage = signal<string | null>(null);

	protected readonly dialogRef = viewChild<ElementRef<HTMLDialogElement>>('authDialog');

	constructor() {
		const mode = this.navService.authModal();
		if (mode) {
			this.activeTab.set(mode);
		}

		// Capture the currently focused element (the trigger button) before the modal renders
		this.triggerElement = document.activeElement as HTMLElement | null;

		// After the modal renders, open it as a modal using the native showModal() for accessibility
		afterNextRender(() => {
			this.dialogRef()?.nativeElement.showModal();
		});
	}

	protected readonly loginForm = this.fb.nonNullable.group({
		email: ['', [Validators.required, Validators.email]],
		password: ['', [Validators.required]]
	});

	protected readonly registerForm = this.fb.nonNullable.group({
		fullName: ['', [Validators.required]],
		email: ['', [Validators.required, Validators.email]],
		password: ['', [Validators.required, Validators.minLength(6)]]
	});

	protected close(): void {
		this.dialogRef()?.nativeElement.close();
	}

	protected onDialogClose(): void {
		this.navService.closeAuth();

		// Restore focus to the element that triggered the modal
		this.triggerElement?.focus();
	}

	protected onOverlayClick(event: MouseEvent): void {
		if (event.target === event.currentTarget) {
			this.close();
		}
	}

	protected switchTab(tab: 'login' | 'register'): void {
		this.activeTab.set(tab);
		this.errorMessage.set(null);
		if (tab !== 'login') {
			this.successMessage.set(null);
		}
	}

	protected onLogin(): void {
		if (this.loginForm.invalid) {
			this.loginForm.markAllAsTouched();
			return;
		}

		this.loading.set(true);
		this.errorMessage.set(null);

		const { email, password } = this.loginForm.getRawValue();
		this.authService.login({ email, password }).subscribe({
			next: () => {
				this.loading.set(false);
				this.close();
				this.router.navigate(['/calculator']);
			},
			error: (err) => {
				this.loading.set(false);
				this.errorMessage.set(err.error?.message ?? 'Login failed. Please check your credentials.');
			}
		});
	}

	protected onRegister(): void {
		if (this.registerForm.invalid) {
			this.registerForm.markAllAsTouched();
			return;
		}

		this.loading.set(true);
		this.errorMessage.set(null);

		const { fullName, email, password } = this.registerForm.getRawValue();
		this.authService.register({ fullName, email, password }).subscribe({
			next: () => {
				this.loading.set(false);
				this.successMessage.set('Registration successful! Please log in.');
				this.registerForm.reset();
				this.activeTab.set('login');
			},
			error: (err) => {
				this.loading.set(false);
				this.errorMessage.set(err.error?.message ?? 'Registration failed. Please try again.');
			}
		});
	}
}
