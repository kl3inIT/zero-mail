---
quick_id: 260509-til
status: planned
date: 2026-05-09
---

# Quick Task 260509-til: Fix Base UI RadioGroup controlled state warning on onboarding template select

## Goal

Remove the console warning where the onboarding template selector switches Base UI `RadioGroup` from uncontrolled to controlled after the first user selection.

## Context

- Error points at `apps/web/app/(protected)/onboarding/template-select/TemplateSelectClient.tsx`.
- Context7 Base UI docs confirm `RadioGroup` uses `defaultValue` for uncontrolled state and `value` for controlled state; a `value` prop must stay defined for the controlled lifetime.
- Current code passes `value={selected ?? undefined}` while `selected` starts as `null`, so the first render is uncontrolled and later selections make it controlled.

## Tasks

1. Keep `TemplateSelectClient`'s radio selection controlled from the first render by replacing the nullable selected state with an empty-string sentinel.
2. Preserve existing template validation, selected-card styling, and disabled submit behavior.
3. Verify with frontend type checking/tests and browser inspection where practical.

## Files Expected

- `apps/web/app/(protected)/onboarding/template-select/TemplateSelectClient.tsx`
