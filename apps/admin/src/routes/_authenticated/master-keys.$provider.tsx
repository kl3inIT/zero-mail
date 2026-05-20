import { createFileRoute, Link } from '@tanstack/react-router';
import { ArrowLeftIcon, RotateCwIcon } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';

import { MaskedSecretField } from '@/components/MaskedSecretField';
import { Badge } from '@/components/ui/badge';
import { Button, buttonVariants } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import type { KeyFormat, TestConnectionResult } from '@/features/master-keys/master-keys-api';
import { useEditSession } from '@/features/master-keys/use-edit-session';
import { useMasterKey } from '@/features/master-keys/use-master-keys';
import { useRotateMasterKey } from '@/features/master-keys/use-rotate-master-key';
import { useSaveMasterKey } from '@/features/master-keys/use-save-master-key';
import { useTestConnection } from '@/features/master-keys/use-test-connection';

export const Route = createFileRoute('/_authenticated/master-keys/$provider')({
  component: MasterKeyProviderRoute,
});

const fixedFormats: Record<string, KeyFormat> = {
  OPENAI: 'OPENAI_FORMAT',
  ANTHROPIC: 'ANTHROPIC_FORMAT',
  GOOGLE: 'GOOGLE_FORMAT',
  DEEPSEEK: 'OPENAI_FORMAT',
  OPENROUTER: 'OPENAI_FORMAT',
};

function MasterKeyProviderRoute() {
  const { provider } = Route.useParams();
  const masterKey = useMasterKey(provider);
  const editSession = useEditSession(provider);
  const testConnection = useTestConnection();
  const saveMasterKey = useSaveMasterKey();
  const rotateMasterKey = useRotateMasterKey();
  const [editing, setEditing] = useState(false);
  // CR-04: keep plaintext out of React fiber state. The ref is mutable, not snapshot-able by
  // React DevTools/HMR, and is read imperatively at mutation time. Only the length is mirrored
  // into state for UI gating (length is not secret).
  const plaintextKeyRef = useRef<string>('');
  const [plaintextLength, setPlaintextLength] = useState(0);
  const [editSessionToken, setEditSessionToken] = useState<string | null>(null);
  const [keyFormat, setKeyFormat] = useState<KeyFormat>('OPENAI_FORMAT');
  const [baseUrl, setBaseUrl] = useState('');
  const [reason, setReason] = useState('');
  const [testResult, setTestResult] = useState<TestConnectionResult | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const row = masterKey.data;

  useEffect(() => {
    if (!row) return;
    setKeyFormat(row.keyFormat ?? fixedFormats[row.provider] ?? 'OPENAI_FORMAT');
    setBaseUrl(row.baseUrl ?? '');
  }, [row]);

  function setPlaintextKey(value: string) {
    plaintextKeyRef.current = value;
    setPlaintextLength(value.length);
  }

  function clearPlaintextKey() {
    plaintextKeyRef.current = '';
    setPlaintextLength(0);
  }

  const canSave = editing && plaintextLength >= 10 && editSessionToken && testResult === 'OK' && reason.length >= 8;

  async function startEditing() {
    const session = await editSession.mutateAsync();
    setEditSessionToken(session.token);
    setEditing(true);
    setTestResult(null);
    setSuccessMessage(null);
  }

  async function testCurrentKey() {
    if (!editSessionToken) return;
    const result = await testConnection.mutateAsync({
      provider,
      plaintextKey: plaintextKeyRef.current,
      keyFormat,
      baseUrl: baseUrl || null,
      editSessionToken,
    });
    setTestResult(result.result);
  }

  async function saveCurrentKey() {
    if (!editSessionToken) return;
    // Capture the secret locally, wipe the ref BEFORE awaiting so any error in the
    // mutation cannot leave the secret reachable from React state during render.
    const capturedKey = plaintextKeyRef.current;
    clearPlaintextKey();
    setEditing(false);
    setTestResult(null);
    await saveMasterKey.mutateAsync({
      provider,
      plaintextKey: capturedKey,
      keyFormat,
      baseUrl: baseUrl || null,
      editSessionToken,
      reason,
    });
    setSuccessMessage(`Đã lưu khóa ${row?.displayName ?? provider}`);
  }

  async function rotateCurrentKey() {
    if (!editSessionToken) return;
    const capturedKey = plaintextKeyRef.current;
    clearPlaintextKey();
    setEditing(false);
    setTestResult(null);
    await rotateMasterKey.mutateAsync({
      provider,
      plaintextKey: capturedKey,
      keyFormat,
      baseUrl: baseUrl || null,
      editSessionToken,
      reason,
    });
  }

  return (
    <div className="space-y-6">
      <header className="flex items-end justify-between gap-4">
        <div>
          <Link to="/master-keys" className={buttonVariants({ variant: 'ghost', size: 'sm', className: 'mb-2 px-0' })}>
            <ArrowLeftIcon className="size-4" />
            Khóa nền tảng
          </Link>
          <p className="font-mono text-[11px] tracking-wider text-muted-foreground uppercase">Vận hành LLM</p>
          <h1 className="text-xl font-semibold text-ink">Khóa nền tảng {row?.displayName ?? provider}</h1>
        </div>
        {testResult && <Badge variant={testResult === 'OK' ? 'default' : 'destructive'}>Đã test {testResult}</Badge>}
      </header>
      <Card>
        <CardHeader>
          <CardTitle>Thông tin xác thực</CardTitle>
          <CardDescription>Phản hồi chỉ hiển thị bản che mặt nạ sau khi lưu.</CardDescription>
        </CardHeader>
        <CardContent>
          <form
            className="space-y-5"
            onSubmit={(event) => {
              event.preventDefault();
              void saveCurrentKey();
            }}
          >
            <MaskedSecretField
              maskedValue={row?.maskedKey ?? null}
              editing={editing}
              // Initial value when an edit session starts is always empty; the field
              // is uncontrolled while editing — value lives in the input DOM + ref only.
              defaultPlaintextValue=""
              onPlaintextChange={(value) => {
                setPlaintextKey(value);
                setTestResult(null);
              }}
              onEdit={() => void startEditing()}
              editDisabled={editSession.isPending}
            />
            <div className="grid gap-4 md:grid-cols-2">
              <div className="space-y-2">
                <Label>Định dạng khóa</Label>
                <Select value={keyFormat} onValueChange={(value) => setKeyFormat(value as KeyFormat)} disabled={provider !== 'ROUTER_9R'}>
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="OPENAI_FORMAT">Định dạng OpenAI</SelectItem>
                    <SelectItem value="ANTHROPIC_FORMAT">Định dạng Anthropic</SelectItem>
                    {provider === 'GOOGLE' && <SelectItem value="GOOGLE_FORMAT">Định dạng Google</SelectItem>}
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-2">
                <Label htmlFor="master-key-base-url">Base URL</Label>
                <Input
                  id="master-key-base-url"
                  value={baseUrl}
                  onChange={(event) => setBaseUrl(event.target.value)}
                  disabled={provider !== 'ROUTER_9R' && provider !== 'OPENROUTER'}
                />
              </div>
            </div>
            <div className="space-y-2">
              <Label htmlFor="master-key-reason">Lý do</Label>
              <Input id="master-key-reason" aria-label="Lý do" value={reason} onChange={(event) => setReason(event.target.value)} />
            </div>
            {successMessage && <div className="rounded-md border border-border bg-secondary px-3 py-2 text-sm">{successMessage}</div>}
            <div className="flex flex-wrap gap-2">
              <Button type="button" variant="secondary" disabled={!editing || plaintextLength < 10 || !editSessionToken} onClick={() => void testCurrentKey()}>
                Kiểm tra kết nối
              </Button>
              <Button type="submit" disabled={!canSave}>
                Lưu khóa
              </Button>
              <Button type="button" variant="destructive" disabled={!canSave} onClick={() => void rotateCurrentKey()}>
                <RotateCwIcon className="size-4" />
                Xoay khóa
              </Button>
            </div>
            {!canSave && editing && (
              <p className="text-sm text-muted-foreground">Không thể lưu — vui lòng chạy Kiểm tra kết nối và đợi kết quả PASS.</p>
            )}
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
