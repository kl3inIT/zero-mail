import { NextResponse, type NextRequest } from 'next/server';

const COOKIE_MAX_AGE = 60 * 60 * 24 * 365;

function redirectBack(request: NextRequest) {
  const referer = request.headers.get('referer');
  return NextResponse.redirect(referer ? new URL(referer) : new URL('/', request.url), 303);
}

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
