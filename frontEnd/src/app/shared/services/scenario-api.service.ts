import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ScenarioRequest, ScenarioResponse } from '@app/shared/types/scenario.types';

@Injectable({ providedIn: 'root' })
export class ScenarioApiService {
	private readonly http = inject(HttpClient);
	private readonly baseUrl = '/api/scenarios';

	save(request: ScenarioRequest): Observable<ScenarioResponse> {
		return this.http.post<ScenarioResponse>(this.baseUrl, request, { withCredentials: true });
	}

	list(): Observable<ScenarioResponse[]> {
		return this.http.get<ScenarioResponse[]>(this.baseUrl, { withCredentials: true });
	}

	delete(id: number): Observable<void> {
		return this.http.delete<void>(`${this.baseUrl}/${id}`, { withCredentials: true });
	}

	exportPdf(id: number): Observable<Blob> {
		return this.http.get(`${this.baseUrl}/${id}/export/pdf`, {
			responseType: 'blob',
			withCredentials: true,
		});
	}

	exportCsv(id: number): Observable<Blob> {
		return this.http.get(`${this.baseUrl}/${id}/export/csv`, {
			responseType: 'blob',
			withCredentials: true,
		});
	}
}
