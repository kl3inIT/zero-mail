import { type NextRequest } from 'next/server';

import { redirectBack } from '../redirect-back';

const COOKIE_MAX_AGE = 60 * 60 * 24 * 365;

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
