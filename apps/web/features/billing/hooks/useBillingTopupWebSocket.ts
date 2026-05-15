'use client';

import { useEffect, useRef } from 'react';
import { Client, type IMessage, type StompSubscription } from '@stomp/stompjs';

import {
  type BillingTopupCreditedMessage,
  parseBillingTopupCreditedMessage,
} from '@/features/billing/realtime/billing-topup-events';
import { getBillingWebSocketUrl } from '@/features/billing/realtime/billing-websocket-url';

type UseBillingTopupWebSocketOptions = {
  tenantId?: string | null;
  orderCode?: string | null;
  enabled?: boolean;
  onCredited: (message: BillingTopupCreditedMessage) => void;
};

export function useBillingTopupWebSocket({
  tenantId,
  orderCode,
  enabled = true,
  onCredited,
}: UseBillingTopupWebSocketOptions) {
  const onCreditedRef = useRef(onCredited);

  useEffect(() => {
    onCreditedRef.current = onCredited;
  }, [onCredited]);

  useEffect(() => {
    if (!enabled || !tenantId || !orderCode) {
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
          const message = parseBillingTopupCreditedMessage(frame.body);
          if (message?.orderCode === orderCode) {
            onCreditedRef.current(message);
          }
        });
      },
    });

    client.activate();

    return () => {
      subscription?.unsubscribe();
      void client.deactivate();
    };
  }, [enabled, orderCode, tenantId]);
}
