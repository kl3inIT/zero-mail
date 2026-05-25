# Quick Task 260524-sej: Replace Zero Mail logo and browser tab icon

## Goal

Use `apps/web/public/images/logo.png` as the canonical Zero Mail logo everywhere the old inline `ZMLogoMark` rendered, and replace the browser tab icon.

## Tasks

1. Update the shared logo component.
   - Files: `apps/web/features/landing/components/ZMLogoMark.tsx`, related tests
   - Verify: all existing imports continue to render the shared logo.

2. Replace favicon.
   - Files: `apps/web/app/favicon.ico`
   - Verify: the browser loads the new favicon for `http://localhost:3000/`.

3. Run focused frontend verification.
   - Verify: `i18n:check`, `typecheck`, `lint`, targeted logo test, and rendered browser smoke pass.
