'use server';

import { cookies } from 'next/headers';

export async function setTheme(formData: FormData) {
  const raw = formData.get('theme');
  const theme: 'light' | 'dark' = raw === 'dark' ? 'dark' : 'light';
  const cookieStore = await cookies();
  cookieStore.set('zm-theme', theme, {
    sameSite: 'lax',
    secure: process.env.NODE_ENV === 'production',
    httpOnly: false,
    maxAge: 60 * 60 * 24 * 365,
    path: '/',
  });
}
