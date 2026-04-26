// Wave 0 RED scaffold — references implementation that lands in Plan 04.
// Locks the SectionCard primitive contract (UI-SPEC §Component Inventory):
//  - resolved heading + description strings (RSC-friendly)
//  - omits header region when neither heading nor description provided
//  - children inside body slot
//  - optional footer slot
import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';

// RED-by-design: @/components/ui/SectionCard does not exist yet (Plan 04 lands it).
import { SectionCard } from '@/components/ui/SectionCard';

describe('SectionCard', () => {
  it('renders heading text when heading prop provided', () => {
    render(
      <SectionCard heading="Account">
        <p>body</p>
      </SectionCard>,
    );
    expect(screen.getByText('Account')).toBeInTheDocument();
  });

  it('renders description text when description prop provided', () => {
    render(
      <SectionCard heading="Account" description="Manage your profile">
        <p>body</p>
      </SectionCard>,
    );
    expect(screen.getByText('Manage your profile')).toBeInTheDocument();
  });

  it('omits header region when no heading and no description', () => {
    render(
      <SectionCard>
        <p data-testid="body-only">body content</p>
      </SectionCard>,
    );
    expect(screen.getByTestId('body-only')).toBeInTheDocument();
    // Heading / description text not present.
    expect(screen.queryByText(/Account/)).not.toBeInTheDocument();
    expect(screen.queryByText(/Manage your profile/)).not.toBeInTheDocument();
  });

  it('renders children inside body slot', () => {
    render(
      <SectionCard heading="Gmail connection">
        <p data-testid="card-body">connection status</p>
      </SectionCard>,
    );
    expect(screen.getByTestId('card-body')).toBeInTheDocument();
  });

  it('renders footer region when footer prop provided', () => {
    render(
      <SectionCard
        heading="Gmail connection"
        footer={<p data-testid="card-footer">Single account note</p>}
      >
        <p>body</p>
      </SectionCard>,
    );
    expect(screen.getByTestId('card-footer')).toBeInTheDocument();
  });
});
