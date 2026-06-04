'use client';

import { useQuery } from '@tanstack/react-query';

import {
  fetchSenderMessageBody,
  fetchSenderMessages,
  fetchSenderTimeline,
  type SenderMessageBody,
  type SenderMessageSummary,
  type SenderTimelineEntry,
} from '@/features/cleanup/unsubscribe-campaign/api/unsubscribe-campaign-api';
import type { DateRangeSpec } from '@/features/cleanup/unsubscribe-campaign/date-range-spec';
import { unsubscribeCampaignKeys } from '@/features/cleanup/unsubscribe-campaign/query-keys';

export function useSenderTimeline(senderEmail: string | null, spec: DateRangeSpec) {
  return useQuery<SenderTimelineEntry[]>({
    queryKey: unsubscribeCampaignKeys.senderTimeline(senderEmail ?? '', spec),
    queryFn: () => fetchSenderTimeline(senderEmail ?? '', spec),
    enabled: !!senderEmail,
    staleTime: 60_000,
    refetchOnWindowFocus: false,
    meta: { silent: true },
  });
}

export function useSenderMessages(
  senderEmail: string | null,
  archivedOnly: boolean,
  spec: DateRangeSpec,
) {
  return useQuery<SenderMessageSummary[]>({
    queryKey: unsubscribeCampaignKeys.senderMessages(senderEmail ?? '', archivedOnly, spec),
    queryFn: () => fetchSenderMessages(senderEmail ?? '', archivedOnly, 50, spec),
    enabled: !!senderEmail,
    staleTime: 30_000,
    refetchOnWindowFocus: false,
    meta: { silent: true },
  });
}

export function useSenderMessageBody(gmailMessageId: string | null) {
  return useQuery<SenderMessageBody | null>({
    queryKey: unsubscribeCampaignKeys.senderMessageBody(gmailMessageId ?? ''),
    queryFn: () => fetchSenderMessageBody(gmailMessageId ?? ''),
    enabled: !!gmailMessageId,
    staleTime: 5 * 60_000,
    refetchOnWindowFocus: false,
    meta: { silent: true },
  });
}
