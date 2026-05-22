import { useForm } from '@tanstack/react-form';
import { useState } from 'react';
import { toast } from 'sonner';
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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import {
  testMasterKeyConnection,
  type KeyFormat,
  type TestConnectionResult,
} from '@/features/master-keys/master-keys-api';
import { useAddProviderKey } from '@/features/master-keys/use-add-provider-key';
import { useEditSession } from '@/features/master-keys/use-edit-session';

const KEY_FORMAT_OPTIONS: { value: KeyFormat; label: string }[] = [
  { value: 'OPENAI_FORMAT', label: 'OpenAI compatible' },
  { value: 'ANTHROPIC_FORMAT', label: 'Anthropic' },
  { value: 'GOOGLE_FORMAT', label: 'Google' },
];

function extractZodMessage(value: unknown): string {
  if (value && typeof value === 'object' && 'message' in value) {
    return String((value as { message: unknown }).message ?? value);
  }
  return String(value);
}

const PROVIDER_DEFAULT_FORMAT: Record<string, KeyFormat> = {
  OPENAI: 'OPENAI_FORMAT',
  ANTHROPIC: 'ANTHROPIC_FORMAT',
  GOOGLE: 'GOOGLE_FORMAT',
  DEEPSEEK: 'OPENAI_FORMAT',
  OPENROUTER: 'OPENAI_FORMAT',
  ROUTER_9R: 'OPENAI_FORMAT',
};

const formSchema = z.object({
  plaintextKey: z.string().min(10, 'Key tối thiểu 10 ký tự.').max(2048),
  baseUrl: z.string().max(500),
  label: z.string().max(64),
});

export type AddProviderKeyDialogProps = {
  provider: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
};

export function AddProviderKeyDialog({
  provider,
  open,
  onOpenChange,
}: AddProviderKeyDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      {open && (
        <AddProviderKeyForm
          provider={provider}
          onSuccess={() => onOpenChange(false)}
        />
      )}
    </Dialog>
  );
}

function AddProviderKeyForm({
  provider,
  onSuccess,
}: {
  provider: string;
  onSuccess: () => void;
}) {
  const editSession = useEditSession(provider);
  const addMutation = useAddProviderKey(provider);
  const [keyFormat, setKeyFormat] = useState<KeyFormat>(
    PROVIDER_DEFAULT_FORMAT[provider] ?? 'OPENAI_FORMAT',
  );
  const [testResult, setTestResult] = useState<TestConnectionResult | null>(null);
  const [testing, setTesting] = useState(false);

  const form = useForm({
    defaultValues: {
      plaintextKey: '',
      baseUrl: '',
      label: '',
    },
    validators: { onChange: formSchema },
    onSubmit: async ({ value }) => {
      const session = await editSession.mutateAsync();
      await addMutation.mutateAsync({
        provider,
        plaintextKey: value.plaintextKey,
        keyFormat,
        baseUrl: value.baseUrl?.trim() || null,
        label: value.label?.trim() || null,
        editSessionToken: session.token,
      });
      onSuccess();
    },
  });

  async function runTest() {
    const value = form.state.values;
    if (!value.plaintextKey || value.plaintextKey.length < 10) {
      toast.error('Nhập key trước khi test.');
      return;
    }
    setTesting(true);
    setTestResult(null);
    try {
      const session = await editSession.mutateAsync();
      const result = await testMasterKeyConnection({
        provider,
        plaintextKey: value.plaintextKey,
        keyFormat,
        baseUrl: value.baseUrl?.trim() || null,
        editSessionToken: session.token,
      });
      setTestResult(result.result);
      if (result.result === 'OK') {
        toast.success('Kết nối OK.');
      } else {
        toast.error(`Kết nối thất bại: ${result.result}`);
      }
    } finally {
      setTesting(false);
    }
  }

  return (
    <DialogContent className="sm:max-w-xl">
      <DialogHeader>
        <DialogTitle>Thêm key mới — {provider}</DialogTitle>
        <DialogDescription>
          Key phải test PASS trước khi lưu. Priority sẽ được gán vào cuối chuỗi failover.
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
        <form.Field name="plaintextKey">
          {(field) => (
            <div className="space-y-2">
              <Label htmlFor={field.name}>Plaintext key</Label>
              <Input
                id={field.name}
                type="password"
                autoComplete="off"
                value={field.state.value}
                onChange={(event) => {
                  field.handleChange(event.target.value);
                  setTestResult(null);
                }}
                placeholder="sk-..."
              />
              {field.state.meta.errors[0] != null && (
                <p className="text-destructive text-xs">
                  {extractZodMessage(field.state.meta.errors[0])}
                </p>
              )}
            </div>
          )}
        </form.Field>

        <div className="space-y-2">
          <Label htmlFor="add-key-format">Định dạng</Label>
          <Select
            value={keyFormat}
            onValueChange={(next) => setKeyFormat(next as KeyFormat)}
          >
            <SelectTrigger id="add-key-format" className="h-10 w-full">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {KEY_FORMAT_OPTIONS.map((entry) => (
                <SelectItem key={entry.value} value={entry.value}>
                  {entry.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        <form.Field name="baseUrl">
          {(field) => (
            <div className="space-y-2">
              <Label htmlFor={field.name}>Base URL (tuỳ chọn)</Label>
              <Input
                id={field.name}
                value={field.state.value}
                onChange={(event) => field.handleChange(event.target.value)}
                placeholder="https://api.example.com/v1"
              />
            </div>
          )}
        </form.Field>

        <form.Field name="label">
          {(field) => (
            <div className="space-y-2">
              <Label htmlFor={field.name}>Nhãn (tuỳ chọn)</Label>
              <Input
                id={field.name}
                value={field.state.value}
                onChange={(event) => field.handleChange(event.target.value)}
                placeholder="email / nickname để phân biệt"
              />
            </div>
          )}
        </form.Field>

        {testResult && (
          <div
            className={
              testResult === 'OK'
                ? 'bg-green-soft text-green rounded-md px-3 py-2 text-sm'
                : 'bg-destructive/10 text-destructive rounded-md px-3 py-2 text-sm'
            }
          >
            Test result: {testResult}
          </div>
        )}

        <DialogFooter className="gap-2">
          <DialogClose render={(closeProps) => <Button variant="ghost" {...closeProps} />}>
            Hủy
          </DialogClose>
          <Button
            type="button"
            variant="outline"
            disabled={testing}
            onClick={() => void runTest()}
          >
            {testing ? 'Đang test…' : 'Test kết nối'}
          </Button>
          <form.Subscribe selector={(state) => state.canSubmit}>
            {(canSubmit) => (
              <Button
                type="submit"
                disabled={!canSubmit || testResult !== 'OK' || addMutation.isPending}
              >
                {addMutation.isPending ? 'Đang lưu…' : 'Lưu key'}
              </Button>
            )}
          </form.Subscribe>
        </DialogFooter>
      </form>
    </DialogContent>
  );
}
