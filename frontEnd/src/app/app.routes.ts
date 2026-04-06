import { Routes } from '@angular/router';
import { authGuard } from './guards/auth.guard';

export const appRoutes: Routes = [
	{
		path: '',
		loadComponent: () => import('@app/pages/landing-component/landing.component').then((m) => m.LandingComponent),
	},
	{
		path: 'calculator',
		loadComponent: () =>
			import('@app/pages/calculator-component/calculator.component').then((m) => m.CalculatorComponent),
		canActivate: [authGuard],
	},
	{
		path: 'tax',
		loadComponent: () => import('./pages/tax/tax.page').then((m) => m.TaxPage),
		canActivate: [authGuard],
	},
	{
		path: 'scenarios',
		loadComponent: () =>
			import('@app/pages/scenarios-component/scenarios.component').then((m) => m.ScenariosComponent),
		canActivate: [authGuard],
	},
	{
		path: '**',
		redirectTo: '',
	},
];
