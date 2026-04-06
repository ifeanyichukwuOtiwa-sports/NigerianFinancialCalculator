export interface AuthUser {
	id: number;
	email: string;
	fullName: string;
}

export interface LoginRequest {
	email: string;
	password: string;
}

export interface RegisterRequest {
	email: string;
	password: string;
	fullName: string;
}
