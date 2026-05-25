import { createFileRoute } from '@tanstack/react-router';
import {
  ChevronDownIcon,
  ChevronUpIcon,
  PencilIcon,
  PlusIcon,
  SaveIcon,
} from 'lucide-react';
import { type FormEvent, type ReactNode, useMemo, useState } from 'react';

import { Button } from '@/components/ui/button';
import {
  Card,
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
import { Textarea } from '@/components/ui/textarea';
import type {
  RuleCatalogExample,
  RuleCatalogExampleWriteRequest,
  RuleCatalogPersona,
  RuleCatalogPersonaWriteRequest,
} from '@/features/rule-catalog/rule-catalog-api';
import { useReorderRuleCatalog } from '@/features/rule-catalog/use-reorder-rule-catalog';
import { useRuleCatalogPersonas } from '@/features/rule-catalog/use-rule-catalog';
import { useSetRuleCatalogEnabled } from '@/features/rule-catalog/use-save-action-descriptor';
import { useSaveExample } from '@/features/rule-catalog/use-save-example';
import { useSavePersona } from '@/features/rule-catalog/use-save-persona';

export const Route = createFileRoute('/_authenticated/rule-catalog')({
  component: RuleCatalogRoute,
});

const ADMIN_REASON = 'Admin rule catalog UI update';

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
  const savePersona = useSavePersona();
  const saveExample = useSaveExample();
  const setEnabled = useSetRuleCatalogEnabled();
  const reorderCatalog = useReorderRuleCatalog();

  const [selectedPersonaId, setSelectedPersonaId] = useState<string | null>(null);
  const [editingPersona, setEditingPersona] = useState<EditablePersona>(null);
  const [editingExample, setEditingExample] = useState<EditableExample | null>(null);
  const [personaDialogOpen, setPersonaDialogOpen] = useState(false);

  const personas = useMemo(
    () => sortByOrder(personasQuery.data?.personas ?? []),
    [personasQuery.data],
  );
  const selectedPersona =
    personas.find((persona) => persona.personaId === selectedPersonaId) ?? personas[0] ?? null;
  const selectedExamples = useMemo(
    () =>
      selectedPersona
        ? sortByOrder(selectedPersona.examples).map((example) => ({
            ...example,
            personaId: selectedPersona.personaId,
            personaKey: selectedPersona.personaKey,
            personaNameEn: selectedPersona.displayNameEn,
            personaNameVi: selectedPersona.displayNameVi,
          }))
        : [],
    [selectedPersona],
  );

  const mutationPending =
    savePersona.isPending || saveExample.isPending || setEnabled.isPending || reorderCatalog.isPending;

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

  return (
    <div className="space-y-6">
      <header className="flex items-end justify-between gap-4">
        <div>
          <h1 className="text-ink text-xl font-semibold">Ví dụ tạo quy tắc</h1>
          <p className="text-muted-foreground mt-1 max-w-2xl text-sm">
            Quản lý nhóm và ví dụ song ngữ hiển thị trong nút Chọn từ ví dụ.
          </p>
        </div>
        <Button type="button" onClick={openNewPersonaDialog}>
          <PlusIcon className="size-3.5" />
          Thêm nhóm
        </Button>
      </header>

      <div className="grid gap-4 xl:grid-cols-[390px_minmax(0,1fr)]">
        <Card>
          <CardHeader>
            <CardTitle>Nhóm người dùng</CardTitle>
            <CardDescription>Chọn một nhóm để quản lý ví dụ bên phải.</CardDescription>
          </CardHeader>
          <CardContent>
            <PersonaList
              personas={personas}
              selectedPersonaId={selectedPersona?.personaId ?? null}
              loading={personasQuery.isLoading}
              mutationPending={mutationPending}
              onSelect={setSelectedPersonaId}
              onEdit={openEditPersonaDialog}
              onEnabledChange={setPersonaEnabled}
              onMove={reorderPersonas}
            />
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
              <div>
                <CardTitle>
                  {selectedPersona
                    ? `Ví dụ của ${selectedPersona.displayNameVi}`
                    : 'Ví dụ'}
                </CardTitle>
                <CardDescription>
                  {selectedPersona
                    ? `Mẫu EN/VI hiển thị khi người dùng chọn nhóm ${selectedPersona.displayNameVi}.`
                    : 'Chọn một persona để quản lý ví dụ.'}
                </CardDescription>
              </div>
              <Button
                type="button"
                size="sm"
                variant="outline"
                disabled={!selectedPersona}
                onClick={() =>
                  selectedPersona &&
                  setEditingExample({ persona: selectedPersona, example: null })
                }
              >
                <PlusIcon className="size-3.5" />
                Thêm ví dụ
              </Button>
            </div>
          </CardHeader>
          <CardContent>
            <ExamplesTable
              examples={selectedExamples}
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
      </div>

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
    </div>
  );
}

function PersonaList({
  personas,
  selectedPersonaId,
  loading,
  mutationPending,
  onSelect,
  onEdit,
  onEnabledChange,
  onMove,
}: {
  personas: RuleCatalogPersona[];
  selectedPersonaId: string | null;
  loading: boolean;
  mutationPending: boolean;
  onSelect: (personaId: string) => void;
  onEdit: (persona: RuleCatalogPersona) => void;
  onEnabledChange: (persona: RuleCatalogPersona, enabled: boolean) => void;
  onMove: (persona: RuleCatalogPersona, direction: -1 | 1) => void;
}) {
  if (loading) return <Skeleton className="h-40 w-full" />;
  if (personas.length === 0) {
    return (
      <div className="text-muted-foreground rounded-lg border border-dashed p-6 text-center text-sm">
        Chưa có nhóm ví dụ nào.
      </div>
    );
  }
  return (
    <div className="space-y-2">
      {personas.map((persona, index) => {
        const selected = persona.personaId === selectedPersonaId;
        return (
          <div
            key={persona.personaId}
            className={`rounded-md border px-3 py-2 transition-colors ${
              selected ? 'border-primary bg-violet-soft/70' : 'bg-background hover:bg-secondary'
            }`}
          >
            <div className="flex items-center gap-2">
              <button
                type="button"
                className="min-w-0 flex-1 text-left"
                aria-label={`Chọn nhóm ${persona.displayNameEn}`}
                onClick={() => onSelect(persona.personaId)}
              >
                <div className="truncate text-sm font-semibold">{persona.displayNameEn}</div>
                <div className="text-muted-foreground truncate text-xs">{persona.displayNameVi}</div>
              </button>
              <Switch
                size="sm"
                checked={persona.enabled}
                disabled={mutationPending}
                aria-label={`Bật nhóm ${persona.displayNameEn}`}
                onCheckedChange={(value) => onEnabledChange(persona, value === true)}
              />
              <RowActions
                editLabel={`Sửa nhóm ${persona.displayNameEn}`}
                isFirst={index === 0}
                isLast={index === personas.length - 1}
                disabled={mutationPending}
                onEdit={() => onEdit(persona)}
                onMoveUp={() => onMove(persona, -1)}
                onMoveDown={() => onMove(persona, 1)}
              />
            </div>
          </div>
        );
      })}
    </div>
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
          <TableHead>Mẫu EN</TableHead>
          <TableHead>Mẫu VI</TableHead>
          <TableHead className="text-right">Thứ tự</TableHead>
          <TableHead>Bật</TableHead>
          <TableHead className="text-right">Thao tác</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {examples.length === 0 && (
          <EmptyRow colSpan={5} message="Chưa có ví dụ nào cho persona này." />
        )}
        {examples.map((example, index) => (
          <TableRow key={example.exampleId}>
            <TableCell className="max-w-[280px] truncate">{example.exampleTextEn}</TableCell>
            <TableCell className="max-w-[280px] truncate">{example.exampleTextVi}</TableCell>
            <TableCell className="text-right tabular-nums">{example.displayOrder}</TableCell>
            <TableCell>
              <Switch
                size="sm"
                checked={example.enabled}
                disabled={mutationPending}
                aria-label={`Bật ví dụ ${example.exampleTextEn}`}
                onCheckedChange={(value) => onEnabledChange(example, value === true)}
              />
            </TableCell>
            <TableCell>
              <RowActions
                editLabel={`Sửa ví dụ ${example.exampleTextEn}`}
                isFirst={index === 0}
                isLast={index === examples.length - 1}
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
        aria-label="Di chuyển lên"
        disabled={isFirst || disabled}
        onClick={onMoveUp}
      >
        <ChevronUpIcon className="size-4" />
      </Button>
      <Button
        type="button"
        variant="ghost"
        size="icon-sm"
        aria-label="Di chuyển xuống"
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
          <DialogTitle>{persona ? 'Sửa nhóm' : 'Thêm nhóm'}</DialogTitle>
          <DialogDescription>Nhóm quyết định các ví dụ hiển thị dưới prompt box.</DialogDescription>
        </DialogHeader>

        <form className="space-y-4" onSubmit={submitForm}>
          <Field label="Mã nhóm" htmlFor="persona-key">
            <Input
              id="persona-key"
              required
              value={form.personaKey}
              disabled={Boolean(persona)}
              onChange={(event) => updateForm({ ...form, personaKey: event.target.value })}
            />
          </Field>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Tên EN" htmlFor="persona-name-en">
              <Input
                id="persona-name-en"
                required
                value={form.displayNameEn}
                onChange={(event) => updateForm({ ...form, displayNameEn: event.target.value })}
              />
            </Field>
            <Field label="Tên VI" htmlFor="persona-name-vi">
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
            <Field label="Thứ tự" htmlFor="persona-order">
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
                Bật
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
      reason: ADMIN_REASON,
    });
  }

  return (
    <Dialog open={Boolean(state)} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>{state?.example ? 'Sửa ví dụ' : 'Thêm ví dụ'}</DialogTitle>
          <DialogDescription>Ví dụ song ngữ được user chọn bên dưới prompt box.</DialogDescription>
        </DialogHeader>

        <form className="space-y-4" onSubmit={submitForm}>
          <Field label="Nhóm" htmlFor="example-persona">
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
            <Field label="Mẫu EN" htmlFor="example-text-en">
              <Textarea
                id="example-text-en"
                required
                rows={6}
                value={form.exampleTextEn}
                onChange={(event) => updateForm({ ...form, exampleTextEn: event.target.value })}
              />
            </Field>
            <Field label="Mẫu VI" htmlFor="example-text-vi">
              <Textarea
                id="example-text-vi"
                required
                rows={6}
                value={form.exampleTextVi}
                onChange={(event) => updateForm({ ...form, exampleTextVi: event.target.value })}
              />
            </Field>
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Thứ tự" htmlFor="example-order">
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
                Bật
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

function EmptyRow({ colSpan, message }: { colSpan: number; message: string }) {
  return (
    <TableRow>
      <TableCell colSpan={colSpan} className="text-muted-foreground h-24 text-center">
        {message}
      </TableCell>
    </TableRow>
  );
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
  return {
    exampleTextEn: state?.example?.exampleTextEn ?? '',
    exampleTextVi: state?.example?.exampleTextVi ?? '',
    displayOrder: state?.example?.displayOrder ?? 10,
    enabled: state?.example?.enabled ?? true,
    reason: ADMIN_REASON,
  };
}
