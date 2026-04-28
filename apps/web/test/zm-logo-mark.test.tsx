// Wave 0 RED scaffold — locks the ZMLogoMark component contract (Phase 1.6 REQ-1.6-5):
//  - ZMLogoMark renders SVG with default size 16
//  - Uses currentColor for stroke and fill
//  - Respects size prop
//
// RED-by-design: ZMLogoMark does not yet exist at features/landing/components/ZMLogoMark.tsx.
// This spec becomes GREEN when Phase 1.6 Wave 2 lands.
import { describe, it, expect } from 'vitest';
import { render } from '@testing-library/react';
import ZMLogoMark from '@/features/landing/components/ZMLogoMark';

describe('ZMLogoMark', () => {
  it('renders an SVG with default size 16', () => {
    const { container } = render(<ZMLogoMark />);
    const svg = container.querySelector('svg');
    expect(svg).not.toBeNull();
    expect(svg!.getAttribute('width')).toBe('16');
    expect(svg!.getAttribute('height')).toBe('16');
  });

  it('uses currentColor for stroke and fill', () => {
    const { container } = render(<ZMLogoMark />);
    const html = container.innerHTML;
    expect(html).toMatch(/stroke="currentColor"/);
    expect(html).toMatch(/fill="currentColor"/);
  });

  it('respects size prop', () => {
    const { container } = render(<ZMLogoMark size={48} />);
    const svg = container.querySelector('svg');
    expect(svg!.getAttribute('width')).toBe('48');
  });
});
