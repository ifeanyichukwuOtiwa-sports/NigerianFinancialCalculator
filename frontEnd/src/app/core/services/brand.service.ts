import { inject, Injectable, PLATFORM_ID, signal } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';

@Injectable({
	providedIn: 'root',
})
export class BrandService {
	private readonly brand = signal<string>('NGN');
	private readonly platformId = inject(PLATFORM_ID);

	constructor() {
		if (isPlatformBrowser(this.platformId)) {
			this.resolveBrand();
		}
	}

	private resolveBrand(): void {
		const hostname = window.location.hostname;
		const parts = hostname.split('.');

		// Example: gh.mycalculator.com -> GHS, ke.mycalculator.com -> KES
		if (parts.length > 2) {
			const subdomain = parts[0].toUpperCase();
			if (['NGN', 'GHS', 'KES'].includes(subdomain)) {
				this.brand.set(subdomain);
				return;
			}
		}

		// Fallback or default
		this.brand.set('NGN');
	}

	getBrand(): string {
		return this.brand();
	}

	setBrand(brand: string): void {
		this.brand.set(brand);
	}
}
