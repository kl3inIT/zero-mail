import { type NextRequest } from 'next/server';

import { redirectBack } from '../redirect-back';

const COOKIE_MAX_AGE = 60 * 60 * 24 * 365;

export async function POST(request: NextRequest) {
  const formData = await request.formData();
  const raw = formData.get('theme');
  const theme = raw === 'dark' ? 'dark' : 'light';
  const response = redirectBack(request);

  response.cookies.set('zm-theme', theme, {
    sameSite: 'lax',
    secure: process.env.NODE_ENV === 'production',
    httpOnly: false,
    maxAge: COOKIE_MAX_AGE,
    path: '/',
  });

  return response;
}
