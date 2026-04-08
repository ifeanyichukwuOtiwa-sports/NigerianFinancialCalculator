import { inject } from '@angular/core';
import { HttpInterceptorFn } from '@angular/common/http';
import { BrandService } from '@app/shared/services/brand.service';

export const brandInterceptor: HttpInterceptorFn = (req, next) => {
	const brandService = inject(BrandService);
	const branded = req.clone({
		setHeaders: { 'x-app-brand': brandService.getBrand() },
	});
	return next(branded);
};
