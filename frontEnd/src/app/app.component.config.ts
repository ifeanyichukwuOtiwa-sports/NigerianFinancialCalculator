import { ApplicationConfig, provideAppInitializer, provideBrowserGlobalErrorListeners, inject } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { appRoutes } from './app.routes';
import { brandInterceptor } from './interceptors/brand.interceptor';
import { authInterceptor } from './interceptors/auth.interceptor';
import { AuthService } from './services/auth.service';

export const appComponentConfig: ApplicationConfig = {
	providers: [
		provideBrowserGlobalErrorListeners(),
		provideRouter(appRoutes, withComponentInputBinding()),
		provideHttpClient(withInterceptors([brandInterceptor, authInterceptor])),
		provideAppInitializer(() => {
			const authService = inject(AuthService);
			return firstValueFrom(authService.checkSession());
		})
	]
};
