import { api } from '@/lib/api/admin-client';
import type { components, paths } from '@/lib/api/admin-schema';

export type WaitlistEntry = components['schemas']['AdminWaitlistEntryResponse'];
export type WaitlistStatus = WaitlistEntry['status'];
export type WaitlistListResponse = components['schemas']['AdminWaitlistListResponse'];

type OpenApiWaitlistListQuery = NonNullable<
  paths['/api/admin/waitlist/']['get']['parameters']['query']
>;

export const WAITLIST_STATUSES = [
  'PENDING',
  'APPROVED',
  'REJECTED',
  'INVITED',
  'INVITE_FAILED',
] as const satisfies ReadonlyArray<WaitlistStatus>;

export type WaitlistListQuery = Omit<OpenApiWaitlistListQuery, 'status'> & {
  status?: WaitlistStatus;
};

function errorFor(operation: string): Error {
  return new Error(`Kh\u00f4ng th\u1ec3 ${operation}.`);
}

export async function fetchWaitlistList(
  query: WaitlistListQuery,
): Promise<WaitlistListResponse> {
  const { data, error } = await api.GET('/api/admin/waitlist/', {
    params: { query },
  });
  if (error || !data) {
    throw errorFor('t\u1ea3i danh s\u00e1ch \u0111\u0103ng k\u00fd ch\u1edd');
  }
  return data;
}

export async function approveWaitlistEntry(waitlistId: string): Promise<WaitlistEntry> {
  const { data, error } = await api.POST('/api/admin/waitlist/{waitlistId}/approve', {
    params: {
      path: { waitlistId },
    },
  });
  if (error || !data) {
    throw errorFor('duy\u1ec7t \u0111\u0103ng k\u00fd');
  }
  return data;
}

export async function rejectWaitlistEntry(waitlistId: string): Promise<WaitlistEntry> {
  const { data, error } = await api.POST('/api/admin/waitlist/{waitlistId}/reject', {
    params: {
      path: { waitlistId },
    },
  });
  if (error || !data) {
    throw errorFor('t\u1eeb ch\u1ed1i \u0111\u0103ng k\u00fd');
  }
  return data;
}
