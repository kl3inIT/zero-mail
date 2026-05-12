# Deferred Items

## 2026-05-11 - Plan 04-03 Verification

- `./gradlew.bat :backend:core:test --console=plain` still fails on pre-existing Wave 0
  future-contract presence tests for later Phase 04 services:
  `SenderSafetyNetServiceContractTest`, `TriageOrchestratorContractTest`,
  `TriageSafetyPolicyContractTest`, and `TriageUndoServiceContractTest`.
  This is outside Plan 04-03's semantic-intent gateway/model-pin scope and remains owned by the
  later Phase 04 implementation plans that create those services.
