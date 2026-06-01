import { render, screen } from '@testing-library/react';
import { NextIntlClientProvider } from 'next-intl';
import type { ReactNode } from 'react';
import { describe, expect, it } from 'vitest';

import { AuditSafetyNetBadge } from '@/features/triage/components/AuditSafetyNetBadge';
import enMessages from '@/i18n/messages/en.json';
import viMessages from '@/i18n/messages/vi.json';

describe('AuditSafetyNetBadge', () => {
  it('renders nothing when the pattern is absent', () => {
    const { container: nullContainer } = renderWithMessages(<AuditSafetyNetBadge pattern={null} />);
    const { container: emptyContainer } = renderWithMessages(<AuditSafetyNetBadge pattern="" />);

    expect(nullContainer.firstChild).toBeNull();
    expect(emptyContainer.firstChild).toBeNull();
  });

  it('renders the English badge with the pattern', () => {
    renderWithMessages(<AuditSafetyNetBadge pattern="@evilcorp.com" />);

    expect(screen.getByText('Blocked by safety net: @evilcorp.com')).toBeInTheDocument();
  });

  it('renders the Vietnamese badge with the pattern', () => {
    renderWithMessages(<AuditSafetyNetBadge pattern="@evilcorp.com" />, 'vi');

    expect(screen.getByText('Chặn bởi lưới an toàn: @evilcorp.com')).toBeInTheDocument();
  });
});

function renderWithMessages(children: ReactNode, locale: 'en' | 'vi' = 'en') {
  return render(
    <NextIntlClientProvider locale={locale} messages={locale === 'vi' ? viMessages : enMessages}>
      {children}
    </NextIntlClientProvider>,
  );
}
