import { createFileRoute, Link } from '@tanstack/react-router';
import { ArrowLeftIcon, RotateCwIcon } from 'lucide-react';
import { useEffect, useState } from 'react';

import { MaskedSecretField } from '@/components/MaskedSecretField';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
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
  const [plaintextKey, setPlaintextKey] = useState('');
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

  const canSave = editing && plaintextKey.length >= 10 && editSessionToken && testResult === 'OK' && reason.length >= 8;

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
      plaintextKey,
      keyFormat,
      baseUrl: baseUrl || null,
      editSessionToken,
    });
    setTestResult(result.result);
  }

  async function saveCurrentKey() {
    if (!editSessionToken) return;
    await saveMasterKey.mutateAsync({
      provider,
      plaintextKey,
      keyFormat,
      baseUrl: baseUrl || null,
      editSessionToken,
      reason,
    });
    setPlaintextKey('');
    setEditing(false);
    setTestResult(null);
    setSuccessMessage(`${row?.displayName ?? provider} key saved`);
  }

  async function rotateCurrentKey() {
    if (!editSessionToken) return;
    await rotateMasterKey.mutateAsync({
      provider,
      plaintextKey,
      keyFormat,
      baseUrl: baseUrl || null,
      editSessionToken,
      reason,
    });
    setPlaintextKey('');
    setEditing(false);
    setTestResult(null);
  }

  return (
    <div className="space-y-6">
      <header className="flex items-end justify-between gap-4">
        <div>
          <Button variant="ghost" size="sm" asChild className="mb-2 px-0">
            <Link to="/master-keys">
              <ArrowLeftIcon className="size-4" />
              Master keys
            </Link>
          </Button>
          <p className="font-mono text-[11px] tracking-wider text-muted-foreground uppercase">LLM operations</p>
          <h1 className="text-xl font-semibold text-ink">{row?.displayName ?? provider} master key</h1>
        </div>
        {testResult && <Badge variant={testResult === 'OK' ? 'default' : 'destructive'}>Tested {testResult}</Badge>}
      </header>
      <Card>
        <CardHeader>
          <CardTitle>Credential</CardTitle>
          <CardDescription>Responses render masked-only after save.</CardDescription>
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
              plaintextValue={plaintextKey}
              onPlaintextChange={(value) => {
                setPlaintextKey(value);
                setTestResult(null);
              }}
              onEdit={() => void startEditing()}
              editDisabled={editSession.isPending}
            />
            <div className="grid gap-4 md:grid-cols-2">
              <div className="space-y-2">
                <Label>Key format</Label>
                <Select value={keyFormat} onValueChange={(value) => setKeyFormat(value as KeyFormat)} disabled={provider !== 'ROUTER_9R'}>
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="OPENAI_FORMAT">OpenAI format</SelectItem>
                    <SelectItem value="ANTHROPIC_FORMAT">Anthropic format</SelectItem>
                    {provider === 'GOOGLE' && <SelectItem value="GOOGLE_FORMAT">Google format</SelectItem>}
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
              <Label htmlFor="master-key-reason">Reason</Label>
              <Input id="master-key-reason" aria-label="Reason" value={reason} onChange={(event) => setReason(event.target.value)} />
            </div>
            {successMessage && <div className="rounded-md border border-border bg-secondary px-3 py-2 text-sm">{successMessage}</div>}
            <div className="flex flex-wrap gap-2">
              <Button type="button" variant="secondary" disabled={!editing || plaintextKey.length < 10 || !editSessionToken} onClick={() => void testCurrentKey()}>
                Test connection
              </Button>
              <Button type="submit" disabled={!canSave}>
                Save key
              </Button>
              <Button type="button" variant="destructive" disabled={!canSave} onClick={() => void rotateCurrentKey()}>
                <RotateCwIcon className="size-4" />
                Rotate key
              </Button>
            </div>
            {!canSave && editing && (
              <p className="text-sm text-muted-foreground">Save blocked - run Test connection first and wait for PASS.</p>
            )}
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
