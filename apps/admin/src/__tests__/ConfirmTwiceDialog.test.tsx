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
        actionLabel="Thu hồi quyền admin"
        targetLabel="admin@example.com"
        consequences={['Tài khoản admin này sẽ không đăng nhập được nữa.']}
        confirmationToken="admin@example.com"
        finalButtonLabel="Thu hồi admin"
        onConfirm={onConfirm}
      />,
    );

    const reasonField = screen.getByLabelText('Lý do (ghi vào nhật ký audit)');
    await user.type(reasonField, 'key:sk-test123');

    expect(await screen.findByText(/tiền tố token bị cấm/i)).toBeInTheDocument();

    await user.clear(reasonField);
    await user.type(reasonField, 'phần cứng bị xâm phạm');
    await user.click(screen.getByRole('button', { name: /tiếp tục/i }));

    const finalButton = await screen.findByRole('button', { name: /thu hồi admin/i });
    expect(finalButton).toBeDisabled();

    await user.type(screen.getByLabelText(/nhập "admin@example.com" để xác nhận/i), 'wrong-token');
    expect(finalButton).toBeDisabled();

    await user.clear(screen.getByLabelText(/nhập "admin@example.com" để xác nhận/i));
    await user.type(screen.getByLabelText(/nhập "admin@example.com" để xác nhận/i), 'admin@example.com');
    expect(finalButton).toBeEnabled();
    await user.click(finalButton);

    await waitFor(() => expect(onConfirm).toHaveBeenCalledWith('phần cứng bị xâm phạm'));
  });
});
