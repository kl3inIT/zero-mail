import { describe, it, expect } from 'vitest';
import { render } from '@testing-library/react';
import ZMLogoMark from '@/features/landing/components/ZMLogoMark';

describe('ZMLogoMark', () => {
  it('renders the shared logo image with default size 16', () => {
    const { container } = render(<ZMLogoMark />);
    const image = container.querySelector('img');
    expect(image).not.toBeNull();
    expect(decodeURIComponent(image!.getAttribute('src') ?? '')).toContain('/images/logo.png');
    expect(image!.getAttribute('width')).toBe('16');
    expect(image!.getAttribute('height')).toBe('16');
  });

  it('is decorative because nearby wordmarks provide the readable brand name', () => {
    const { container } = render(<ZMLogoMark />);
    const wrapper = container.querySelector('[aria-hidden="true"]');
    const image = container.querySelector('img');
    expect(wrapper).not.toBeNull();
    expect(image!.getAttribute('alt')).toBe('');
  });

  it('respects size prop', () => {
    const { container } = render(<ZMLogoMark size={48} />);
    const image = container.querySelector('img');
    expect(image!.getAttribute('width')).toBe('48');
    expect(image!.getAttribute('height')).toBe('48');
  });
});
