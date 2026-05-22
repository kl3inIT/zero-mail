import createClient, { type Middleware } from 'openapi-fetch';

import { getAdminApiBase } from './admin-base-url';
import type { paths } from './admin-schema';

export const api = createClient<paths>({
  baseUrl: getAdminApiBase(),
  credentials: 'include',
});

const PUBLIC_AUTH_PATHS = ['/login', '/enroll'];
const MUTATING_METHODS = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);

function readXsrfCookie(): string | undefined {
  if (typeof document === 'undefined') return undefined;
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
  return match ? decodeURIComponent(match[1]) : undefined;
}

// Spring Security `csrf().spa()` issues XSRF-TOKEN cookie (HttpOnly=false).
// Mutating requests must echo it back as X-XSRF-TOKEN header.
const xsrfMiddleware: Middleware = {
  async onRequest({ request }) {
    if (!MUTATING_METHODS.has(request.method.toUpperCase())) return undefined;
    const token = readXsrfCookie();
    if (!token) return undefined;
    request.headers.set('X-XSRF-TOKEN', token);
    return request;
  },
};

const unauthorizedRedirectMiddleware: Middleware = {
  async onResponse({ response }) {
    if (response.status !== 401 || typeof window === 'undefined') return undefined;
    const path = window.location.pathname;
    if (PUBLIC_AUTH_PATHS.some((publicPath) => path.startsWith(publicPath))) return undefined;
    window.location.assign('/login');
    return undefined;
  },
};

api.use(xsrfMiddleware);
api.use(unauthorizedRedirectMiddleware);
