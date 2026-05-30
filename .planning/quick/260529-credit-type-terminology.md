# Credit Type Terminology

## Goal

Rename the two user-facing credit buckets so they match production billing semantics:

- Monthly credits: credits included in the tenant's active plan and reset each month.
- Additional credits: credits outside the monthly plan allowance, including paid top-ups, promotions, admin grants, and service grants.

## Scope

- Backend billing balance DTO/projection naming.
- Backend balance grouping for additional grant categories.
- Frontend labels, typed schema, mocks, and tests.

## Non-goals

- Changing ledger spend allocation behavior.
- Adding new admin/promotion grant issuance flows.
