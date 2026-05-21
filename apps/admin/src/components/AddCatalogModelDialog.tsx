import { useForm } from '@tanstack/react-form';
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
import type { CatalogProvider } from '@/features/catalog/catalog-api';
import { useCreateModel } from '@/features/catalog/use-create-model';

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

  const form = useForm({
    defaultValues: {
      modelId: '',
      displayName: '',
      recommended: false,
    },
    validators: { onChange: formSchema },
    onSubmit: async ({ value }) => {
      await createMutation.mutateAsync({
        provider,
        modelId: value.modelId.trim(),
        displayName: value.displayName.trim(),
        recommended: value.recommended,
      });
      onSuccess();
    },
  });

  return (
    <DialogContent className="sm:max-w-xl">
      <DialogHeader>
        <DialogTitle>Thêm model — {provider}</DialogTitle>
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

        <DialogFooter className="gap-2">
          <DialogClose render={(closeProps) => <Button variant="ghost" {...closeProps} />}>
            Hủy
          </DialogClose>
          <form.Subscribe selector={(state) => state.canSubmit}>
            {(canSubmit) => (
              <Button type="submit" disabled={!canSubmit || createMutation.isPending}>
                {createMutation.isPending ? 'Đang lưu…' : 'Thêm'}
              </Button>
            )}
          </form.Subscribe>
        </DialogFooter>
      </form>
    </DialogContent>
  );
}
