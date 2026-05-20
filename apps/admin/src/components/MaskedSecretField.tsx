import { EyeOffIcon, KeyRoundIcon } from 'lucide-react';
import { useRef } from 'react';

import { Button } from './ui/button';
import { Input } from './ui/input';
import { Label } from './ui/label';

type MaskedSecretFieldProps = {
  maskedValue: string | null;
  editing: boolean;
  plaintextValue: string;
  onPlaintextChange: (value: string) => void;
  onEdit: () => void;
  editDisabled?: boolean;
};

export function MaskedSecretField({
  maskedValue,
  editing,
  plaintextValue,
  onPlaintextChange,
  onEdit,
  editDisabled,
}: MaskedSecretFieldProps) {
  const inputRef = useRef<HTMLInputElement>(null);

  return (
    <div className="space-y-2">
      <Label htmlFor="master-key-plaintext">{editing ? 'Plaintext key' : 'Saved key'}</Label>
      <div className="flex gap-2">
        <div className="relative flex-1">
          <EyeOffIcon className="absolute top-2.5 left-3 size-4 text-muted-foreground" />
          <Input
            ref={inputRef}
            id="master-key-plaintext"
            aria-label={editing ? 'Plaintext key' : 'Saved key'}
            type={editing ? 'password' : 'text'}
            readOnly={!editing}
            value={editing ? plaintextValue : (maskedValue ?? 'Not set')}
            onChange={(event) => onPlaintextChange(event.target.value)}
            className="pl-9 font-mono"
            autoComplete="off"
          />
        </div>
        <Button type="button" variant="secondary" onClick={onEdit} disabled={editDisabled}>
          <KeyRoundIcon className="size-4" />
          Edit key
        </Button>
      </div>
    </div>
  );
}
