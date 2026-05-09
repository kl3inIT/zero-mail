// Locks the StepIndicator 3-state contract (Phase 1.6 REQ-1.6-6):
//  - Renders active pill with bg-accent for each currentStep value
//  - Renders completed pills with bg-accent-soft when currentStep=COMPLETE
//  - Renders future pills with border border-border when currentStep=GMAIL_CONNECTED
import { describe, it, expect, vi } from 'vitest';
import { render } from '@testing-library/react';

vi.mock('next-intl/server', () => ({
  getTranslations: vi.fn(async () => (k: string, vals?: Record<string, unknown>) => {
    if (vals) return `${k}:${JSON.stringify(vals)}`;
    return k;
  }),
}));

import { StepIndicator } from '@/features/auth/components/StepIndicator';

describe('StepIndicator', () => {
  it.each([
    ['GMAIL_CONNECTED', 'bg-accent'],
    ['TEMPLATE_SELECTED', 'bg-accent'],
    ['COMPLETE', 'bg-accent'],
  ] as const)('renders active pill with bg-accent for currentStep=%s', async (step, marker) => {
    const Node = await StepIndicator({ currentStep: step });
    const { container } = render(Node as React.ReactElement);
    expect(container.innerHTML).toContain(marker);
  });

  it('renders completed pills with bg-accent-soft when currentStep=COMPLETE', async () => {
    const Node = await StepIndicator({ currentStep: 'COMPLETE' });
    const { container } = render(Node as React.ReactElement);
    expect(container.innerHTML).toContain('bg-accent-soft');
  });

  it('renders future pills with border border-border when currentStep=GMAIL_CONNECTED', async () => {
    const Node = await StepIndicator({ currentStep: 'GMAIL_CONNECTED' });
    const { container } = render(Node as React.ReactElement);
    expect(container.innerHTML).toMatch(/border\s+border-border/);
  });
});
