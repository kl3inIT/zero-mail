#!/usr/bin/env bash
# ARCH-01 - Count Gmail send call sites partitioned by shared gateway vs
# non-gateway code. Output: two lines: "non_gateway=<N>" and "gateway=<N>".
# Always exits 0.
# The calling CI step parses and asserts the expected counts.
set -u

ALLOWED_GATEWAY_PATH='backend/core/src/main/java/com/zeromail/core/outbound/usecases/GmailOutboundSendGateway.java'

MATCHES=$(
  grep -rE 'messages\(\)\.send\(|drafts\(\)\.send\(' backend/ \
    --include='*.java' \
    --exclude-dir='test' \
    --exclude-dir='generated' 2>/dev/null || true
)

if [ -z "$MATCHES" ]; then
  NON_GATEWAY=0
  GATEWAY=0
else
  NON_GATEWAY=$(printf '%s\n' "$MATCHES" | grep -Fvc "$ALLOWED_GATEWAY_PATH" || true)
  GATEWAY=$(printf '%s\n' "$MATCHES" | grep -Fc "$ALLOWED_GATEWAY_PATH" || true)
fi

NON_GATEWAY=${NON_GATEWAY:-0}
GATEWAY=${GATEWAY:-0}

echo "non_gateway=${NON_GATEWAY}"
echo "gateway=${GATEWAY}"
exit 0
