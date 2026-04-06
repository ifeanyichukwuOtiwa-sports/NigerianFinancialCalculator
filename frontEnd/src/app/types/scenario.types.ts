import { CompoundingFrequency, TaxStrategy } from '@app/app.types';

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
