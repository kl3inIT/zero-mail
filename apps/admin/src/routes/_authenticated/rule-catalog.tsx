import { createFileRoute } from '@tanstack/react-router';
import {
  ChevronDownIcon,
  ChevronUpIcon,
  PencilIcon,
  PlusIcon,
  SaveIcon,
} from 'lucide-react';
import { type FormEvent, type ReactNode, useMemo, useState } from 'react';

import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import {
  Card,
  CardAction,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Skeleton } from '@/components/ui/skeleton';
import { Switch } from '@/components/ui/switch';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Textarea } from '@/components/ui/textarea';
import type {
  RuleCatalogActionDescriptor,
  RuleCatalogActionDescriptorWriteRequest,
  RuleCatalogExample,
  RuleCatalogExampleWriteRequest,
  RuleCatalogPersona,
  RuleCatalogPersonaWriteRequest,
} from '@/features/rule-catalog/rule-catalog-api';
import { useReorderRuleCatalog } from '@/features/rule-catalog/use-reorder-rule-catalog';
import { useRuleCatalogActions, useRuleCatalogPersonas } from '@/features/rule-catalog/use-rule-catalog';
import { useSaveActionDescriptor, useSetRuleCatalogEnabled } from '@/features/rule-catalog/use-save-action-descriptor';
import { useSaveExample } from '@/features/rule-catalog/use-save-example';
import { useSavePersona } from '@/features/rule-catalog/use-save-persona';

export const Route = createFileRoute('/_authenticated/rule-catalog')({
  component: RuleCatalogRoute,
});

const ADMIN_REASON = 'Admin rule catalog UI update';
const RISK_OPTIONS = ['LOW', 'MEDIUM', 'HIGH'];
const AVAILABILITY_OPTIONS = ['AVAILABLE', 'COMING_SOON', 'DISABLED'];

type EditablePersona = RuleCatalogPersona | null;

type EditableExample = {
  persona: RuleCatalogPersona;
  example: RuleCatalogExample | null;
};

type ExampleRow = RuleCatalogExample & {
  personaId: string;
  personaKey: string;
  personaNameEn: string;
  personaNameVi: string;
};

function RuleCatalogRoute() {
  return <RuleCatalogPage />;
}

export function RuleCatalogPage() {
  const personasQuery = useRuleCatalogPersonas();
  const actionsQuery = useRuleCatalogActions();
  const savePersona = useSavePersona();
  const saveExample = useSaveExample();
  const saveActionDescriptor = useSaveActionDescriptor();
  const setEnabled = useSetRuleCatalogEnabled();
  const reorderCatalog = useReorderRuleCatalog();

  const [activeTab, setActiveTab] = useState('personas');
  const [editingPersona, setEditingPersona] = useState<EditablePersona>(null);
  const [editingExample, setEditingExample] = useState<EditableExample | null>(null);
  const [editingAction, setEditingAction] = useState<RuleCatalogActionDescriptor | null>(null);
  const [personaDialogOpen, setPersonaDialogOpen] = useState(false);

  const personas = useMemo(
    () => sortByOrder(personasQuery.data?.personas ?? []),
    [personasQuery.data],
  );
  const examples = useMemo(
    () =>
      personas.flatMap((persona) =>
        sortByOrder(persona.examples).map((example) => ({
          ...example,
          personaId: persona.personaId,
          personaKey: persona.personaKey,
          personaNameEn: persona.displayNameEn,
          personaNameVi: persona.displayNameVi,
        })),
      ),
    [personas],
  );
  const actions = useMemo(
    () => sortByOrder(actionsQuery.data?.actions ?? []),
    [actionsQuery.data],
  );

  const mutationPending =
    savePersona.isPending ||
    saveExample.isPending ||
    saveActionDescriptor.isPending ||
    setEnabled.isPending ||
    reorderCatalog.isPending;

  function openNewPersonaDialog() {
    setEditingPersona(null);
    setPersonaDialogOpen(true);
  }

  function openEditPersonaDialog(persona: RuleCatalogPersona) {
    setEditingPersona(persona);
    setPersonaDialogOpen(true);
  }

  function setPersonaEnabled(persona: RuleCatalogPersona, enabled: boolean) {
    setEnabled.mutate({
      target: 'persona',
      targetId: persona.personaId,
      enabled,
      reason: ADMIN_REASON,
    });
  }

  function setExampleEnabled(example: ExampleRow, enabled: boolean) {
    setEnabled.mutate({
      target: 'example',
      targetId: example.exampleId,
      enabled,
      reason: ADMIN_REASON,
    });
  }

  function setActionEnabled(action: RuleCatalogActionDescriptor, enabled: boolean) {
    setEnabled.mutate({
      target: 'action',
      targetId: action.actionKey,
      enabled,
      reason: ADMIN_REASON,
    });
  }

  function reorderPersonas(persona: RuleCatalogPersona, direction: -1 | 1) {
    const reordered = moveInList(personas, persona.personaId, direction, 'personaId');
    if (!reordered) return;
    reorderCatalog.mutate({
      target: 'personas',
      request: {
        items: reordered.map((row, index) => ({
          itemId: row.personaId,
          displayOrder: orderForIndex(index),
        })),
        reason: ADMIN_REASON,
      },
    });
  }

  function reorderExamples(example: ExampleRow, direction: -1 | 1) {
    const persona = personas.find((entry) => entry.personaId === example.personaId);
    if (!persona) return;
    const reordered = moveInList(sortByOrder(persona.examples), example.exampleId, direction, 'exampleId');
    if (!reordered) return;
    reorderCatalog.mutate({
      target: 'examples',
      personaId: persona.personaId,
      request: {
        items: reordered.map((row, index) => ({
          itemId: row.exampleId,
          displayOrder: orderForIndex(index),
        })),
        reason: ADMIN_REASON,
      },
    });
  }

  function reorderActions(action: RuleCatalogActionDescriptor, direction: -1 | 1) {
    const reordered = moveInList(actions, action.actionKey, direction, 'actionKey');
    if (!reordered) return;
    reorderCatalog.mutate({
      target: 'actions',
      request: {
        items: reordered.map((row, index) => ({
          actionKey: row.actionKey,
          displayOrder: orderForIndex(index),
        })),
        reason: ADMIN_REASON,
      },
    });
  }

  return (
    <div className="space-y-6">
      <header className="flex items-end justify-between gap-4">
        <div>
          <p className="text-muted-foreground font-mono text-[11px] tracking-wider uppercase">
            Rule operations
          </p>
          <h1 className="text-ink text-xl font-semibold">Rule Catalog</h1>
          <p className="text-muted-foreground mt-1 max-w-2xl text-sm">
            Personas, bilingual examples, and rule action descriptors used by the user rule builder.
          </p>
        </div>
        <Button type="button" onClick={openNewPersonaDialog}>
          <PlusIcon className="size-3.5" />
          Thêm persona
        </Button>
      </header>

      <Tabs value={activeTab} onValueChange={setActiveTab}>
        <TabsList variant="line" className="w-full justify-start">
          <TabsTrigger value="personas">Personas</TabsTrigger>
          <TabsTrigger value="examples">Examples</TabsTrigger>
          <TabsTrigger value="actions">Actions</TabsTrigger>
        </TabsList>

        <TabsContent value="personas" className="space-y-4">
          <Card>
            <CardHeader>
              <CardTitle>Personas</CardTitle>
              <CardDescription>Nhóm ví dụ hiển thị trong chooser của rule prompt box.</CardDescription>
            </CardHeader>
            <CardContent>
              <PersonaTable
                personas={personas}
                loading={personasQuery.isLoading}
                mutationPending={mutationPending}
                onEdit={openEditPersonaDialog}
                onEnabledChange={setPersonaEnabled}
                onMove={reorderPersonas}
              />
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="examples" className="space-y-4">
          <Card>
            <CardHeader>
              <CardTitle>Examples</CardTitle>
              <CardDescription>Prompt mẫu EN/VI được seed từ Inbox Zero và lưu trong DB.</CardDescription>
              <CardAction>
                <Button
                  type="button"
                  size="sm"
                  variant="outline"
                  disabled={personas.length === 0}
                  onClick={() => setEditingExample({ persona: personas[0], example: null })}
                >
                  <PlusIcon className="size-3.5" />
                  Thêm ví dụ
                </Button>
              </CardAction>
            </CardHeader>
            <CardContent>
              <ExamplesTable
                examples={examples}
                loading={personasQuery.isLoading}
                mutationPending={mutationPending}
                onEdit={(example) => {
                  const persona = personas.find((entry) => entry.personaId === example.personaId);
                  if (persona) setEditingExample({ persona, example });
                }}
                onEnabledChange={setExampleEnabled}
                onMove={reorderExamples}
              />
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="actions" className="space-y-4">
          <Card>
            <CardHeader>
              <CardTitle>Actions</CardTitle>
              <CardDescription>Descriptor quản trị cho action mà rule compiler có thể đề xuất.</CardDescription>
            </CardHeader>
            <CardContent>
              <ActionTable
                actions={actions}
                loading={actionsQuery.isLoading}
                mutationPending={mutationPending}
                onEdit={setEditingAction}
                onEnabledChange={setActionEnabled}
                onMove={reorderActions}
              />
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>

      <PersonaDialog
        open={personaDialogOpen}
        persona={editingPersona}
        pending={savePersona.isPending}
        onOpenChange={setPersonaDialogOpen}
        onSave={async (request) => {
          await savePersona.mutateAsync({
            personaId: editingPersona?.personaId,
            request,
          });
          setPersonaDialogOpen(false);
        }}
      />

      <ExampleDialog
        state={editingExample}
        personas={personas}
        pending={saveExample.isPending}
        onOpenChange={(open) => {
          if (!open) setEditingExample(null);
        }}
        onSave={async (personaId, exampleId, request) => {
          await saveExample.mutateAsync({ personaId, exampleId, request });
          setEditingExample(null);
        }}
      />

      <ActionDialog
        action={editingAction}
        pending={saveActionDescriptor.isPending}
        onOpenChange={(open) => {
          if (!open) setEditingAction(null);
        }}
        onSave={async (actionKey, request) => {
          await saveActionDescriptor.mutateAsync({ actionKey, request });
          setEditingAction(null);
        }}
      />
    </div>
  );
}

function PersonaTable({
  personas,
  loading,
  mutationPending,
  onEdit,
  onEnabledChange,
  onMove,
}: {
  personas: RuleCatalogPersona[];
  loading: boolean;
  mutationPending: boolean;
  onEdit: (persona: RuleCatalogPersona) => void;
  onEnabledChange: (persona: RuleCatalogPersona, enabled: boolean) => void;
  onMove: (persona: RuleCatalogPersona, direction: -1 | 1) => void;
}) {
  if (loading) return <Skeleton className="h-40 w-full" />;
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Key</TableHead>
          <TableHead>Name EN</TableHead>
          <TableHead>Name VI</TableHead>
          <TableHead>Icon</TableHead>
          <TableHead className="text-right">Order</TableHead>
          <TableHead>Enabled</TableHead>
          <TableHead className="text-right">Actions</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {personas.length === 0 && (
          <EmptyRow colSpan={7} message="Chưa có persona nào trong rule catalog." />
        )}
        {personas.map((persona, index) => (
          <TableRow key={persona.personaId}>
            <TableCell className="font-mono text-xs">{persona.personaKey}</TableCell>
            <TableCell>{persona.displayNameEn}</TableCell>
            <TableCell>{persona.displayNameVi}</TableCell>
            <TableCell className="font-mono text-xs">{persona.icon ?? '-'}</TableCell>
            <TableCell className="text-right tabular-nums">{persona.displayOrder}</TableCell>
            <TableCell>
              <Switch
                size="sm"
                checked={persona.enabled}
                disabled={mutationPending}
                aria-label={`Enable persona ${persona.displayNameEn}`}
                onCheckedChange={(value) => onEnabledChange(persona, value === true)}
              />
            </TableCell>
            <TableCell>
              <RowActions
                editLabel={`Edit persona ${persona.displayNameEn}`}
                isFirst={index === 0}
                isLast={index === personas.length - 1}
                disabled={mutationPending}
                onEdit={() => onEdit(persona)}
                onMoveUp={() => onMove(persona, -1)}
                onMoveDown={() => onMove(persona, 1)}
              />
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

function ExamplesTable({
  examples,
  loading,
  mutationPending,
  onEdit,
  onEnabledChange,
  onMove,
}: {
  examples: ExampleRow[];
  loading: boolean;
  mutationPending: boolean;
  onEdit: (example: ExampleRow) => void;
  onEnabledChange: (example: ExampleRow, enabled: boolean) => void;
  onMove: (example: ExampleRow, direction: -1 | 1) => void;
}) {
  if (loading) return <Skeleton className="h-40 w-full" />;
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Persona</TableHead>
          <TableHead>Prompt EN</TableHead>
          <TableHead>Prompt VI</TableHead>
          <TableHead className="text-right">Order</TableHead>
          <TableHead>Enabled</TableHead>
          <TableHead className="text-right">Actions</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {examples.length === 0 && (
          <EmptyRow colSpan={6} message="Chưa có ví dụ nào trong rule catalog." />
        )}
        {examples.map((example, index) => (
          <TableRow key={example.exampleId}>
            <TableCell>
              <div className="font-medium">{example.personaNameEn}</div>
              <div className="text-muted-foreground text-xs">{example.personaNameVi}</div>
            </TableCell>
            <TableCell className="max-w-[280px] truncate">{example.exampleTextEn}</TableCell>
            <TableCell className="max-w-[280px] truncate">{example.exampleTextVi}</TableCell>
            <TableCell className="text-right tabular-nums">{example.displayOrder}</TableCell>
            <TableCell>
              <Switch
                size="sm"
                checked={example.enabled}
                disabled={mutationPending}
                aria-label={`Enable example ${example.sourceRef}`}
                onCheckedChange={(value) => onEnabledChange(example, value === true)}
              />
            </TableCell>
            <TableCell>
              <RowActions
                editLabel={`Edit example ${example.sourceRef}`}
                isFirst={isFirstInPersona(examples, example, index)}
                isLast={isLastInPersona(examples, example, index)}
                disabled={mutationPending}
                onEdit={() => onEdit(example)}
                onMoveUp={() => onMove(example, -1)}
                onMoveDown={() => onMove(example, 1)}
              />
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

function ActionTable({
  actions,
  loading,
  mutationPending,
  onEdit,
  onEnabledChange,
  onMove,
}: {
  actions: RuleCatalogActionDescriptor[];
  loading: boolean;
  mutationPending: boolean;
  onEdit: (action: RuleCatalogActionDescriptor) => void;
  onEnabledChange: (action: RuleCatalogActionDescriptor, enabled: boolean) => void;
  onMove: (action: RuleCatalogActionDescriptor, direction: -1 | 1) => void;
}) {
  if (loading) return <Skeleton className="h-40 w-full" />;
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Action key</TableHead>
          <TableHead>Label EN/VI</TableHead>
          <TableHead>Description EN/VI</TableHead>
          <TableHead>Risk</TableHead>
          <TableHead>Availability</TableHead>
          <TableHead className="text-right">Order</TableHead>
          <TableHead>Enabled</TableHead>
          <TableHead className="text-right">Actions</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {actions.length === 0 && (
          <EmptyRow colSpan={8} message="Chưa có action descriptor nào trong rule catalog." />
        )}
        {actions.map((action, index) => (
          <TableRow key={action.actionKey}>
            <TableCell className="font-mono text-xs">{action.actionKey}</TableCell>
            <TableCell>
              <div className="font-medium">{action.labelEn}</div>
              <div className="text-muted-foreground text-xs">{action.labelVi}</div>
            </TableCell>
            <TableCell className="max-w-[320px]">
              <div className="truncate">{action.descriptionEn}</div>
              <div className="text-muted-foreground truncate text-xs">{action.descriptionVi}</div>
            </TableCell>
            <TableCell>
              <RiskBadge risk={action.riskLevel} />
            </TableCell>
            <TableCell>
              <Badge variant={action.availabilityStatus === 'AVAILABLE' ? 'secondary' : 'outline'}>
                {action.availabilityStatus}
              </Badge>
            </TableCell>
            <TableCell className="text-right tabular-nums">{action.displayOrder}</TableCell>
            <TableCell>
              <Switch
                size="sm"
                checked={action.enabled}
                disabled={mutationPending}
                aria-label={`Enable action ${action.actionKey}`}
                onCheckedChange={(value) => onEnabledChange(action, value === true)}
              />
            </TableCell>
            <TableCell>
              <RowActions
                editLabel={`Edit action ${action.actionKey}`}
                isFirst={index === 0}
                isLast={index === actions.length - 1}
                disabled={mutationPending}
                onEdit={() => onEdit(action)}
                onMoveUp={() => onMove(action, -1)}
                onMoveDown={() => onMove(action, 1)}
              />
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

function RowActions({
  editLabel,
  isFirst,
  isLast,
  disabled,
  onEdit,
  onMoveUp,
  onMoveDown,
}: {
  editLabel: string;
  isFirst: boolean;
  isLast: boolean;
  disabled: boolean;
  onEdit: () => void;
  onMoveUp: () => void;
  onMoveDown: () => void;
}) {
  return (
    <div className="flex justify-end gap-1">
      <Button
        type="button"
        variant="ghost"
        size="icon-sm"
        aria-label="Move up"
        disabled={isFirst || disabled}
        onClick={onMoveUp}
      >
        <ChevronUpIcon className="size-4" />
      </Button>
      <Button
        type="button"
        variant="ghost"
        size="icon-sm"
        aria-label="Move down"
        disabled={isLast || disabled}
        onClick={onMoveDown}
      >
        <ChevronDownIcon className="size-4" />
      </Button>
      <Button type="button" variant="ghost" size="icon-sm" aria-label={editLabel} onClick={onEdit}>
        <PencilIcon className="size-4" />
      </Button>
    </div>
  );
}

function PersonaDialog({
  open,
  persona,
  pending,
  onOpenChange,
  onSave,
}: {
  open: boolean;
  persona: EditablePersona;
  pending: boolean;
  onOpenChange: (open: boolean) => void;
  onSave: (request: RuleCatalogPersonaWriteRequest) => Promise<void>;
}) {
  const formKey = `${open ? 'open' : 'closed'}:${persona?.personaId ?? 'new'}`;
  const [formState, setFormState] = useState(() => ({
    key: formKey,
    value: personaRequestFrom(persona),
  }));
  const form = formState.key === formKey ? formState.value : personaRequestFrom(persona);
  const updateForm = (nextForm: RuleCatalogPersonaWriteRequest) =>
    setFormState({ key: formKey, value: nextForm });

  async function submitForm(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    await onSave({
      ...form,
      personaKey: form.personaKey.trim(),
      displayNameEn: form.displayNameEn.trim(),
      displayNameVi: form.displayNameVi.trim(),
      icon: form.icon?.trim() || undefined,
      reason: ADMIN_REASON,
    });
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-xl">
        <DialogHeader>
          <DialogTitle>{persona ? 'Sửa persona' : 'Thêm persona'}</DialogTitle>
          <DialogDescription>Persona là nhóm ví dụ hiển thị dưới prompt box của user.</DialogDescription>
        </DialogHeader>

        <form className="space-y-4" onSubmit={submitForm}>
          <Field label="Persona key" htmlFor="persona-key">
            <Input
              id="persona-key"
              required
              value={form.personaKey}
              disabled={Boolean(persona)}
              onChange={(event) => updateForm({ ...form, personaKey: event.target.value })}
            />
          </Field>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Name EN" htmlFor="persona-name-en">
              <Input
                id="persona-name-en"
                required
                value={form.displayNameEn}
                onChange={(event) => updateForm({ ...form, displayNameEn: event.target.value })}
              />
            </Field>
            <Field label="Name VI" htmlFor="persona-name-vi">
              <Input
                id="persona-name-vi"
                required
                value={form.displayNameVi}
                onChange={(event) => updateForm({ ...form, displayNameVi: event.target.value })}
              />
            </Field>
          </div>
          <div className="grid gap-4 sm:grid-cols-3">
            <Field label="Icon" htmlFor="persona-icon">
              <Input
                id="persona-icon"
                value={form.icon ?? ''}
                onChange={(event) => updateForm({ ...form, icon: event.target.value })}
              />
            </Field>
            <Field label="Order" htmlFor="persona-order">
              <Input
                id="persona-order"
                required
                type="number"
                value={form.displayOrder}
                onChange={(event) =>
                  updateForm({ ...form, displayOrder: Number(event.target.value) })
                }
              />
            </Field>
            <div className="flex items-end gap-2 pb-2">
              <Switch
                id="persona-enabled"
                checked={form.enabled}
                onCheckedChange={(value) => updateForm({ ...form, enabled: value === true })}
              />
              <Label htmlFor="persona-enabled" className="font-normal">
                Enabled
              </Label>
            </div>
          </div>

          <DialogFooter>
            <Button type="button" variant="ghost" onClick={() => onOpenChange(false)}>
              Hủy
            </Button>
            <Button type="submit" disabled={pending}>
              <SaveIcon className="size-3.5" />
              Lưu
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

function ExampleDialog({
  state,
  personas,
  pending,
  onOpenChange,
  onSave,
}: {
  state: EditableExample | null;
  personas: RuleCatalogPersona[];
  pending: boolean;
  onOpenChange: (open: boolean) => void;
  onSave: (
    personaId: string,
    exampleId: string | undefined,
    request: RuleCatalogExampleWriteRequest,
  ) => Promise<void>;
}) {
  const formKey =
    state?.example?.exampleId ?? `new:${state?.persona.personaId ?? personas[0]?.personaId ?? 'none'}`;
  const initialPersonaId = state?.persona.personaId ?? personas[0]?.personaId ?? '';
  const [formState, setFormState] = useState(() => ({
    key: formKey,
    personaId: initialPersonaId,
    value: exampleRequestFrom(state),
  }));
  const form = formState.key === formKey ? formState.value : exampleRequestFrom(state);
  const personaId =
    formState.key === formKey ? formState.personaId : initialPersonaId;
  const updateForm = (nextForm: RuleCatalogExampleWriteRequest) =>
    setFormState({ key: formKey, personaId, value: nextForm });
  const updatePersonaId = (nextPersonaId: string) =>
    setFormState({ key: formKey, personaId: nextPersonaId, value: form });

  async function submitForm(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    await onSave(personaId, state?.example?.exampleId, {
      ...form,
      exampleTextEn: form.exampleTextEn.trim(),
      exampleTextVi: form.exampleTextVi.trim(),
      sourceRef: form.sourceRef.trim(),
      reason: ADMIN_REASON,
    });
  }

  return (
    <Dialog open={Boolean(state)} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>{state?.example ? 'Sửa ví dụ' : 'Thêm ví dụ'}</DialogTitle>
          <DialogDescription>Ví dụ bilingual được user chọn bên dưới prompt box.</DialogDescription>
        </DialogHeader>

        <form className="space-y-4" onSubmit={submitForm}>
          <Field label="Persona" htmlFor="example-persona">
            <select
              id="example-persona"
              className="border-input bg-background h-8 w-full rounded-lg border px-2 text-sm"
              value={personaId}
              disabled={Boolean(state?.example)}
              onChange={(event) => updatePersonaId(event.target.value)}
            >
              {personas.map((persona) => (
                <option key={persona.personaId} value={persona.personaId}>
                  {persona.displayNameEn} / {persona.displayNameVi}
                </option>
              ))}
            </select>
          </Field>
          <div className="grid gap-4 lg:grid-cols-2">
            <Field label="Prompt EN" htmlFor="example-text-en">
              <Textarea
                id="example-text-en"
                required
                rows={6}
                value={form.exampleTextEn}
                onChange={(event) => updateForm({ ...form, exampleTextEn: event.target.value })}
              />
            </Field>
            <Field label="Prompt VI" htmlFor="example-text-vi">
              <Textarea
                id="example-text-vi"
                required
                rows={6}
                value={form.exampleTextVi}
                onChange={(event) => updateForm({ ...form, exampleTextVi: event.target.value })}
              />
            </Field>
          </div>
          <div className="grid gap-4 sm:grid-cols-3">
            <Field label="Source ref" htmlFor="example-source-ref">
              <Input
                id="example-source-ref"
                required
                value={form.sourceRef}
                onChange={(event) => updateForm({ ...form, sourceRef: event.target.value })}
              />
            </Field>
            <Field label="Order" htmlFor="example-order">
              <Input
                id="example-order"
                required
                type="number"
                value={form.displayOrder}
                onChange={(event) =>
                  updateForm({ ...form, displayOrder: Number(event.target.value) })
                }
              />
            </Field>
            <div className="flex items-end gap-2 pb-2">
              <Switch
                id="example-enabled"
                checked={form.enabled}
                onCheckedChange={(value) => updateForm({ ...form, enabled: value === true })}
              />
              <Label htmlFor="example-enabled" className="font-normal">
                Enabled
              </Label>
            </div>
          </div>

          <DialogFooter>
            <Button type="button" variant="ghost" onClick={() => onOpenChange(false)}>
              Hủy
            </Button>
            <Button type="submit" disabled={pending || !personaId}>
              <SaveIcon className="size-3.5" />
              Lưu
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

function ActionDialog({
  action,
  pending,
  onOpenChange,
  onSave,
}: {
  action: RuleCatalogActionDescriptor | null;
  pending: boolean;
  onOpenChange: (open: boolean) => void;
  onSave: (actionKey: string, request: RuleCatalogActionDescriptorWriteRequest) => Promise<void>;
}) {
  const formKey = action?.actionKey ?? 'closed';
  const [formState, setFormState] = useState(() => ({
    key: formKey,
    value: actionRequestFrom(action),
  }));
  const form = formState.key === formKey ? formState.value : actionRequestFrom(action);
  const updateForm = (nextForm: RuleCatalogActionDescriptorWriteRequest) =>
    setFormState({ key: formKey, value: nextForm });

  if (!action) {
    return <Dialog open={false} onOpenChange={onOpenChange} />;
  }

  const actionKey = action.actionKey;

  async function submitForm(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    await onSave(actionKey, {
      ...form,
      labelEn: form.labelEn.trim(),
      labelVi: form.labelVi.trim(),
      descriptionEn: form.descriptionEn.trim(),
      descriptionVi: form.descriptionVi.trim(),
      riskLevel: form.riskLevel.trim().toUpperCase(),
      availabilityStatus: form.availabilityStatus.trim().toUpperCase(),
      reason: ADMIN_REASON,
    });
  }

  return (
    <Dialog open={Boolean(action)} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>Sửa action {actionKey}</DialogTitle>
          <DialogDescription>Label và mô tả hiển thị cho user rule builder.</DialogDescription>
        </DialogHeader>

        <form className="space-y-4" onSubmit={submitForm}>
          <div className="grid gap-4 lg:grid-cols-2">
            <Field label="Label EN" htmlFor="action-label-en">
              <Input
                id="action-label-en"
                required
                value={form.labelEn}
                onChange={(event) => updateForm({ ...form, labelEn: event.target.value })}
              />
            </Field>
            <Field label="Label VI" htmlFor="action-label-vi">
              <Input
                id="action-label-vi"
                required
                value={form.labelVi}
                onChange={(event) => updateForm({ ...form, labelVi: event.target.value })}
              />
            </Field>
          </div>
          <div className="grid gap-4 lg:grid-cols-2">
            <Field label="Description EN" htmlFor="action-description-en">
              <Textarea
                id="action-description-en"
                required
                rows={5}
                value={form.descriptionEn}
                onChange={(event) => updateForm({ ...form, descriptionEn: event.target.value })}
              />
            </Field>
            <Field label="Description VI" htmlFor="action-description-vi">
              <Textarea
                id="action-description-vi"
                required
                rows={5}
                value={form.descriptionVi}
                onChange={(event) => updateForm({ ...form, descriptionVi: event.target.value })}
              />
            </Field>
          </div>
          <div className="grid gap-4 sm:grid-cols-4">
            <SelectField
              label="Risk"
              id="action-risk"
              value={form.riskLevel}
              options={RISK_OPTIONS}
              onChange={(riskLevel) => updateForm({ ...form, riskLevel })}
            />
            <SelectField
              label="Availability"
              id="action-availability"
              value={form.availabilityStatus}
              options={AVAILABILITY_OPTIONS}
              onChange={(availabilityStatus) => updateForm({ ...form, availabilityStatus })}
            />
            <Field label="Order" htmlFor="action-order">
              <Input
                id="action-order"
                required
                type="number"
                value={form.displayOrder}
                onChange={(event) =>
                  updateForm({ ...form, displayOrder: Number(event.target.value) })
                }
              />
            </Field>
            <div className="flex items-end gap-2 pb-2">
              <Switch
                id="action-enabled"
                checked={form.enabled}
                onCheckedChange={(value) => updateForm({ ...form, enabled: value === true })}
              />
              <Label htmlFor="action-enabled" className="font-normal">
                Enabled
              </Label>
            </div>
          </div>

          <DialogFooter>
            <Button type="button" variant="ghost" onClick={() => onOpenChange(false)}>
              Hủy
            </Button>
            <Button type="submit" disabled={pending}>
              <SaveIcon className="size-3.5" />
              Lưu
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

function Field({
  label,
  htmlFor,
  children,
}: {
  label: string;
  htmlFor: string;
  children: ReactNode;
}) {
  return (
    <div className="space-y-2">
      <Label htmlFor={htmlFor}>{label}</Label>
      {children}
    </div>
  );
}

function SelectField({
  label,
  id,
  value,
  options,
  onChange,
}: {
  label: string;
  id: string;
  value: string;
  options: string[];
  onChange: (value: string) => void;
}) {
  const optionSet = new Set(options);
  const renderedOptions = optionSet.has(value) ? options : [value, ...options];
  return (
    <Field label={label} htmlFor={id}>
      <select
        id={id}
        className="border-input bg-background h-8 w-full rounded-lg border px-2 text-sm"
        value={value}
        onChange={(event) => onChange(event.target.value)}
      >
        {renderedOptions.map((option) => (
          <option key={option} value={option}>
            {option}
          </option>
        ))}
      </select>
    </Field>
  );
}

function EmptyRow({ colSpan, message }: { colSpan: number; message: string }) {
  return (
    <TableRow>
      <TableCell colSpan={colSpan} className="text-muted-foreground h-24 text-center">
        {message}
      </TableCell>
    </TableRow>
  );
}

function RiskBadge({ risk }: { risk: string }) {
  if (risk === 'HIGH') return <Badge className="bg-destructive/10 text-destructive">{risk}</Badge>;
  if (risk === 'MEDIUM') return <Badge className="bg-amber-soft text-amber">{risk}</Badge>;
  return <Badge variant="secondary">{risk}</Badge>;
}

function sortByOrder<T extends { displayOrder: number }>(rows: T[]): T[] {
  return [...rows].sort((left, right) => left.displayOrder - right.displayOrder);
}

function orderForIndex(index: number): number {
  return (index + 1) * 10;
}

function moveInList<T, K extends keyof T>(
  rows: T[],
  targetValue: T[K],
  direction: -1 | 1,
  key: K,
): T[] | null {
  const index = rows.findIndex((row) => row[key] === targetValue);
  const nextIndex = index + direction;
  if (index < 0 || nextIndex < 0 || nextIndex >= rows.length) return null;
  const reordered = rows.slice();
  [reordered[index], reordered[nextIndex]] = [reordered[nextIndex], reordered[index]];
  return reordered;
}

function isFirstInPersona(examples: ExampleRow[], example: ExampleRow, index: number): boolean {
  const previous = examples[index - 1];
  return !previous || previous.personaId !== example.personaId;
}

function isLastInPersona(examples: ExampleRow[], example: ExampleRow, index: number): boolean {
  const next = examples[index + 1];
  return !next || next.personaId !== example.personaId;
}

function personaRequestFrom(persona: EditablePersona): RuleCatalogPersonaWriteRequest {
  return {
    personaKey: persona?.personaKey ?? '',
    displayNameEn: persona?.displayNameEn ?? '',
    displayNameVi: persona?.displayNameVi ?? '',
    icon: persona?.icon ?? '',
    displayOrder: persona?.displayOrder ?? 10,
    enabled: persona?.enabled ?? true,
    reason: ADMIN_REASON,
  };
}

function exampleRequestFrom(state: EditableExample | null): RuleCatalogExampleWriteRequest {
  const personaKey = state?.persona.personaKey ?? 'persona';
  return {
    exampleTextEn: state?.example?.exampleTextEn ?? '',
    exampleTextVi: state?.example?.exampleTextVi ?? '',
    displayOrder: state?.example?.displayOrder ?? 10,
    enabled: state?.example?.enabled ?? true,
    sourceRef: state?.example?.sourceRef ?? `admin:${personaKey}:custom`,
    reason: ADMIN_REASON,
  };
}

function actionRequestFrom(
  action: RuleCatalogActionDescriptor | null,
): RuleCatalogActionDescriptorWriteRequest {
  return {
    labelEn: action?.labelEn ?? '',
    labelVi: action?.labelVi ?? '',
    descriptionEn: action?.descriptionEn ?? '',
    descriptionVi: action?.descriptionVi ?? '',
    riskLevel: action?.riskLevel ?? 'LOW',
    availabilityStatus: action?.availabilityStatus ?? 'AVAILABLE',
    displayOrder: action?.displayOrder ?? 10,
    enabled: action?.enabled ?? true,
    reason: ADMIN_REASON,
  };
}
