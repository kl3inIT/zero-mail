'use client';

import { useMutation } from '@tanstack/react-query';

import {
  submitFeedback,
  type FeedbackSubmissionRequest,
  type FeedbackSubmissionResponse,
} from '@/features/support/api/support-api';

export function useSubmitFeedback() {
  return useMutation<FeedbackSubmissionResponse, Error, FeedbackSubmissionRequest>({
    mutationFn: submitFeedback,
    meta: {
      successMessage: 'Feedback submitted. We will get back to you soon.',
      errorMessage: 'Failed to submit feedback. Please try again.',
    },
  });
}
