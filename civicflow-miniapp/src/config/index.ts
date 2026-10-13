export const API_BASE = (
  import.meta.env.VITE_API_BASE_URL || 'http://127.0.0.1:8080/api/v1'
).replace(/\/$/, '')
const storageDays = Number(import.meta.env.VITE_REFRESH_STORAGE_DAYS ?? 1)
export const REFRESH_MAX_AGE =
  Math.max(0, Math.min(7, Number.isFinite(storageDays) ? storageDays : 1)) *
  86400000
