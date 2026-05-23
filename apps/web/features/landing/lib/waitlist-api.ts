import { getPublicApiUrl } from '@/lib/api/base-url';

// TODO: regenerate apps/web/lib/api/schema.d.ts via `pnpm --filter web run generate:api`
// after backend `WaitlistController` is bootable, then replace this raw-fetch wrapper with
// the typed `api.POST('/api/waitlist/subscribe', ...)` from `@/lib/api/client`. See AGENTS.md
// — raw fetch is allowed for "temporarily missing schema with an explicit TODO".

export type WaitlistSubscribeStatus = 'ADDED' | 'ALREADY_REGISTERED' | 'ALREADY_USER';

export type WaitlistSubscribeResponse = {
  status: WaitlistSubscribeStatus;
};

export type WaitlistSubscribeRequest = {
  email: string;
  source?: string;
  website?: string;
};

export class WaitlistSubscribeError extends Error {
  constructor(
    message: string,
    readonly httpStatus: number,
    readonly code: 'INVALID_EMAIL' | 'RATE_LIMITED' | 'SERVER_ERROR' | 'NETWORK',
  ) {
    super(message);
  }
}

export async function submitWaitlist(
  request: WaitlistSubscribeRequest,
): Promise<WaitlistSubscribeResponse> {
  let response: Response;
  try {
    response = await fetch(getPublicApiUrl('/api/waitlist/subscribe'), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request),
      credentials: 'include',
    });
  } catch {
    throw new WaitlistSubscribeError('Không kết nối được máy chủ. Vui lòng thử lại.', 0, 'NETWORK');
  }

  if (response.status === 400) {
    throw new WaitlistSubscribeError('Email không hợp lệ.', 400, 'INVALID_EMAIL');
  }
  if (response.status === 429) {
    throw new WaitlistSubscribeError(
      'Bạn đã gửi quá nhiều lần. Vui lòng thử lại sau vài phút.',
      429,
      'RATE_LIMITED',
    );
  }
  if (!response.ok) {
    throw new WaitlistSubscribeError(
      'Có lỗi xảy ra. Vui lòng thử lại sau.',
      response.status,
      'SERVER_ERROR',
    );
  }

  const payload = (await response.json()) as WaitlistSubscribeResponse;
  return payload;
}
