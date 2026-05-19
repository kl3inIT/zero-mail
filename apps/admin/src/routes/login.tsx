import { useForm } from '@tanstack/react-form';
import { createFileRoute, useNavigate } from '@tanstack/react-router';
import { ShieldCheckIcon } from 'lucide-react';
import { useState } from 'react';
import { z } from 'zod';

import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { authenticatePasskey } from '@/lib/webauthn';

const loginSearchSchema = z.object({
  email: z.email().optional().catch(undefined),
});

export const Route = createFileRoute('/login')({
  validateSearch: loginSearchSchema,
  component: LoginRoute,
});

function LoginRoute() {
  const navigate = useNavigate();
  const search = Route.useSearch();
  const [errorText, setErrorText] = useState<string | null>(null);
  const form = useForm({
    defaultValues: {
      email: search.email ?? '',
    },
    validators: {
      onChange: z.object({ email: z.email('Enter a valid admin email.') }),
    },
    onSubmit: async ({ value }) => {
      setErrorText(null);
      await authenticatePasskey(value.email);
      await navigate({ to: '/' });
    },
  });

  return (
    <main className="grid min-h-screen place-items-center bg-background px-6">
      <Card className="w-full max-w-md text-center">
        <CardHeader>
          <div className="mx-auto mb-2 grid size-12 place-items-center rounded-lg bg-violet-soft text-primary">
            <ShieldCheckIcon className="size-6" />
          </div>
          <h1 className="text-base leading-snug font-medium">Admin sign-in</h1>
          <CardDescription>Authenticate with your passkey to enter the admin console.</CardDescription>
        </CardHeader>
        <CardContent>
          <form
            className="space-y-4 text-left"
            onSubmit={(event) => {
              event.preventDefault();
              void form.handleSubmit().catch((error: unknown) => {
                setErrorText(error instanceof Error ? error.message : 'Passkey ceremony failed.');
              });
            }}
          >
            <form.Field name="email">
              {(field) => (
                <div className="space-y-2">
                  <Label htmlFor={field.name}>Admin email</Label>
                  <Input
                    id={field.name}
                    value={field.state.value}
                    type="email"
                    onBlur={field.handleBlur}
                    onChange={(event) => field.handleChange(event.target.value)}
                  />
                </div>
              )}
            </form.Field>
            {errorText && <p className="text-sm text-destructive">{errorText}</p>}
            <Button type="submit" className="w-full">
              Sign in with passkey
            </Button>
          </form>
        </CardContent>
      </Card>
    </main>
  );
}
