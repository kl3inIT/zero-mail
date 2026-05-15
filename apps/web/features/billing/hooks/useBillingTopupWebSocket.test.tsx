import { act, renderHook } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

type MessageCallback = (message: { body: string }) => void;

type ClientConfig = {
  brokerURL: string;
  onConnect?: () => void;
};

class MockStompClient {
  static instances: MockStompClient[] = [];

  readonly config: ClientConfig;
  readonly activate = vi.fn(() => {
    this.config.onConnect?.();
  });
  readonly deactivate = vi.fn(() => Promise.resolve());
  readonly unsubscribe = vi.fn();
  readonly subscribe = vi.fn((destination: string, callback: MessageCallback) => {
    this.destination = destination;
    this.callback = callback;
    return { unsubscribe: this.unsubscribe };
  });

  destination?: string;
  callback?: MessageCallback;

  constructor(config: ClientConfig) {
    this.config = config;
    MockStompClient.instances.push(this);
  }
}

vi.mock('@stomp/stompjs', () => ({
  Client: vi.fn(function Client(config: ClientConfig) {
    return new MockStompClient(config);
  }),
}));

vi.mock('@/features/billing/realtime/billing-websocket-url', () => ({
  getBillingWebSocketUrl: () => 'ws://localhost:8080/ws',
}));

import { useBillingTopupWebSocket } from '@/features/billing/hooks/useBillingTopupWebSocket';

describe('useBillingTopupWebSocket', () => {
  beforeEach(() => {
    MockStompClient.instances = [];
  });

  it('subscribes to the tenant billing topic and handles matching top-up events', () => {
    const onCredited = vi.fn();

    renderHook(() =>
      useBillingTopupWebSocket({
        tenantId: 'tenant-123',
        orderCode: 'ZM123456',
        onCredited,
      }),
    );

    const client = MockStompClient.instances[0];

    expect(client.config.brokerURL).toBe('ws://localhost:8080/ws');
    expect(client.activate).toHaveBeenCalledTimes(1);
    expect(client.destination).toBe('/topic/tenants/tenant-123/billing');

    act(() => {
      client.callback?.({
        body: JSON.stringify({
          type: 'TOPUP_CREDITED',
          orderCode: 'ZM123456',
          packageCode: 'starter',
          packageName: 'Starter',
          amountVnd: 100000,
          creditAmount: 100,
          creditedAt: '2026-05-15T00:00:00Z',
        }),
      });
    });

    expect(onCredited).toHaveBeenCalledWith(
      expect.objectContaining({ orderCode: 'ZM123456', type: 'TOPUP_CREDITED' }),
    );
  });

  it('ignores events for another order code', () => {
    const onCredited = vi.fn();

    renderHook(() =>
      useBillingTopupWebSocket({
        tenantId: 'tenant-123',
        orderCode: 'ZM123456',
        onCredited,
      }),
    );

    const client = MockStompClient.instances[0];

    act(() => {
      client.callback?.({
        body: JSON.stringify({
          type: 'TOPUP_CREDITED',
          orderCode: 'OTHER',
          packageCode: null,
          packageName: null,
          amountVnd: 100000,
          creditAmount: 100,
          creditedAt: '2026-05-15T00:00:00Z',
        }),
      });
    });

    expect(onCredited).not.toHaveBeenCalled();
  });

  it('cleans up the subscription and websocket client on unmount', () => {
    const { unmount } = renderHook(() =>
      useBillingTopupWebSocket({
        tenantId: 'tenant-123',
        orderCode: 'ZM123456',
        onCredited: vi.fn(),
      }),
    );

    const client = MockStompClient.instances[0];

    unmount();

    expect(client.unsubscribe).toHaveBeenCalledTimes(1);
    expect(client.deactivate).toHaveBeenCalledTimes(1);
  });
});
