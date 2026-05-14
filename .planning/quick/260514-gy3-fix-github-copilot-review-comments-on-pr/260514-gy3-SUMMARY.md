---
quick_id: 260514-gy3
slug: fix-github-copilot-review-comments-on-pr
status: complete
commit: b764ab9
---

# Quick Task 260514-gy3 Summary

## Completed

- Changed analytics applied volume to count distinct `gmail_message_id` values, so the "messages triaged" metric no longer double-counts multi-action messages.
- Kept digest transient dispatch failures retryable by preserving `PENDING`, setting `next_attempt_at`, and letting the scheduler reclaim due retry rows.
- Clarified digest email copy from "yesterday" to "last 24 hours" / "24 giờ qua" so the wording matches the locked send-hour-anchored window.
- Added regression coverage for distinct applied-message counting and retry-after-transient digest dispatch.

## Verification

- `.\gradlew.bat :backend:core:test --tests "com.zeromail.core.analytics.AnalyticsSummaryQueryServiceTest" :backend:worker:test --tests "com.zeromail.worker.notification.DigestDispatchSchedulerTest" --tests "com.zeromail.worker.notification.DigestIdempotencyTest" --tests "com.zeromail.worker.notification.DigestPendingReaperJobTest" --tests "com.zeromail.worker.notification.DigestDispatchWithNoopChannelTest"` — passed.
- JetBrains file problem checks were run on touched Java files; native-SQL table/column resolution warnings remain IDE datasource false positives, while Gradle compile/tests passed.
