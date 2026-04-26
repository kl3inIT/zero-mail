const DEFAULT_API_BASE = 'http://localhost:8080';

export function getApiBase(): string {
  const raw = (process.env.NEXT_PUBLIC_API_BASE?.trim() || DEFAULT_API_BASE).replace(/\/+$/, '');
  return /^https?:\/\//i.test(raw) ? raw : `http://${raw}`;
}

export function getApiUrl(path: `/${string}`): string {
  return `${getApiBase()}${path}`;
}
