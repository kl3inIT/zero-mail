// Plan 1.3-05 Task 5 will delete this entire [locale]/ tree after the
// Playwright route-smoke confirms localePrefix:'never' does not actually
// rely on a [locale] segment. Until then, point the re-export at the new
// (auth)/login/page.tsx so tsc stays green.
export { default } from '../../(auth)/login/page';
