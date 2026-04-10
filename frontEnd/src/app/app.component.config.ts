import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { appRoutes } from './app.routes';
import { brandInterceptor } from './core/interceptors/brand.interceptor';
import { authInterceptor } from './core/interceptors/auth.interceptor';

export const appComponentConfig: ApplicationConfig = {
	providers: [
		provideBrowserGlobalErrorListeners(),
		provideRouter(appRoutes, withComponentInputBinding()),
		provideHttpClient(withInterceptors([brandInterceptor, authInterceptor])),
	],
};
