# Quick Task 260510-mid: Refactor backend domain package boundaries and sync architecture conventions

## Goal

Refactor backend package organization so core domain packages no longer use ambiguous `model` buckets, API controllers are grouped by domain, large rules DTO contracts are split, and project docs record the convention for future sessions.

## Tasks

1. Move core domain classes from `model` into responsibility packages:
   - `domain` for enums/value objects/business concepts.
   - `application` for service use-case contracts, commands, and operation results.
   - `projection` for read-side snapshots.
   - `exception` for domain/application exceptions.
2. Keep persistence concerns under `persistence`, including any `lowlevel` native SQL helper.
3. Group API controllers under `api/controllers/<domain>`.
4. Split `api/dto/rules/RuleDtos.java` into focused request/response record files.
5. Update imports, tests, package-info docs, OpenAPI-facing references, and architecture/convention docs.
6. Run backend build/tests or the closest available verification, plus IDE problem checks on changed Java files when useful.

## Notes

- This is a package/boundary refactor, not a behavior change.
- `Projection` means read-side snapshot; operation outcomes remain in `application` as `*Result`.
- `backend/api` keeps the existing horizontal layer layout (`controllers`, `dto`, `error`, `security`, `config`) but controller files are grouped by domain.
