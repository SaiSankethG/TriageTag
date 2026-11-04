#!/usr/bin/env bash
# Sends a webhook to the local app the way GitHub does: signed with GITHUB_WEBHOOK_SECRET.
#
# Usage:  GITHUB_WEBHOOK_SECRET=... scripts/send-test-webhook.sh <payload.json> [delivery-id] [event]
# Example: scripts/send-test-webhook.sh src/test/resources/github/webhook-issues-opened.json test-1
#
# Prints the HTTP status, the response body and how long the app took to answer.
# The secret is only read from the environment and is never printed.
set -euo pipefail

payload_file="${1:?usage: send-test-webhook.sh <payload.json> [delivery-id] [event]}"
delivery_id="${2:-local-$(date +%s)}"
event="${3:-issues}"
target="${WEBHOOK_URL:-http://localhost:8080/webhooks/github}"

if [ -z "${GITHUB_WEBHOOK_SECRET:-}" ]; then
  echo "GITHUB_WEBHOOK_SECRET is not set" >&2
  exit 1
fi

# HMAC-SHA256 of the exact file bytes, as lowercase hex (same as GitHub's X-Hub-Signature-256).
signature="sha256=$(openssl dgst -sha256 -hmac "$GITHUB_WEBHOOK_SECRET" < "$payload_file" | sed 's/^.* //')"

curl -s -X POST "$target" \
  -H "Content-Type: application/json" \
  -H "X-GitHub-Event: $event" \
  -H "X-GitHub-Delivery: $delivery_id" \
  -H "X-Hub-Signature-256: $signature" \
  --data-binary "@$payload_file" \
  -w "\nHTTP %{http_code} in %{time_total}s\n"
