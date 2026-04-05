import { Injectable, signal, computed, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap, catchError, of } from 'rxjs';

export interface AuthUser {
	id: number;
	email: string;
	fullName: string;
}

export interface LoginRequest {
	email: string;
	password: string;
}

export interface RegisterRequest {
	email: string;
	password: string;
	fullName: string;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
	private readonly http = inject(HttpClient);
	private readonly baseUrl = '/api/auth';

	private readonly currentUser = signal<AuthUser | null>(null);

	readonly user = this.currentUser.asReadonly();
	readonly isLoggedIn = computed(() => this.currentUser() !== null);

	checkSession(): Observable<AuthUser | null> {
		return this.http.get<AuthUser>(`${this.baseUrl}/me`, { withCredentials: true }).pipe(
			tap(user => this.currentUser.set(user)),
			catchError(() => {
				this.currentUser.set(null);
				return of(null);
			})
		);
	}

	clearSession(): void {
		this.currentUser.set(null);
	}

	register(request: RegisterRequest): Observable<AuthUser> {
		return this.http.post<AuthUser>(`${this.baseUrl}/register`, request, { withCredentials: true });
	}

	login(request: LoginRequest): Observable<AuthUser> {
		return this.http.post<AuthUser>(`${this.baseUrl}/login`, request, { withCredentials: true }).pipe(
			tap(user => this.currentUser.set(user))
		);
	}

	logout(): Observable<void> {
		return this.http.post<void>(`${this.baseUrl}/logout`, {}, { withCredentials: true }).pipe(
			tap(() => this.currentUser.set(null))
		);
	}
}
