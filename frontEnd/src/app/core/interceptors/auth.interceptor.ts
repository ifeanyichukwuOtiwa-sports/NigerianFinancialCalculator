import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { tap } from 'rxjs';
import { AuthService } from '@app/shared/services/auth.service';
import { NavigationService } from '@app/shared/services/navigation.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
	const authService = inject(AuthService);
	const navService = inject(NavigationService);
	const router = inject(Router);

	return next(req).pipe(
		tap({
			error: (error: HttpErrorResponse) => {
				// Don't handle 401 on auth endpoints (login/register/me) — those are expected
				if (error.status === 401 && !req.url.includes('/api/auth/')) {
					authService.clearSession();
					navService.closeAuth();
					router.navigate(['/']);
				}
			},
		}),
	);
};
