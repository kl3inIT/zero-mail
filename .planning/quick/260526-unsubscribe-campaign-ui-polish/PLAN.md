---
created: 2026-05-26
status: complete
---

# Unsubscribe Campaign UI Polish

Task: improve the unsubscribe campaign list UI so it matches the newer Analytics visual direction and is easier to scan.

Scope:
- Polish `CandidateListPage`, `SelectionToolbar`, and `CandidateListTable`.
- Add a compact top-right help icon with a popover explaining how safe unsubscribe works.
- Keep existing API behavior, selection behavior, preview dialog, safe-list dialog, and route flow unchanged.
- Update i18n through feature messages and regenerate merged locale JSON.

Verification:
- Run i18n check, lint, typecheck, and unsubscribe campaign e2e coverage.
