import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class TaxApiService {
	private readonly http = inject(HttpClient);

	public onDownloadReport(annualIncome: number): Observable<Blob> {
		return this.http.post('/api/tax/export/pdf', { annualIncome }, { responseType: 'blob' });
	}
}
