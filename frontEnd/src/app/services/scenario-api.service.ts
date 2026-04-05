import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CompoundingFrequency, TaxStrategy } from '../app.types';

export interface ScenarioRequest {
	name: string;
	principal: number;
	annualRate: number;
	years: number;
	monthlyContribution: number;
	compoundingFrequency: CompoundingFrequency;
	taxStrategy: TaxStrategy;
	annualIncome: number;
}

export interface ScenarioResponse {
	id: number;
	brand: string;
	name: string;
	principal: number;
	annualRate: number;
	years: number;
	monthlyContribution: number;
	compoundingFrequency: CompoundingFrequency;
	taxStrategy: TaxStrategy;
	annualIncome: number;
	totalBalance: number;
	totalInterest: number;
	estimatedTax: number;
}

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
}
