import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';

export const appComponentConfig: ApplicationConfig = {
	providers: [
		provideBrowserGlobalErrorListeners(),
	]
};
