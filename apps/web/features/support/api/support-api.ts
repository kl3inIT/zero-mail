'use client';

import { api } from '@/lib/api/client';
import type { components } from '@/lib/api/schema';

export type FeedbackSubmissionRequest = components['schemas']['FeedbackSubmissionRequest'];
export type FeedbackSubmissionResponse = components['schemas']['FeedbackSubmissionResponse'];
export type FeedbackType = FeedbackSubmissionRequest['type'];

function unwrap<T>(
  result: { data?: T; error?: unknown; response: Response },
  fallbackMessage: string,
): T {
  if (result.error || !result.response.ok || result.data === undefined) {
    throw result.error ?? new Error(fallbackMessage);
  }
  return result.data;
}

export async function submitFeedback(
  body: FeedbackSubmissionRequest,
): Promise<FeedbackSubmissionResponse> {
  const result = await api.POST('/api/support/feedback', { body });
  return unwrap(result, `feedback submission failed: ${result.response.status}`);
}
