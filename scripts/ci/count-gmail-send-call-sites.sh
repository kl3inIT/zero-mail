#!/usr/bin/env bash
# ARCH-01 - Count Gmail send call sites partitioned by executor vs non-executor.
# Output: two lines: "non_executor=<N>" and "executor=<N>". Always exits 0.
# The calling CI step parses and asserts the expected counts.
set -u

MATCHES=$(
  grep -rE 'messages\(\)\.send\(|drafts\(\)\.send\(' backend/ \
    --include='*.java' \
    --exclude-dir='test' \
    --exclude-dir='generated' 2>/dev/null || true
)

if [ -z "$MATCHES" ]; then
  NON_EXECUTOR=0
  EXECUTOR=0
else
  NON_EXECUTOR=$(printf '%s\n' "$MATCHES" | grep -vc 'AssistantSendExecutor' || true)
  EXECUTOR=$(printf '%s\n' "$MATCHES" | grep -c 'AssistantSendExecutor' || true)
fi

NON_EXECUTOR=${NON_EXECUTOR:-0}
EXECUTOR=${EXECUTOR:-0}

echo "non_executor=${NON_EXECUTOR}"
echo "executor=${EXECUTOR}"
exit 0
