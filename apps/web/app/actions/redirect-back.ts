import { NextResponse, type NextRequest } from 'next/server';

export function redirectBack(request: NextRequest): NextResponse {
  const referer = request.headers.get('referer');
  const fallback = new URL('/', request.url);

  if (!referer) {
    return NextResponse.redirect(fallback, 303);
  }

  try {
    const target = new URL(referer);
    if (target.origin !== request.nextUrl.origin) {
      return NextResponse.redirect(fallback, 303);
    }
    return NextResponse.redirect(target, 303);
  } catch {
    return NextResponse.redirect(fallback, 303);
  }
}
