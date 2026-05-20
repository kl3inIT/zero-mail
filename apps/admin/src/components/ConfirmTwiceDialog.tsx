import { useForm } from '@tanstack/react-form';
import { TriangleAlertIcon } from 'lucide-react';
import { useMemo, useState } from 'react';
import { z } from 'zod';

import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';

type ConfirmTwiceDialogProps = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  actionLabel: string;
  targetLabel: string;
  consequences: string[];
  confirmationToken: string;
  finalButtonLabel: string;
  variant?: 'destructive' | 'warning';
  onConfirm: (reason: string) => Promise<{ auditId?: string | null }>;
};

const SENTINEL_PATTERN = /(sk-ant-|sk-or-|sk-|AIza)/;

const reasonSchema = z.object({
  reason: z
    .string()
    .min(8, 'Provide a reason of at least 8 characters.')
    .max(500, 'Reason must be 500 characters or fewer.')
    .refine((value) => !SENTINEL_PATTERN.test(value), 'Forbidden token prefix detected.'),
});

function fieldErrors(errors: unknown[]): string {
  return errors
    .map((error) => {
      if (typeof error === 'string') return error;
      if (error && typeof error === 'object' && 'message' in error) {
        return String((error as { message?: unknown }).message);
      }
      return String(error);
    })
    .join(', ');
}

export function ConfirmTwiceDialog({
  open,
  onOpenChange,
  actionLabel,
  targetLabel,
  consequences,
  confirmationToken,
  finalButtonLabel,
  variant = 'destructive',
  onConfirm,
}: ConfirmTwiceDialogProps) {
  const [step, setStep] = useState<1 | 2>(1);
  const [reasonValue, setReasonValue] = useState('');
  const [typedToken, setTypedToken] = useState('');
  const [submitError, setSubmitError] = useState<string | null>(null);
  // Either the real audit row id surfaced by the backend, or the empty string when
  // the backend returned 204 No Content (mutation succeeded but no id was surfaced).
  // We only use this to gate the second click — the message text never claims an id
  // unless one was actually returned. WR-10.
  const [successAuditId, setSuccessAuditId] = useState<string | null>(null);
  const form = useForm({
    defaultValues: {
      reason: '',
    },
    validators: {
      onChange: reasonSchema,
    },
  });
  const headerClassName = useMemo(
    () => (variant === 'destructive' ? 'bg-destructive-soft text-destructive' : 'bg-warning-soft text-amber'),
    [variant],
  );

  return (
    <Dialog
      open={open}
      onOpenChange={(nextOpen) => {
        onOpenChange(nextOpen);
        if (!nextOpen) {
          setStep(1);
          setReasonValue('');
          setTypedToken('');
          setSubmitError(null);
          setSuccessAuditId(null);
        }
      }}
    >
      <DialogContent className="max-w-lg gap-0 overflow-hidden p-0">
        <div className={`flex items-center gap-2 px-5 py-3 ${headerClassName}`}>
          <TriangleAlertIcon className="size-4" />
          <span className="text-sm font-semibold">
            {actionLabel} — {targetLabel}
          </span>
        </div>
        <div className="p-5">
          <DialogHeader>
            <DialogTitle>{step === 1 ? 'Record a reason' : 'Confirm the target'}</DialogTitle>
            <DialogDescription>
              {step === 1
                ? 'This reason is recorded in the admin audit log.'
                : `Type "${confirmationToken}" to confirm.`}
            </DialogDescription>
          </DialogHeader>

          {step === 1 ? (
            <form
              className="mt-5 space-y-4"
              onSubmit={(event) => {
                event.preventDefault();
                const validationResult = reasonSchema.safeParse({ reason: reasonValue });
                if (validationResult.success) {
                  setStep(2);
                }
              }}
            >
              <ul className="list-disc space-y-1 rounded-md border border-border bg-secondary p-4 pl-8 text-sm text-ink-2">
                {consequences.map((consequence) => (
                  <li key={consequence}>{consequence}</li>
                ))}
              </ul>
              <form.Field name="reason">
                {(field) => (
                  <div className="space-y-2">
                    <div className="flex items-center justify-between">
                      <Label htmlFor={field.name}>Reason (recorded in audit log)</Label>
                      <span className="font-mono text-xs text-muted-foreground">
                        {String(field.state.value).length} / 500
                      </span>
                    </div>
                    <Textarea
                      id={field.name}
                      value={field.state.value}
                      placeholder="e.g. decommissioning test admin"
                      onBlur={field.handleBlur}
                      onChange={(event) => {
                        setReasonValue(event.target.value);
                        field.handleChange(event.target.value);
                      }}
                    />
                    {field.state.meta.errors.length > 0 && (
                      <p className="text-sm text-destructive">{fieldErrors(field.state.meta.errors)}</p>
                    )}
                  </div>
                )}
              </form.Field>
              <div className="flex justify-end gap-2">
                <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
                  Cancel
                </Button>
                <Button type="submit" variant="secondary">
                  Continue
                </Button>
              </div>
            </form>
          ) : (
            <div className="mt-5 space-y-4">
              <div className="space-y-2">
                <Label htmlFor="confirmation-token">Type "{confirmationToken}" to confirm</Label>
                <Input
                  id="confirmation-token"
                  value={typedToken}
                  onChange={(event) => setTypedToken(event.target.value)}
                />
              </div>
              {submitError && <p className="text-sm text-destructive">{submitError}</p>}
              {successAuditId !== null && (
                <p className="text-sm text-green">
                  {successAuditId === ''
                    ? 'Action recorded.'
                    : `Action recorded. Audit row ${successAuditId}.`}
                </p>
              )}
              <div className="flex justify-end gap-2">
                <Button type="button" variant="outline" onClick={() => setStep(1)}>
                  Back
                </Button>
                <Button
                  type="button"
                  variant={variant === 'destructive' ? 'destructive' : 'secondary'}
                  disabled={typedToken !== confirmationToken || Boolean(successAuditId)}
                  onClick={() => {
                    setSubmitError(null);
                    void onConfirm(reasonValue)
                      .then((result) => setSuccessAuditId(result.auditId ?? ''))
                      .catch((error: unknown) => {
                        setSubmitError(error instanceof Error ? error.message : 'Unable to complete action.');
                      });
                  }}
                >
                  {finalButtonLabel}
                </Button>
              </div>
            </div>
          )}
        </div>
      </DialogContent>
    </Dialog>
  );
}
