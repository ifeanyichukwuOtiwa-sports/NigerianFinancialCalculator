import { HttpInterceptorFn } from '@angular/common/http';

export const brandInterceptor: HttpInterceptorFn = (req, next) => {
	const branded = req.clone({
		setHeaders: { 'X-App-Brand': 'NGN' }
	});
	return next(branded);
};
