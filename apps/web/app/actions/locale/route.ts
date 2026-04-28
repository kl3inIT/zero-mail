import { NextResponse, type NextRequest } from 'next/server';

const COOKIE_MAX_AGE = 60 * 60 * 24 * 365;

function redirectBack(request: NextRequest) {
  const referer = request.headers.get('referer');
  return NextResponse.redirect(referer ? new URL(referer) : new URL('/', request.url), 303);
}

export async function POST(request: NextRequest) {
  const formData = await request.formData();
  const locale = formData.get('locale');
  const response = redirectBack(request);

  if (locale === 'en' || locale === 'vi') {
    response.cookies.set('NEXT_LOCALE', locale, {
      path: '/',
      maxAge: COOKIE_MAX_AGE,
      sameSite: 'lax',
      secure: true,
    });
  }

  return response;
}
