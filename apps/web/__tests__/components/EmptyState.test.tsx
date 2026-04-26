// Wave 0 RED scaffold — references implementation that lands in Plan 04.
// Locks the EmptyState primitive contract (UI-SPEC §Component Inventory):
//  - resolved string title + body (RSC-friendly — caller already translated)
//  - optional icon prop (lucide component) OR children fallback for custom art
//  - optional cta as <Link className={buttonVariants()}> (NOT <Button asChild>)
//  - omits cta region when cta prop missing
import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';

// RED-by-design: @/components/ui/EmptyState does not exist yet (Plan 04 lands it).
import { EmptyState } from '@/components/ui/EmptyState';

describe('EmptyState', () => {
  it('renders title and body strings', () => {
    render(<EmptyState title="No items yet" body="Add one to get started." />);
    expect(screen.getByText('No items yet')).toBeInTheDocument();
    expect(screen.getByText('Add one to get started.')).toBeInTheDocument();
  });

  it('renders custom icon node when icon prop is a JSX element', () => {
    render(
      <EmptyState
        icon={<svg data-testid="custom-icon" aria-hidden="true" />}
        title="Page not found"
        body="The page you are looking for does not exist."
      />,
    );
    expect(screen.getByTestId('custom-icon')).toBeInTheDocument();
  });

  it('renders cta as a Link with buttonVariants() classes when cta provided', () => {
    render(
      <EmptyState
        title="Page not found"
        body="The page you are looking for does not exist."
        cta={{ label: 'Back home', href: '/' }}
      />,
    );
    // UI-SPEC §Interaction Contracts: navigation CTAs use <Link className={buttonVariants()}>
    // — assert there is an anchor with the recognizable button-shaped classes (rounded-md
    // is part of buttonVariants base — present in the existing button.tsx primitive).
    const anchor = screen.getByRole('link', { name: 'Back home' });
    expect(anchor).toHaveAttribute('href', '/');
    expect(anchor.className).toMatch(/rounded-md|inline-flex/);
    // Token-aware: never raw color names.
    expect(anchor.className).not.toMatch(/text-stone-|bg-white|text-gray-/);
  });

  it('omits cta region when cta prop missing', () => {
    render(<EmptyState title="Empty" body="Nothing here yet." />);
    expect(screen.queryByRole('link')).not.toBeInTheDocument();
  });
});
