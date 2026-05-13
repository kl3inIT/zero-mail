import { render, screen, within } from '@testing-library/react';
import { NextIntlClientProvider } from 'next-intl';
import type { ReactNode } from 'react';
import { describe, expect, it } from 'vitest';

import { TooltipProvider } from '@/components/ui/tooltip';
import { RuleHitsPanel } from '@/features/analytics/components/RuleHitsPanel';
import { TimeSavedPanel, formatTimeSaved } from '@/features/analytics/components/TimeSavedPanel';
import { TopSendersPanel } from '@/features/analytics/components/TopSendersPanel';
import { VolumePanel } from '@/features/analytics/components/VolumePanel';
import enMessages from '@/i18n/messages/en.json';

describe('analytics panels', () => {
  it('renders explicit zero states without NaN', () => {
    const { container } = renderWithProviders(
      <>
        <VolumePanel observed={0} applied={0} />
        <TimeSavedPanel seconds={0} />
        <TopSendersPanel senders={[]} />
        <RuleHitsPanel ruleHits={[]} />
      </>,
    );

    expect(screen.getByTestId('analytics-volume-panel')).toHaveTextContent('0');
    expect(screen.getByTestId('analytics-time-saved-panel')).toHaveTextContent('0m');
    expect(screen.getByText('No activity in this window.')).toBeInTheDocument();
    expect(screen.getByText('No senders yet in this window.')).toBeInTheDocument();
    expect(screen.getByText('No rules triggered in this window.')).toBeInTheDocument();
    expect(container).not.toHaveTextContent('NaN');
  });

  it('renders seeded panel data', () => {
    renderWithProviders(
      <>
        <VolumePanel observed={1500} applied={1247} />
        <TimeSavedPanel seconds={15120} />
        <TopSendersPanel
          senders={[
            { senderEmail: 'founder@acme.test', count: 44 },
            { senderEmail: 'billing@example.com', count: 21 },
            { senderEmail: 'alerts@example.com', count: 12 },
          ]}
        />
        <RuleHitsPanel
          ruleHits={[
            { ruleName: 'Archive receipts', decisions: 30, applied: 28, reverted: 2 },
            { ruleName: 'Draft investor updates', decisions: 9, applied: 9, reverted: 0 },
          ]}
        />
      </>,
    );

    expect(screen.getByTestId('analytics-volume-panel')).toHaveTextContent('1247');
    expect(screen.getByTestId('analytics-time-saved-panel')).toHaveTextContent('4h 12m');
    expect(formatTimeSaved(15120).label).toBe('4h 12m');

    const topSendersPanel = screen.getByTestId('analytics-top-senders-panel');
    expect(within(topSendersPanel).getByText('1')).toBeInTheDocument();
    expect(within(topSendersPanel).getByText('2')).toBeInTheDocument();
    expect(within(topSendersPanel).getByText('3')).toBeInTheDocument();
    expect(within(topSendersPanel).getByText('founder@acme.test')).toBeInTheDocument();

    const tableRows = screen.getAllByTestId('rule-hit-table-row');
    expect(tableRows).toHaveLength(2);
    expect(screen.getAllByText('Archive receipts')).not.toHaveLength(0);
    expect(screen.getAllByText('28')).not.toHaveLength(0);
    expect(screen.getAllByText('2')).not.toHaveLength(0);
  });

  it('re-renders panel values when the selected window data changes', () => {
    const { rerender } = renderWithProviders(<VolumePanel observed={10} applied={7} />);
    expect(screen.getByTestId('analytics-volume-panel')).toHaveTextContent('7');

    rerender(
      <Providers>
        <VolumePanel observed={60} applied={42} />
      </Providers>,
    );

    expect(screen.getByTestId('analytics-volume-panel')).toHaveTextContent('42');
  });
});

function renderWithProviders(children: ReactNode) {
  return render(<Providers>{children}</Providers>);
}

function Providers({ children }: { children: ReactNode }) {
  return (
    <NextIntlClientProvider locale="en" messages={enMessages}>
      <TooltipProvider>{children}</TooltipProvider>
    </NextIntlClientProvider>
  );
}
