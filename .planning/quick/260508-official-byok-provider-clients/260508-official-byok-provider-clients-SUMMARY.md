# Quick Task 260508: Official BYOK Provider Clients Summary

**Date:** 2026-05-08
**Status:** Completed

## Outcome

BYOK now routes official provider presets through provider-specific Spring AI clients:

- `openai`: `OpenAiByokModelClient`
- `anthropic`: `AnthropicByokModelClient`
- `google-genai`: `GoogleGenAiByokModelClient`
- `deepseek`: `DeepSeekByokModelClient`

OpenRouter remains a first-class preset but intentionally uses the OpenAI adapter with `https://openrouter.ai/api/v1`. Custom OpenAI-compatible and Anthropic-compatible presets remain available as separate UI choices for user-entered endpoints.

## Verification

- Backend compile for `backend/core`, `backend/api`, and `backend/worker`.
- Focused core LLM/BYOK tests.
- API BYOK controller integration test.
- Web i18n build/check, API client generation, typecheck, lint, focused Vitest tests, and BYOK Playwright e2e.

## Known Follow-Up

`backend:api:generateOpenApiDocs` still needs a clean rerun. The task hit an existing test runtime classpath issue first and then a Windows Gradle daemon native-memory exhaustion issue. The checked-in OpenAPI schema was updated and regenerated into the web client from that schema.
