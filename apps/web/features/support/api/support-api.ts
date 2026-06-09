'use client';

// TODO: regenerate schema after backend deployment and replace with typed api client
// (POST /api/support/feedback will appear in schema.d.ts after regen)

export type FeedbackType = 'BUG_REPORT' | 'FEATURE_REQUEST' | 'GENERAL';

export type FeedbackSubmissionRequest = {
  type: FeedbackType;
  subject: string;
  message: string;
  contactEmail: string;
};

export type FeedbackSubmissionResponse = {
  id: string;
};

export async function submitFeedback(
  body: FeedbackSubmissionRequest,
): Promise<FeedbackSubmissionResponse> {
  const response = await fetch('/api/support/feedback', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
    credentials: 'include',
  });
  if (!response.ok) {
    throw new Error(`Feedback submission failed: ${response.status}`);
  }
  return response.json() as Promise<FeedbackSubmissionResponse>;
}
