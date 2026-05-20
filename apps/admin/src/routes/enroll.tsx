import { useForm } from '@tanstack/react-form';
import { createFileRoute, useNavigate } from '@tanstack/react-router';
import { KeyRoundIcon } from 'lucide-react';
import { useState } from 'react';
import { z } from 'zod';

import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { createEnrollmentSession, registerPasskey } from '@/lib/webauthn';

const enrollSearchSchema = z.object({
  token: z.string().length(64),
  email: z.email().optional().catch(undefined),
});

export const Route = createFileRoute('/enroll')({
  validateSearch: enrollSearchSchema,
  component: EnrollRoute,
});

function EnrollRoute() {
  const navigate = useNavigate();
  const search = Route.useSearch();
  const [statusText, setStatusText] = useState<string | null>(null);
  const [errorText, setErrorText] = useState<string | null>(null);
  const form = useForm({
    defaultValues: {
      email: search.email ?? '',
    },
    validators: {
      onChange: z.object({ email: z.email('Nhập email quản trị viên hợp lệ.') }),
    },
    onSubmit: async ({ value }) => {
      setErrorText(null);
      setStatusText('Đang tạo phiên đăng ký.');
      await createEnrollmentSession(search.token, value.email);
      setStatusText('Đang mở cửa sổ tạo passkey.');
      await registerPasskey(value.email);
      await navigate({ to: '/login', search: { email: value.email } });
    },
  });

  return (
    <main className="grid min-h-screen place-items-center bg-background px-6">
      <Card className="w-full max-w-md">
        <CardHeader>
          <div className="mb-2 grid size-10 place-items-center rounded-md bg-violet-soft text-primary">
            <KeyRoundIcon className="size-5" />
          </div>
          <h1 className="text-base leading-snug font-medium">Đăng ký passkey</h1>
          <CardDescription>Hoàn tất quy trình đăng ký quản trị viên một lần.</CardDescription>
        </CardHeader>
        <CardContent>
          <form
            className="space-y-4"
            onSubmit={(event) => {
              event.preventDefault();
              void form.handleSubmit().catch((error: unknown) => {
                setStatusText(null);
                setErrorText(error instanceof Error ? error.message : 'Xác thực passkey thất bại.');
              });
            }}
          >
            <form.Field name="email">
              {(field) => (
                <div className="space-y-2">
                  <Label htmlFor={field.name}>Email quản trị viên</Label>
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
            {statusText && <p className="text-sm text-muted-foreground">{statusText}</p>}
            {errorText && <p className="text-sm text-destructive">{errorText}</p>}
            <Button type="submit" className="w-full">
              Đăng ký passkey
            </Button>
          </form>
        </CardContent>
      </Card>
    </main>
  );
}
