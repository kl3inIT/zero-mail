import createClient from 'openapi-fetch';

import { getAdminApiBase } from './admin-base-url';
import type { paths } from './admin-schema';

export const api = createClient<paths>({
  baseUrl: getAdminApiBase(),
  credentials: 'include',
});
