'use client';

import {
  useInfiniteQuery,
  useMutation,
  useQuery,
  useQueryClient,
  type InfiniteData,
  type QueryClient,
} from '@tanstack/react-query';

import {
  getInboxMessageDetail,
  getInboxPage,
  markInboxMessageRead,
  type InboxLabel,
  type InboxMessage,
  type InboxMessageDetail,
  type InboxPage,
} from '@/features/inbox/api/inbox-api';
import { inboxKeys } from '@/features/inbox/query-keys';

export function useInboxMessages() {
  return useInfiniteQuery({
    queryKey: inboxKeys.pages(),
    queryFn: ({ pageParam }) => getInboxPage({ cursor: pageParam, limit: 20 }),
    initialPageParam: null as string | null,
    getNextPageParam: (lastPage) => lastPage.nextCursor ?? undefined,
    staleTime: 15_000,
    gcTime: 30 * 60_000,
    refetchOnMount: 'always',
    refetchOnWindowFocus: true,
  });
}

export function flattenInboxMessages(data: InfiniteData<InboxPage> | undefined): InboxMessage[] {
  return data?.pages.flatMap((page) => page.items) ?? [];
}

export function latestInboxMaxMessages(data: InfiniteData<InboxPage> | undefined): number {
  const lastPage = data?.pages.at(-1);
  return lastPage?.maxMessages ?? 100;
}

export function latestInboxDataSource(
  data: InfiniteData<InboxPage> | undefined,
): InboxPage['dataSource'] | null {
  const lastPage = data?.pages.at(-1);
  return lastPage?.dataSource ?? null;
}

export function useInboxMessageDetail(gmailMessageId: string | null) {
  return useQuery<InboxMessageDetail>({
    queryKey: inboxKeys.detail(gmailMessageId),
    queryFn: () => getInboxMessageDetail(gmailMessageId!),
    enabled: Boolean(gmailMessageId),
    staleTime: 30_000,
    gcTime: 30 * 60_000,
    refetchOnMount: 'always',
    refetchOnWindowFocus: true,
  });
}

export function useMarkInboxMessageRead() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (gmailMessageId: string) => markInboxMessageRead(gmailMessageId),
    onMutate: async (gmailMessageId) => {
      await queryClient.cancelQueries({ queryKey: inboxKeys.all });
      const previousPages = queryClient.getQueryData<InfiniteData<InboxPage>>(inboxKeys.pages());
      queryClient.setQueryData<InfiniteData<InboxPage>>(inboxKeys.pages(), (current) =>
        current ? updatePagesUnread(current, gmailMessageId, false) : current,
      );
      queryClient.setQueriesData<InboxMessageDetail>(
        { queryKey: [...inboxKeys.all, 'detail'] },
        (current) =>
          current && current.message.gmailMessageId === gmailMessageId
            ? { ...current, message: markMessageUnread(current.message, false) }
            : current,
      );
      return { previousPages };
    },
    onError: (_error, _gmailMessageId, context) => {
      if (context?.previousPages) {
        queryClient.setQueryData(inboxKeys.pages(), context.previousPages);
      }
    },
    onSuccess: async (_data, gmailMessageId) => {
      // Phase B Wave 2: the backend mark-read now writes the projection row in the same flow as
      // the Gmail label modify, so the optimistic cache already matches the next DB read. Skip
      // the inbox pages refetch (would flash the read state away then back) and only invalidate
      // the open message detail so the labels chip there reflects the UNREAD removal.
      await queryClient.invalidateQueries({ queryKey: inboxKeys.detail(gmailMessageId) });
    },
  });
}

export type AppliedInboxLabel = {
  gmailMessageId: string;
  labelName: string;
  gmailLabelId: string;
};

export function addAppliedLabelsToInboxCache(
  queryClient: QueryClient,
  appliedLabels: AppliedInboxLabel[],
) {
  queryClient.setQueryData<InfiniteData<InboxPage>>(inboxKeys.pages(), (current) =>
    current ? addLabelsToPages(current, appliedLabels) : current,
  );
  queryClient.setQueriesData<InboxMessageDetail>(
    { queryKey: [...inboxKeys.all, 'detail'] },
    (current) =>
      current
        ? {
            ...current,
            message: addLabelsToMessage(current.message, appliedLabels),
          }
        : current,
  );
}

function updatePagesUnread(
  data: InfiniteData<InboxPage>,
  gmailMessageId: string,
  unread: boolean,
): InfiniteData<InboxPage> {
  return {
    ...data,
    pages: data.pages.map((page) => ({
      ...page,
      items: page.items.map((message) =>
        message.gmailMessageId === gmailMessageId ? markMessageUnread(message, unread) : message,
      ),
    })),
  };
}

function markMessageUnread(message: InboxMessage, unread: boolean): InboxMessage {
  if (unread) {
    return {
      ...message,
      unread: true,
      labelIds: addUnique(message.labelIds, 'UNREAD'),
      labels: addUniqueLabel(message.labels, { id: 'UNREAD', name: 'UNREAD' }),
    };
  }
  return {
    ...message,
    unread: false,
    labelIds: message.labelIds.filter((labelId) => labelId !== 'UNREAD'),
    labels: message.labels.filter((label) => label.id !== 'UNREAD'),
  };
}

function addLabelsToPages(
  data: InfiniteData<InboxPage>,
  appliedLabels: AppliedInboxLabel[],
): InfiniteData<InboxPage> {
  return {
    ...data,
    pages: data.pages.map((page) => ({
      ...page,
      items: page.items.map((message) => addLabelsToMessage(message, appliedLabels)),
    })),
  };
}

function addLabelsToMessage(
  message: InboxMessage,
  appliedLabels: AppliedInboxLabel[],
): InboxMessage {
  const labelsForMessage = appliedLabels.filter(
    (appliedLabel) => appliedLabel.gmailMessageId === message.gmailMessageId,
  );
  if (labelsForMessage.length === 0) {
    return message;
  }

  let nextLabelIds = message.labelIds;
  let nextLabels = message.labels;
  for (const appliedLabel of labelsForMessage) {
    nextLabelIds = addUnique(nextLabelIds, appliedLabel.gmailLabelId);
    nextLabels = addUniqueLabel(nextLabels, {
      id: appliedLabel.gmailLabelId,
      name: appliedLabel.labelName,
    });
  }
  return { ...message, labelIds: nextLabelIds, labels: nextLabels };
}

function addUnique(values: string[], value: string): string[] {
  return values.includes(value) ? values : [...values, value];
}

function addUniqueLabel(labels: InboxLabel[], label: InboxLabel): InboxLabel[] {
  const existingLabelIndex = labels.findIndex((existingLabel) => existingLabel.id === label.id);
  if (existingLabelIndex === -1) {
    return [...labels, label];
  }
  return labels.map((existingLabel, index) =>
    index === existingLabelIndex ? { ...existingLabel, name: label.name } : existingLabel,
  );
}
