import { api } from '@/lib/api/admin-client';
import type { components } from '@/lib/api/admin-schema';

export type Scheduler = components['schemas']['SchedulerResponse'];

export async function fetchSchedulers(): Promise<Scheduler[]> {
  const { data, error } = await api.GET('/api/admin/schedulers');
  if (error || !data) {
    throw new Error('Không thể tải danh sách bộ định thời.');
  }
  return data;
}
