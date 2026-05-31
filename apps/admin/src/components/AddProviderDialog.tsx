import { useMutation } from '@tanstack/react-query';
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
  mintEditSession,
  testMasterKeyConnection,
  type KeyFormat,
  type TestConnectionResult,
} from '@/features/master-keys/master-keys-api';
import { useCreateProvider } from '@/features/master-keys/use-create-provider';

const COMPATIBLE_TYPE_OPTIONS: { value: KeyFormat; label: string }[] = [
  { value: 'OPENAI_FORMAT', label: 'OpenAI-compatible' },
  { value: 'ANTHROPIC_FORMAT', label: 'Anthropic-compatible' },
];

const formSchema = z.object({
  providerId: z
    .string()
    .min(2, 'Tối thiểu 2 ký tự.')
    .max(32, 'Tối đa 32 ký tự.')
    .regex(/^[A-Za-z0-9][A-Za-z0-9_:-]{1,31}$/, 'Chỉ dùng chữ, số, _, :, -.'),
  displayName: z.string().min(1, 'Bắt buộc.').max(120),
  defaultBaseUrl: z.url('Nhập URL hợp lệ.').max(500),
  plaintextKey: z.string().min(10, 'Key tối thiểu 10 ký tự.').max(2048),
  label: z.string().max(64),
});

function extractZodMessage(value: unknown): string {
  if (value && typeof value === 'object' && 'message' in value) {
    return String((value as { message: unknown }).message ?? value);
  }
  return String(value);
}

function normalizeProviderId(providerId: string) {
  return providerId.trim().toUpperCase();
}

function testSignature(input: {
  providerId: string;
  compatibleType: KeyFormat;
  defaultBaseUrl: string;
  plaintextKey: string;
}) {
  return [
    normalizeProviderId(input.providerId),
    input.compatibleType,
    input.defaultBaseUrl.trim(),
    input.plaintextKey,
  ].join('|');
}

type SuccessfulTest = {
  signature: string;
  token: string;
  result: TestConnectionResult;
};

export type AddProviderDialogProps = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
};

export function AddProviderDialog({ open, onOpenChange }: AddProviderDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      {open && <AddProviderForm onSuccess={() => onOpenChange(false)} />}
    </Dialog>
  );
}

function AddProviderForm({ onSuccess }: { onSuccess: () => void }) {
  const createProviderMutation = useCreateProvider();
  const editSessionMutation = useMutation({ mutationFn: mintEditSession });
  const [compatibleType, setCompatibleType] = useState<KeyFormat>('OPENAI_FORMAT');
  const [successfulTest, setSuccessfulTest] = useState<SuccessfulTest | null>(null);
  const [lastTestResult, setLastTestResult] = useState<TestConnectionResult | null>(null);
  const [testing, setTesting] = useState(false);

  const form = useForm({
    defaultValues: {
      providerId: '',
      displayName: '',
      defaultBaseUrl: '',
      plaintextKey: '',
      label: '',
    },
    validators: { onChange: formSchema },
    onSubmit: async ({ value }) => {
      const providerId = normalizeProviderId(value.providerId);
      const signature = testSignature({
        providerId,
        compatibleType,
        defaultBaseUrl: value.defaultBaseUrl,
        plaintextKey: value.plaintextKey,
      });
      if (!successfulTest || successfulTest.signature !== signature) {
        toast.error('Test kết nối trước khi lưu provider.');
        return;
      }
      await createProviderMutation.mutateAsync({
        providerId,
        displayName: value.displayName.trim(),
        compatibleType,
        defaultBaseUrl: value.defaultBaseUrl.trim(),
        plaintextKey: value.plaintextKey,
        label: value.label.trim() || null,
        editSessionToken: successfulTest.token,
      });
      onSuccess();
    },
  });

  function resetTestState() {
    setSuccessfulTest(null);
    setLastTestResult(null);
  }

  async function runTest() {
    const value = form.state.values;
    const parsed = formSchema.safeParse(value);
    if (!parsed.success) {
      toast.error('Điền đủ provider, base URL và key trước khi test.');
      return;
    }
    const providerId = normalizeProviderId(value.providerId);
    setTesting(true);
    setSuccessfulTest(null);
    setLastTestResult(null);
    try {
      const session = await editSessionMutation.mutateAsync(providerId);
      const result = await testMasterKeyConnection({
        provider: providerId,
        plaintextKey: value.plaintextKey,
        keyFormat: compatibleType,
        baseUrl: value.defaultBaseUrl.trim(),
        editSessionToken: session.token,
      });
      setLastTestResult(result.result);
      if (result.result === 'OK') {
        setSuccessfulTest({
          signature: testSignature({
            providerId,
            compatibleType,
            defaultBaseUrl: value.defaultBaseUrl,
            plaintextKey: value.plaintextKey,
          }),
          token: session.token,
          result: result.result,
        });
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
        <DialogTitle>Thêm provider</DialogTitle>
        <DialogDescription>
          Provider tương thích OpenAI hoặc Anthropic. Phải test PASS trước khi lưu.
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
        <form.Field name="providerId">
          {(field) => (
            <div className="space-y-2">
              <Label htmlFor={field.name}>Provider ID</Label>
              <Input
                id={field.name}
                value={field.state.value}
                onChange={(event) => {
                  field.handleChange(event.target.value);
                  resetTestState();
                }}
                placeholder="OPENROUTER"
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
                placeholder="OpenRouter"
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
          <Label htmlFor="compatible-type">Kiểu tương thích</Label>
          <Select
            value={compatibleType}
            onValueChange={(next) => {
              setCompatibleType(next as KeyFormat);
              resetTestState();
            }}
          >
            <SelectTrigger id="compatible-type" className="h-10 w-full">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {COMPATIBLE_TYPE_OPTIONS.map((option) => (
                <SelectItem key={option.value} value={option.value}>
                  {option.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        <form.Field name="defaultBaseUrl">
          {(field) => (
            <div className="space-y-2">
              <Label htmlFor={field.name}>Base URL</Label>
              <Input
                id={field.name}
                value={field.state.value}
                onChange={(event) => {
                  field.handleChange(event.target.value);
                  resetTestState();
                }}
                placeholder="https://openrouter.ai/api/v1"
              />
              {field.state.meta.errors[0] != null && (
                <p className="text-destructive text-xs">
                  {extractZodMessage(field.state.meta.errors[0])}
                </p>
              )}
            </div>
          )}
        </form.Field>

        <form.Field name="plaintextKey">
          {(field) => (
            <div className="space-y-2">
              <Label htmlFor={field.name}>API key</Label>
              <Input
                id={field.name}
                type="password"
                autoComplete="off"
                value={field.state.value}
                onChange={(event) => {
                  field.handleChange(event.target.value);
                  resetTestState();
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

        <form.Field name="label">
          {(field) => (
            <div className="space-y-2">
              <Label htmlFor={field.name}>Nhãn key</Label>
              <Input
                id={field.name}
                value={field.state.value}
                onChange={(event) => field.handleChange(event.target.value)}
                placeholder="primary"
              />
            </div>
          )}
        </form.Field>

        {lastTestResult && (
          <div
            className={
              lastTestResult === 'OK'
                ? 'bg-green-soft text-green rounded-md px-3 py-2 text-sm'
                : 'bg-destructive/10 text-destructive rounded-md px-3 py-2 text-sm'
            }
          >
            Kết quả test: {lastTestResult}
          </div>
        )}

        <DialogFooter className="gap-2">
          <DialogClose render={(closeProps) => <Button variant="ghost" {...closeProps} />}>
            Hủy
          </DialogClose>
          <Button type="button" variant="outline" disabled={testing} onClick={() => void runTest()}>
            {testing ? 'Đang test...' : 'Test kết nối'}
          </Button>
          <form.Subscribe selector={(state) => state.canSubmit}>
            {(canSubmit) => (
              <Button
                type="submit"
                disabled={!canSubmit || !successfulTest || createProviderMutation.isPending}
              >
                {createProviderMutation.isPending ? 'Đang lưu...' : 'Lưu'}
              </Button>
            )}
          </form.Subscribe>
        </DialogFooter>
      </form>
    </DialogContent>
  );
}
