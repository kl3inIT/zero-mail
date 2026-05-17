// Vitest global setup. Adds @testing-library/jest-dom matchers (toBeInTheDocument,
// toHaveTextContent, etc.) so Plan 06's error-render tests can assert on rendered DOM.
import '@testing-library/jest-dom';
import { vi } from 'vitest';

vi.mock('next/font/google', () => ({
  Roboto: vi.fn(() => ({ variable: 'font-roboto' })),
}));
