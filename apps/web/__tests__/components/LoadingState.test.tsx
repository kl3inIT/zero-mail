// Wave 0 RED scaffold — references implementation that lands in Plan 04.
// Locks the LoadingState primitive contract (UI-SPEC §Component Inventory):
//  - composes shadcn <Skeleton> (data-slot="skeleton")
//  - variant=card (default) -> at least 3 skeleton placeholders
//  - variant=list -> longer placeholder list (>=5)
//  - variant=page -> page-shaped header + body skeletons
import { describe, it, expect } from 'vitest';
import { render } from '@testing-library/react';

// RED-by-design: @/components/ui/LoadingState does not exist yet (Plan 04 lands it).
import { LoadingState } from '@/components/ui/LoadingState';

describe('LoadingState', () => {
  it('renders multiple skeleton placeholders for variant=card (default)', () => {
    const { container } = render(<LoadingState />);
    const skeletons = container.querySelectorAll('[data-slot="skeleton"]');
    expect(skeletons.length).toBeGreaterThanOrEqual(3);
  });

  it('renders longer placeholder list for variant=list', () => {
    const { container } = render(<LoadingState variant="list" />);
    const skeletons = container.querySelectorAll('[data-slot="skeleton"]');
    expect(skeletons.length).toBeGreaterThanOrEqual(5);
  });

  it('renders page-shaped header+body skeletons for variant=page', () => {
    const { container } = render(<LoadingState variant="page" />);
    const skeletons = container.querySelectorAll('[data-slot="skeleton"]');
    expect(skeletons.length).toBeGreaterThanOrEqual(2);
    // Page variant should have at least one skeleton with a "header" shape — assert
    // some skeleton has a width class or distinct height bigger than the others.
    // We assert the structural minimum here; visual fidelity is the implementation's
    // problem (Plan 04).
  });
});
