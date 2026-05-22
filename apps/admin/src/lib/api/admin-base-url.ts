const DEFAULT_ADMIN_API_BASE = 'http://localhost:8080';

function normalizeBaseUrl(rawBaseUrl: string): string {
  const trimmedBaseUrl = rawBaseUrl.trim().replace(/\/+$/, '');
  if (!trimmedBaseUrl) {
    return DEFAULT_ADMIN_API_BASE;
  }
  return /^https?:\/\//i.test(trimmedBaseUrl) ? trimmedBaseUrl : `http://${trimmedBaseUrl}`;
}

export function getAdminApiBase(): string {
  return normalizeBaseUrl(import.meta.env.VITE_ADMIN_API_BASE ?? DEFAULT_ADMIN_API_BASE);
}

export function getAdminApiUrl(path: `/${string}`): string {
  return `${getAdminApiBase()}${path}`;
}
