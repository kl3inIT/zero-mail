'use client';

import { useState } from 'react';

import { Input } from '@/components/ui/input';
import { isEmailAddressList } from '@/features/chat/components/preview-card/preview-card-state';

/**
 * Editable recipient input shared by the send / reply / forward preview bodies. Controlled so the
 * invalid highlight reacts as the user types, and forwards every keystroke to the preview card's
 * contentOverride via {@code onChange} so recipient validation (which gates the Send button) sees
 * the edited value. Kept deliberately lenient — the backend remains the source of truth.
 */
export function RecipientField({
  defaultValue,
  onChange,
}: {
  defaultValue: string;
  onChange: (value: string) => void;
}) {
  const [value, setValue] = useState(defaultValue);
  return (
    <Input
      type="email"
      value={value}
      aria-invalid={!isEmailAddressList(value)}
      placeholder="name@example.com"
      onChange={(event) => {
        setValue(event.currentTarget.value);
        onChange(event.currentTarget.value);
      }}
    />
  );
}
