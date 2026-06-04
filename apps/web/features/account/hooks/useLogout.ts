'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';

import { getApiUrl } from '@/lib/api/base-url';
import { xsrfHeader } from '@/lib/api/client';
import { PERSIST_STORAGE_KEY } from '@/lib/query-client';

async function logoutUser(): Promise<void> {
  const response = await fetch(getApiUrl('/api/logout'), {
    method: 'POST',
    credentials: 'include',
    headers: xsrfHeader(),
  });
  if (!response.ok) {
    throw new Error(`Đăng xuất thất bại: ${response.status}`);
  }
}

export function useLogout() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: logoutUser,
    onSuccess: () => {
      queryClient.clear();
      // queryClient.clear() drops the in-memory cache; the persister's
      // throttled writer would normally fire on the next tick to mirror it
      // into localStorage, but window.location.assign below kills the JS
      // process before that happens. Wipe the key explicitly so the next
      // user on this browser does not rehydrate the prior session's
      // unsubscribe candidate list.
      if (typeof window !== 'undefined') {
        window.localStorage.removeItem(PERSIST_STORAGE_KEY);
      }
      window.location.assign('/login');
    },
    meta: {
      errorMessage: 'Không thể đăng xuất. Vui lòng thử lại.',
    },
  });
}
