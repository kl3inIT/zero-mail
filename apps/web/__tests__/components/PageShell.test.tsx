// Wave 0 RED scaffold — references implementation that lands in Plan 04.
// Locks the PageShell primitive contract (UI-SPEC §Spacing — container widths):
//  - renders <main> element
//  - variant=app  -> max-w-3xl (default)
//  - variant=marketing -> max-w-5xl
//  - variant=docs -> max-w-3xl
//  - variant=auth -> max-w-md + centered flex
import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';

// RED-by-design: @/components/ui/PageShell does not exist yet (Plan 04 lands it).
import { PageShell } from '@/components/ui/PageShell';

describe('PageShell', () => {
  it('renders <main> element', () => {
    render(<PageShell>content</PageShell>);
    expect(screen.getByRole('main')).toBeInTheDocument();
  });

  it('applies max-w-3xl for variant=app (default)', () => {
    render(<PageShell>content</PageShell>);
    expect(screen.getByRole('main').className).toContain('max-w-3xl');
  });

  it('applies max-w-5xl for variant=marketing', () => {
    render(<PageShell variant="marketing">content</PageShell>);
    expect(screen.getByRole('main').className).toContain('max-w-5xl');
  });

  it('applies max-w-3xl for variant=docs', () => {
    render(<PageShell variant="docs">content</PageShell>);
    expect(screen.getByRole('main').className).toContain('max-w-3xl');
  });

  it('applies max-w-md and centered flex for variant=auth', () => {
    render(<PageShell variant="auth">content</PageShell>);
    const main = screen.getByRole('main');
    expect(main.className).toMatch(/max-w-md/);
    expect(main.className).toMatch(/items-center/);
    expect(main.className).toMatch(/justify-center/);
  });
});
