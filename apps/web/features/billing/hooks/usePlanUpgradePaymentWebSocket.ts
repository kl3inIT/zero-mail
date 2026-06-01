'use client';

import { Client, type IMessage, type StompSubscription } from '@stomp/stompjs';
import { useEffect, useRef } from 'react';

import {
  parsePlanUpgradePaymentCompletedMessage,
  type PlanUpgradePaymentCompletedMessage,
} from '@/features/billing/realtime/plan-upgrade-events';
import { getBillingWebSocketUrl } from '@/features/billing/realtime/billing-websocket-url';

type UsePlanUpgradePaymentWebSocketOptions = {
  tenantId?: string | null;
  bankTransferIntentId?: string | null;
  bankTransferCode?: string | null;
  enabled?: boolean;
  onPaymentCompleted: (message: PlanUpgradePaymentCompletedMessage) => void;
};

export function usePlanUpgradePaymentWebSocket({
  tenantId,
  bankTransferIntentId,
  bankTransferCode,
  enabled = true,
  onPaymentCompleted,
}: UsePlanUpgradePaymentWebSocketOptions) {
  const onPaymentCompletedRef = useRef(onPaymentCompleted);

  useEffect(() => {
    onPaymentCompletedRef.current = onPaymentCompleted;
  }, [onPaymentCompleted]);

  useEffect(() => {
    if (!enabled || !tenantId || (!bankTransferIntentId && !bankTransferCode)) {
      return undefined;
    }

    let subscription: StompSubscription | undefined;
    const client = new Client({
      brokerURL: getBillingWebSocketUrl(),
      reconnectDelay: 5_000,
      heartbeatIncoming: 10_000,
      heartbeatOutgoing: 10_000,
      debug: () => undefined,
      onConnect: () => {
        subscription = client.subscribe(`/topic/tenants/${tenantId}/billing`, (frame: IMessage) => {
          const message = parsePlanUpgradePaymentCompletedMessage(frame.body);
          if (
            message &&
            (message.bankTransferIntentId === bankTransferIntentId ||
              message.bankTransferCode === bankTransferCode)
          ) {
            onPaymentCompletedRef.current(message);
          }
        });
      },
    });

    client.activate();

    return () => {
      subscription?.unsubscribe();
      void client.deactivate();
    };
  }, [bankTransferCode, bankTransferIntentId, enabled, tenantId]);
}
