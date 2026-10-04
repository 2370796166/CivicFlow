export interface ApiResponse<T> { code: string; message: string; data: T; requestId: string }
export interface Page<T> { items: T[]; page: number; size: number; total: number }
export interface PageQuery { page?: number; size?: number }
export type Role = 'USER' | 'STAFF' | 'ADMIN'
export interface CurrentUser { id: string; displayName: string; roles: Role[] }
export interface TokenPair { accessToken: string; expiresIn: number; refreshToken: string; user: CurrentUser }
export interface LoginRequest { loginName: string; password: string }
export interface ApiError extends Error { status?: number; code?: string; requestId?: string; data?: unknown }
