'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';

import { getApiUrl } from '@/lib/api/base-url';
import { xsrfHeader } from '@/lib/api/client';

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
      window.location.assign('/login');
    },
    meta: {
      errorMessage: 'Không thể đăng xuất. Vui lòng thử lại.',
    },
  });
}
