import { EyeOffIcon, KeyRoundIcon } from 'lucide-react';
import { useRef } from 'react';

import { Button } from './ui/button';
import { Input } from './ui/input';
import { Label } from './ui/label';

type MaskedSecretFieldProps = {
  maskedValue: string | null;
  editing: boolean;
  /**
   * Initial plaintext value when the input enters edit mode. The field is uncontrolled
   * during editing so the secret does not flow through React state. The current value is
   * surfaced only via {@link onPlaintextChange}; the parent is expected to hold the live
   * value in a ref, not in React state. CR-04.
   */
  defaultPlaintextValue?: string;
  onPlaintextChange: (value: string) => void;
  onEdit: () => void;
  editDisabled?: boolean;
};

export function MaskedSecretField({
  maskedValue,
  editing,
  defaultPlaintextValue = '',
  onPlaintextChange,
  onEdit,
  editDisabled,
}: MaskedSecretFieldProps) {
  const inputRef = useRef<HTMLInputElement>(null);
  // The `key` prop forces React to remount the input when toggling editing, so the
  // DOM-held value is dropped (not preserved across an edit/cancel/edit cycle).
  const editingKey = editing ? 'editing' : 'masked';

  return (
    <div className="space-y-2">
      <Label htmlFor="master-key-plaintext">{editing ? 'Plaintext key' : 'Saved key'}</Label>
      <div className="flex gap-2">
        <div className="relative flex-1">
          <EyeOffIcon className="absolute top-2.5 left-3 size-4 text-muted-foreground" />
          {editing ? (
            <Input
              key={editingKey}
              ref={inputRef}
              id="master-key-plaintext"
              aria-label="Plaintext key"
              type="password"
              defaultValue={defaultPlaintextValue}
              onChange={(event) => onPlaintextChange(event.target.value)}
              className="pl-9 font-mono"
              autoComplete="new-password"
              spellCheck={false}
              data-1p-ignore
            />
          ) : (
            <Input
              key={editingKey}
              id="master-key-plaintext"
              aria-label="Saved key"
              type="text"
              readOnly
              value={maskedValue ?? 'Not set'}
              className="pl-9 font-mono"
              autoComplete="off"
            />
          )}
        </div>
        <Button type="button" variant="secondary" onClick={onEdit} disabled={editDisabled}>
          <KeyRoundIcon className="size-4" />
          Edit key
        </Button>
      </div>
    </div>
  );
}
