// Plan 1.3-05 Task 5 will delete this entire [locale]/ tree after the
// Playwright route-smoke confirms localePrefix:'never' does not actually
// rely on a [locale] segment. Until then, point the re-export at the new
// (public)/page.tsx so tsc stays green.
// (public)/page.tsx lands in Task 3; until then re-export the login page so
// tsc stays green. Task 5 deletes this file regardless.
export { default } from '../(auth)/login/page';
