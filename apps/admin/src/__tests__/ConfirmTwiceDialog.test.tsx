import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import { ConfirmTwiceDialog } from '@/components/ConfirmTwiceDialog';

describe('ConfirmTwiceDialog', () => {
  it('rejects sentinel prefixes and requires the confirmation token', async () => {
    const user = userEvent.setup();
    const onConfirm = vi.fn().mockResolvedValue({ auditId: 'audit-1' });

    render(
      <ConfirmTwiceDialog
        open
        onOpenChange={() => undefined}
        actionLabel="Revoke admin grant"
        targetLabel="admin@example.com"
        consequences={['The admin cannot sign in.']}
        confirmationToken="admin@example.com"
        finalButtonLabel="Revoke admin"
        onConfirm={onConfirm}
      />,
    );

    const reasonField = screen.getByLabelText('Reason (recorded in audit log)');
    await user.type(reasonField, 'key:sk-test123');

    expect(await screen.findByText(/forbidden token prefix/i)).toBeInTheDocument();

    await user.clear(reasonField);
    await user.type(reasonField, 'compromised hardware');
    await user.click(screen.getByRole('button', { name: /continue/i }));

    const finalButton = await screen.findByRole('button', { name: /revoke admin/i });
    expect(finalButton).toBeDisabled();

    await user.type(screen.getByLabelText(/type "admin@example.com" to confirm/i), 'wrong-token');
    expect(finalButton).toBeDisabled();

    await user.clear(screen.getByLabelText(/type "admin@example.com" to confirm/i));
    await user.type(screen.getByLabelText(/type "admin@example.com" to confirm/i), 'admin@example.com');
    expect(finalButton).toBeEnabled();
    await user.click(finalButton);

    await waitFor(() => expect(onConfirm).toHaveBeenCalledWith('compromised hardware'));
  });
});
