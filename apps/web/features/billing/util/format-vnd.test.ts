import { describe, expect, it } from 'vitest';

import { formatVnd } from './format-vnd';

describe('formatVnd', () => {
  it('formats whole-number VND amounts for the selected locale', () => {
    expect(formatVnd(1250000, 'vi-VN')).toBe('1.250.000\u00a0\u20ab');
  });
});
