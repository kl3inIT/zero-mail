import { useForm } from '@tanstack/react-form';
import { z } from 'zod';

import { Button } from '@/components/ui/button';
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import type { ProviderKey } from '@/features/master-keys/master-keys-api';
import { useUpdateProviderKey } from '@/features/master-keys/use-update-provider-key';

const formSchema = z.object({
  label: z.string().max(64),
  baseUrl: z.string().max(500),
});

function extractZodMessage(value: unknown): string {
  if (value && typeof value === 'object' && 'message' in value) {
    return String((value as { message: unknown }).message ?? value);
  }
  return String(value);
}

export type EditProviderKeyDialogProps = {
  provider: string;
  entry: ProviderKey | null;
  onClose: () => void;
};

export function EditProviderKeyDialog({
  provider,
  entry,
  onClose,
}: EditProviderKeyDialogProps) {
  return (
    <Dialog open={entry !== null} onOpenChange={(open) => !open && onClose()}>
      {entry && (
        <EditForm
          provider={provider}
          entry={entry}
          onSuccess={onClose}
        />
      )}
    </Dialog>
  );
}

function EditForm({
  provider,
  entry,
  onSuccess,
}: {
  provider: string;
  entry: ProviderKey;
  onSuccess: () => void;
}) {
  const updateMutation = useUpdateProviderKey(provider);

  const form = useForm({
    defaultValues: {
      label: entry.label ?? '',
      baseUrl: entry.baseUrl ?? '',
    },
    validators: { onChange: formSchema },
    onSubmit: async ({ value }) => {
      await updateMutation.mutateAsync({
        provider,
        keyId: entry.keyId,
        label: value.label.trim() || null,
        baseUrl: value.baseUrl.trim() || null,
      });
      onSuccess();
    },
  });

  return (
    <DialogContent className="sm:max-w-xl">
      <DialogHeader>
        <DialogTitle>Sửa key: {provider}</DialogTitle>
        <DialogDescription>
          Cập nhật nhãn và base URL. Plaintext key không đổi, dùng "Xoay khóa" nếu cần xoay
          secret.
        </DialogDescription>
      </DialogHeader>

      <form
        onSubmit={(event) => {
          event.preventDefault();
          event.stopPropagation();
          void form.handleSubmit();
        }}
        className="space-y-4"
      >
        <form.Field name="label">
          {(field) => (
            <div className="space-y-2">
              <Label htmlFor={field.name}>Nhãn</Label>
              <Input
                id={field.name}
                value={field.state.value}
                onChange={(event) => field.handleChange(event.target.value)}
                placeholder="email / nickname để phân biệt"
              />
              {field.state.meta.errors[0] != null && (
                <p className="text-destructive text-xs">
                  {extractZodMessage(field.state.meta.errors[0])}
                </p>
              )}
            </div>
          )}
        </form.Field>

        <form.Field name="baseUrl">
          {(field) => (
            <div className="space-y-2">
              <Label htmlFor={field.name}>Base URL</Label>
              <Input
                id={field.name}
                value={field.state.value}
                onChange={(event) => field.handleChange(event.target.value)}
                placeholder="https://api.example.com/v1"
              />
              {field.state.meta.errors[0] != null && (
                <p className="text-destructive text-xs">
                  {extractZodMessage(field.state.meta.errors[0])}
                </p>
              )}
            </div>
          )}
        </form.Field>

        <DialogFooter className="gap-2">
          <DialogClose render={(closeProps) => <Button variant="ghost" {...closeProps} />}>
            Hủy
          </DialogClose>
          <form.Subscribe selector={(state) => state.canSubmit}>
            {(canSubmit) => (
              <Button type="submit" disabled={!canSubmit || updateMutation.isPending}>
                {updateMutation.isPending ? 'Đang lưu…' : 'Lưu'}
              </Button>
            )}
          </form.Subscribe>
        </DialogFooter>
      </form>
    </DialogContent>
  );
}
