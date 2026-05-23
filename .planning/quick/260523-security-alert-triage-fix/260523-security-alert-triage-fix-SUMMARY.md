---
status: complete
completed: 2026-05-23
task: security-alert-triage-fix
---

# Summary

## Alerts Triaged

- Dependabot open alerts: none.
- Secret scanning open alerts: none.
- CodeQL source alerts: true positives / actionable quality-security findings.
- Trivy image alerts: true positives from Ubuntu Noble runtime packages with fixed versions available.

## Fixes

- Sanitized logged Gmail thread identifiers in on-demand draft logs to prevent CR/LF log injection.
- Added even key/value count guards for internal varargs map builders.
- Removed unused model-verification actor parameter and routed feature-default reason into admin audit logging.
- Upgraded patched OS packages during API and worker runtime image builds via `apt-get upgrade`.

## Verification

- `./gradlew.bat --no-daemon :backend:api:compileJava :backend:core:test --tests com.zeromail.core.admin.cat.usecases.FeatureDefaultTierServiceValidationTest`
- `./gradlew.bat --no-daemon :backend:core:test --tests com.zeromail.core.chat.llm.VercelProtocolEmitterTest --tests com.zeromail.core.draft.GenerateThreadDraftServiceTest --tests com.zeromail.core.draft.DraftPrivacyLogScrubTest --tests com.zeromail.core.admin.cat.usecases.FeatureDefaultTierServiceValidationTest :backend:api:test --tests com.zeromail.api.ZeroMailApiApplicationModulesTest`
- Docker base runtime package upgrade check confirmed fixed versions for `libgnutls30t64`, `libpng16-16t64`, `libcap2`, `dpkg`, and `sed`.
