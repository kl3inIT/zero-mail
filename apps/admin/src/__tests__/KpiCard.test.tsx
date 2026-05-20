import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { KpiCard } from '@/components/KpiCard';

describe('KpiCard', () => {
  it('renders label, value, and tabular-nums classname by default', () => {
    render(<KpiCard label="Dead letters" value={42} hint="Re-queue from below." />);

    expect(screen.getByText('Dead letters')).toBeInTheDocument();
    const valueNode = screen.getByText('42');
    expect(valueNode).toBeInTheDocument();
    expect(valueNode.className).toMatch(/tabular-nums/);
    expect(screen.getByText('Re-queue from below.')).toBeInTheDocument();
  });

  it('renders a delta badge when the delta prop is supplied', () => {
    render(
      <KpiCard
        label="Failure rate"
        value="2.3%"
        delta={{ sign: 'down', value: '0.4pp' }}
      />,
    );
    expect(screen.getByText('0.4pp')).toBeInTheDocument();
  });

  it('omits tabular-nums when tabular={false}', () => {
    render(<KpiCard label="State" value="OK" tabular={false} />);
    const valueNode = screen.getByText('OK');
    expect(valueNode.className).not.toMatch(/tabular-nums/);
  });
});
