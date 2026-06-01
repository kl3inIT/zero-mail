import { render, screen, within } from '@testing-library/react';
import { NextIntlClientProvider } from 'next-intl';
import type { ReactNode } from 'react';
import { describe, expect, it } from 'vitest';

import { TooltipProvider } from '@/components/ui/tooltip';
import { InboxFlowPanel } from '@/features/analytics/components/InboxFlowPanel';
import { MetadataControlPanel } from '@/features/analytics/components/MetadataControlPanel';
import { MetadataLoadPanel } from '@/features/analytics/components/MetadataLoadPanel';
import { RuleHitsPanel } from '@/features/analytics/components/RuleHitsPanel';
import { TimeSavedPanel } from '@/features/analytics/components/TimeSavedPanel';
import { formatTimeSaved } from '@/features/analytics/components/analytics-visualization';
import { TopSendersPanel } from '@/features/analytics/components/TopSendersPanel';
import { VolumePanel } from '@/features/analytics/components/VolumePanel';
import enMessages from '@/i18n/messages/en.json';

describe('analytics panels', () => {
  it('renders explicit zero states without NaN', () => {
    const { container } = renderWithProviders(
      <>
        <VolumePanel observed={0} applied={0} />
        <TimeSavedPanel seconds={0} />
        <InboxFlowPanel observed={0} applied={0} ruleHits={[]} />
        <MetadataLoadPanel dailyLoad={[]} categoryLoad={[]} />
        <MetadataControlPanel
          actionMix={[]}
          replyBuckets={[]}
          automationOpportunities={{ noRuleMatched: 0, failedActions: 0, pendingActions: 0 }}
        />
        <TopSendersPanel senders={[]} />
        <RuleHitsPanel ruleHits={[]} />
      </>,
    );

    expect(screen.getByTestId('analytics-volume-panel')).toHaveTextContent('0');
    expect(screen.getByTestId('analytics-time-saved-panel')).toHaveTextContent('0m');
    expect(screen.getByTestId('analytics-inbox-flow-panel')).toHaveTextContent('0');
    expect(screen.getByTestId('analytics-metadata-load-panel')).toHaveTextContent(
      'No inbox-load data in this window.',
    );
    expect(screen.getByTestId('analytics-metadata-control-panel')).toHaveTextContent('No rule');
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
        <InboxFlowPanel
          observed={1500}
          applied={1247}
          ruleHits={[
            { ruleName: 'Archive receipts', decisions: 30, applied: 28, reverted: 2 },
            { ruleName: 'Draft investor updates', decisions: 9, applied: 9, reverted: 0 },
          ]}
        />
        <MetadataLoadPanel
          dailyLoad={[
            { day: '2026-05-10', observed: 20, applied: 16, reverted: 1 },
            { day: '2026-05-11', observed: 32, applied: 24, reverted: 0 },
          ]}
          categoryLoad={[
            { category: 'updates', count: 18 },
            { category: 'promotions', count: 7 },
          ]}
        />
        <MetadataControlPanel
          actionMix={[
            { actionType: 'archive', applied: 19, reverted: 1, failed: 0 },
            { actionType: 'save_draft', applied: 9, reverted: 0, failed: 0 },
          ]}
          replyBuckets={[
            { bucket: 'TO_REPLY', count: 6, withDraft: 4 },
            { bucket: 'AWAITING_THEIR_REPLY', count: 2, withDraft: 0 },
          ]}
          automationOpportunities={{ noRuleMatched: 5, failedActions: 1, pendingActions: 0 }}
        />
        <TopSendersPanel
          senders={[
            { senderEmail: 'founder@acme.test', count: 44 },
            { senderEmail: 'billing@example.com', count: 21 },
            { senderEmail: 'alerts@example.com', count: 12 },
          ]}
          domainLoad={[
            { domain: 'acme.test', count: 44 },
            { domain: 'example.com', count: 33 },
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
    expect(screen.getByTestId('analytics-metadata-load-panel')).toHaveTextContent('Updates');
    expect(screen.getByTestId('analytics-metadata-control-panel')).toHaveTextContent('Archive');
    expect(screen.getByTestId('analytics-top-senders-panel')).toHaveTextContent('acme.test');

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
