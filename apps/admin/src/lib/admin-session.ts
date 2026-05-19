import { getAdminApiUrl } from './api/admin-base-url';
import type { components } from './api/admin-schema';

export type AdminMe = components['schemas']['AdminMeResponse'];

export async function getCurrentAdmin(): Promise<AdminMe> {
  const response = await fetch(getAdminApiUrl('/api/admin/me'), {
    credentials: 'include',
  });
  if (response.status === 401) {
    throw new Error('admin session expired');
  }
  if (!response.ok) {
    throw new Error(`admin session lookup failed: ${response.status}`);
  }
  return (await response.json()) as AdminMe;
}
