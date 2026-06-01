# Quick Task 260524-tc6 Summary

## Outcome

Unified platform and BYOK LLM provider execution around resolved provider credentials, then extended the admin LLM configuration flow so Spring AI native providers are built-in catalog rows and OpenAI/Anthropic-compatible gateways are DB-backed provider records created by admins.

## Completed

- Platform and BYOK non-streaming provider execution now share the Spring AI provider client factory/executor path.
- Chat streaming provider construction was moved onto the same resolved provider credential/client factory.
- Removed misleading provider-specific `*ByokModelClient` classes that were no longer BYOK-only.
- Added `docs/architecture/llm-chat-provider-routing-current.html` to explain the current chat/LLM/provider routing architecture.
- Converted admin master-key provider identity from a fixed Java enum boundary to a string-backed `LlmProvider` value object so custom provider IDs can flow through route params, persistence, and DTOs.
- Added provider metadata to `provider_catalog`:
  - `display_name`
  - `provider_kind`
  - `compatible_type`
  - `default_base_url`
- Added DB-backed compatible provider creation:
  - `POST /api/admin/master-keys/providers`
  - inserts a `provider_catalog` row and the first ACTIVE key in one transaction
  - accepts `OPENAI_FORMAT` or `ANTHROPIC_FORMAT`
  - rejects built-in provider IDs and existing provider IDs
- Added admin UI flow for `Thêm provider`:
  - admin enters provider ID, display name, compatibility type, base URL, API key, and key label
  - `Lưu` is disabled until `Test kết nối` succeeds
  - changing provider ID, compatibility type, base URL, or key clears the successful test state and locks save again
  - save reuses the tested edit-session token
- Backend save still re-runs the connection probe before inserting rows, so direct API callers cannot bypass the form test gate.
- Admin provider list groups Spring AI built-in providers separately from compatible gateways and renders gateway cards from backend data instead of hard-coded OpenRouter/9Router UI constants.
- Provider key add/detail pages now use provider metadata from API responses (`providerKind`, `compatibleType`, `defaultBaseUrl`).
- Fixed Spring AI M7 Google GenAI embedding auto-configuration startup failures in API/worker apps by excluding the embedding auto-config classes explicitly.

## Verification

- IntelliJ project build: passed.
- Focused core tests: `.\gradlew.bat :backend:core:test --tests "*MasterKey*" --tests "*Catalog*" --tests "*LlmRouter*"` passed.
- Focused API tests: `.\gradlew.bat :backend:api:test --tests com.zeromail.api.OpenApiSchemaTest --tests com.zeromail.api.security.CorsIntegrationTest` passed.
- Admin typecheck: `pnpm --filter @zeromail/admin typecheck` passed.
- Admin lint: `pnpm --filter @zeromail/admin lint` passed.
- Admin Playwright: `pnpm --filter @zeromail/admin e2e -- master-keys.spec.ts` passed.

## Notes

- The remaining OpenRouter/9Router mentions outside the admin provider-creation UI are legacy tests, BYOK presets, explicit OpenRouter probe tests, or existing catalog seed compatibility. They are not used as hard-coded admin provider cards.
- Generated OpenAPI TypeScript was updated manually in `apps/admin/src/lib/api/admin-schema.d.ts` for this task; a later generated-client refresh can replace that manual delta.
