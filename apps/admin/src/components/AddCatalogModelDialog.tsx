import { useForm } from '@tanstack/react-form';
import { useState } from 'react';
import { z } from 'zod';

import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
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
import type {
  CatalogModelVerificationResponse,
  CatalogProvider,
} from '@/features/catalog/catalog-api';
import { useCreateModel } from '@/features/catalog/use-create-model';
import { useEnableModel } from '@/features/catalog/use-enable-model';

const formSchema = z.object({
  modelId: z.string().min(1, 'Bắt buộc.').max(200),
  displayName: z.string().min(1, 'Bắt buộc.').max(120),
  recommended: z.boolean(),
});

function extractZodMessage(value: unknown): string {
  if (value && typeof value === 'object' && 'message' in value) {
    return String((value as { message: unknown }).message ?? value);
  }
  return String(value);
}

export type AddCatalogModelDialogProps = {
  provider: CatalogProvider;
  open: boolean;
  onOpenChange: (open: boolean) => void;
};

export function AddCatalogModelDialog({
  provider,
  open,
  onOpenChange,
}: AddCatalogModelDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      {open && (
        <AddCatalogModelForm
          provider={provider}
          onSuccess={() => onOpenChange(false)}
        />
      )}
    </Dialog>
  );
}

function AddCatalogModelForm({
  provider,
  onSuccess,
}: {
  provider: CatalogProvider;
  onSuccess: () => void;
}) {
  const createMutation = useCreateModel();
  const enableMutation = useEnableModel();
  const [failedVerification, setFailedVerification] =
    useState<CatalogModelVerificationResponse | null>(null);
  const [deprecatedModelId, setDeprecatedModelId] = useState<string | null>(null);

  const isPending = createMutation.isPending || enableMutation.isPending;

  async function handleReEnable() {
    if (!deprecatedModelId) return;
    const result = await enableMutation.mutateAsync(deprecatedModelId);
    if (result.status === 'VERIFIED' || result.status === 'STALE') {
      onSuccess();
    } else {
      setFailedVerification(result);
      setDeprecatedModelId(null);
    }
  }

  const form = useForm({
    defaultValues: {
      modelId: '',
      displayName: '',
      recommended: false,
    },
    validators: { onChange: formSchema },
    onSubmit: async ({ value }) => {
      setFailedVerification(null);
      setDeprecatedModelId(null);
      const { outcome, verification } = await createMutation.mutateAsync({
        provider,
        modelId: value.modelId.trim(),
        displayName: value.displayName.trim(),
        recommended: value.recommended,
      });
      if (outcome === 'already-exists') {
        // Row exists (either active or deprecated). If verify fails it's likely deprecated
        // and needs re-enable, not a new insert.
        if (verification.status === 'VERIFIED' || verification.status === 'STALE') {
          onSuccess();
          return;
        }
        setDeprecatedModelId(value.modelId.trim());
        return;
      }
      if (verification.status === 'VERIFIED' || verification.status === 'STALE') {
        onSuccess();
        return;
      }
      // FAILED / UNTESTED — keep the dialog open so the admin sees the probe error
      // and can either fix the model id or close to defer the verify step.
      setFailedVerification(verification);
    },
  });

  return (
    <DialogContent className="sm:max-w-xl">
      <DialogHeader>
        <DialogTitle>Thêm model: {provider}</DialogTitle>
        <DialogDescription>
          Model ID phải khớp định danh mà provider chấp nhận (vd: <code>gpt-5-nano</code>,{' '}
          <code>claude-haiku-4-5</code>).
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
        <form.Field name="modelId">
          {(field) => (
            <div className="space-y-2">
              <Label htmlFor={field.name}>Model ID</Label>
              <Input
                id={field.name}
                value={field.state.value}
                onChange={(event) => field.handleChange(event.target.value)}
                placeholder="gpt-5-nano"
              />
              {field.state.meta.errors[0] != null && (
                <p className="text-destructive text-xs">
                  {extractZodMessage(field.state.meta.errors[0])}
                </p>
              )}
            </div>
          )}
        </form.Field>

        <form.Field name="displayName">
          {(field) => (
            <div className="space-y-2">
              <Label htmlFor={field.name}>Tên hiển thị</Label>
              <Input
                id={field.name}
                value={field.state.value}
                onChange={(event) => field.handleChange(event.target.value)}
                placeholder="GPT-5 Nano"
              />
              {field.state.meta.errors[0] != null && (
                <p className="text-destructive text-xs">
                  {extractZodMessage(field.state.meta.errors[0])}
                </p>
              )}
            </div>
          )}
        </form.Field>

        <form.Field name="recommended">
          {(field) => (
            <div className="flex items-center gap-2">
              <Checkbox
                id={field.name}
                checked={field.state.value}
                onCheckedChange={(value) => field.handleChange(value === true)}
              />
              <Label htmlFor={field.name} className="cursor-pointer text-sm font-normal">
                Đánh dấu là model đề xuất
              </Label>
            </div>
          )}
        </form.Field>

        {deprecatedModelId && (
          <div className="rounded-md border border-amber-500/40 bg-amber-500/5 p-3 text-xs">
            <p className="font-medium text-amber-700 dark:text-amber-400">
              Model <code>{deprecatedModelId}</code> đã tồn tại nhưng đang bị vô hiệu. Bấm "Kích
              hoạt lại" để bật lại và verify.
            </p>
          </div>
        )}

        {failedVerification && (
          <div className="rounded-md border border-destructive/40 bg-destructive/5 p-3 text-xs">
            <p className="font-medium text-destructive">
              Model đã được lưu nhưng probe thất bại ({failedVerification.status}). Model vẫn ở
              trạng thái <code>{failedVerification.status}</code> và chưa thể gán vào tier, chỉnh
              lại Model ID rồi thử lại, hoặc đóng để giữ row và verify sau.
            </p>
            {failedVerification.error && (
              <p className="text-muted-foreground mt-2 break-all">
                Provider trả về: {failedVerification.error}
              </p>
            )}
          </div>
        )}

        <DialogFooter className="gap-2">
          <DialogClose render={(closeProps) => <Button variant="ghost" {...closeProps} />}>
            {failedVerification || deprecatedModelId ? 'Đóng' : 'Hủy'}
          </DialogClose>
          {deprecatedModelId ? (
            <Button onClick={handleReEnable} disabled={isPending}>
              {enableMutation.isPending ? 'Đang kích hoạt…' : 'Kích hoạt lại'}
            </Button>
          ) : (
            <form.Subscribe selector={(state) => state.canSubmit}>
              {(canSubmit: boolean) => (
                <Button type="submit" disabled={!canSubmit || isPending}>
                  {createMutation.isPending ? 'Đang lưu + verify…' : 'Thêm'}
                </Button>
              )}
            </form.Subscribe>
          )}
        </DialogFooter>
      </form>
    </DialogContent>
  );
}
