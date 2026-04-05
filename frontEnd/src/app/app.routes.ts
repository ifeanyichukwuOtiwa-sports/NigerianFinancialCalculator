import { Routes } from '@angular/router';
import { authGuard } from './guards/auth.guard';

export const appRoutes: Routes = [
	{
		path: '',
		loadComponent: () => import('./pages/landing/landing.page').then(m => m.LandingPage),
	},
	{
		path: 'calculator',
		loadComponent: () => import('./pages/calculator/calculator.page').then(m => m.CalculatorPage),
		canActivate: [authGuard],
	},
	{
		path: 'tax',
		loadComponent: () => import('./pages/tax/tax.page').then(m => m.TaxPage),
		canActivate: [authGuard],
	},
	{
		path: 'scenarios',
		loadComponent: () => import('./pages/scenarios/scenarios.page').then(m => m.ScenariosPage),
		canActivate: [authGuard],
	},
	{
		path: '**',
		redirectTo: '',
	},
];
