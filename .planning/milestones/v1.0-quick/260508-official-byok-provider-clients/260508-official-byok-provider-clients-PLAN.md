# Quick Task 260508: Official BYOK Provider Clients

**Date:** 2026-05-08
**Status:** Completed

## Goal

Refactor BYOK so official provider presets use native Spring AI provider clients instead of being collapsed into OpenAI-compatible routing.

## Decisions

- Anthropic official must use the native Anthropic adapter.
- OpenAI official should have its own official OpenAI client path, separate from arbitrary OpenAI-compatible endpoints.
- Google GenAI and DeepSeek official presets should use Spring AI-supported provider adapters.
- OpenRouter remains OpenAI-compatible because Spring AI does not expose a dedicated OpenRouter provider adapter.
- Model IDs remain user-entered strings with suggestions, validated at runtime.

## Planned Work

1. Add runtime BYOK provider enum values and preset mappings for Google GenAI and DeepSeek.
2. Add Spring AI provider dependencies and dedicated BYOK model clients.
3. Route stored BYOK credentials to the right provider client.
4. Update validate/save/current DTO flow, OpenAPI schema, frontend preset UI, messages, and tests.
5. Run focused backend/frontend verification.

## Completion Notes

- Added native Spring AI BYOK clients for OpenAI, Anthropic, Google GenAI, and DeepSeek.
- Kept OpenRouter and custom OpenAI-compatible endpoints on the OpenAI adapter because Spring AI has no dedicated OpenRouter adapter.
- Runtime provider storage now uses `openai`, `anthropic`, `google-genai`, and `deepseek`; legacy `openai-compatible` rows deserialize to `openai` and migrate forward.
- Frontend provider presets now separate official providers from compatible endpoints and keep model IDs user-entered with suggestions plus backend validation.
