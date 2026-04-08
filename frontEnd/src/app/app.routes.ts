import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';

export const appRoutes: Routes = [
	{
		path: '',
		loadChildren: () =>
			import('./features/landing/landing.routes').then((m) => m.landingRoutes),
	},
	{
		path: 'calculator',
		loadChildren: () =>
			import('./features/investment/investment.routes').then((m) => m.investmentRoutes),
		canActivate: [authGuard],
	},
	{
		path: 'tax',
		loadChildren: () => import('./features/tax/tax.routes').then((m) => m.taxRoutes),
		canActivate: [authGuard],
	},
	{
		path: 'scenarios',
		loadChildren: () =>
			import('./features/scenarios/scenarios.routes').then((m) => m.scenariosRoutes),
		canActivate: [authGuard],
	},
	{
		path: '**',
		redirectTo: '',
	},
];
