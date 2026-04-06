import { HttpInterceptorFn } from '@angular/common/http';

export const brandInterceptor: HttpInterceptorFn = (req, next) => {
	const branded = req.clone({
		setHeaders: { 'x-app-brand': 'NGN' },
	});
	return next(branded);
};
