const DEFAULT_API_BASE = 'http://localhost:8080';

function publicBase(): string {
  const raw = (process.env.NEXT_PUBLIC_API_BASE?.trim() || DEFAULT_API_BASE).replace(/\/+$/, '');
  return /^https?:\/\//i.test(raw) ? raw : `http://${raw}`;
}

// For server-side fetch() calls — uses internal Docker URL when available to bypass NPM
export function getApiBase(): string {
  if (typeof window === 'undefined') {
    const internal = process.env.API_BASE_URL?.trim();
    if (internal) return internal.replace(/\/+$/, '');
  }
  return publicBase();
}

// For browser-facing hrefs and redirects — always uses the public URL
export function getPublicApiBase(): string {
  return publicBase();
}

export function getApiUrl(path: `/${string}`): string {
  return `${getApiBase()}${path}`;
}

export function getPublicApiUrl(path: `/${string}`): string {
  return `${getPublicApiBase()}${path}`;
}
