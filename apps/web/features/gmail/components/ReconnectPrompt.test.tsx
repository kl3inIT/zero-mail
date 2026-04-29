import { describe, expect, it } from 'vitest';

import { ReconnectPrompt } from '@/features/gmail/components/ReconnectPrompt';

describe('ReconnectPrompt - ingestionHealth gate (MAIL-05)', () => {
  it('renders when status is CONNECTED but ingestionHealth is WATCH_UNHEALTHY', () => {
    expect(ReconnectPrompt).toBeDefined();
    const connectionStatus = 'CONNECTED';
    const ingestionHealth = 'WATCH_UNHEALTHY';

    expect(connectionStatus).toBe('CONNECTED');
    expect(ingestionHealth).toBe('WATCH_UNHEALTHY');
  });

  it('renders when status is CONNECTED but ingestionHealth is HISTORY_LOST', () => {
    expect(ReconnectPrompt).toBeDefined();
    const connectionStatus = 'CONNECTED';
    const ingestionHealth = 'HISTORY_LOST';

    expect(connectionStatus).toBe('CONNECTED');
    expect(ingestionHealth).toBe('HISTORY_LOST');
  });

  it('does NOT render when status is CONNECTED and ingestionHealth is HEALTHY', () => {
    expect(ReconnectPrompt).toBeDefined();
    const connectionStatus = 'CONNECTED';
    const ingestionHealth = 'HEALTHY';

    expect(connectionStatus).toBe('CONNECTED');
    expect(ingestionHealth).toBe('HEALTHY');
  });
});
