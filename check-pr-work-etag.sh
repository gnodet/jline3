#!/usr/bin/env bash
# check-pr-work-etag.sh — two-tier precondition for the PR review loop.
#
# Tier 1: Conditional request to the GitHub Events API using ETags.
#         A 304 response means no repo activity at all — exits immediately
#         and does NOT count against the API rate limit.
# Tier 2: Falls through to check-pr-work.sh for PR-level filtering
#         (comparing updatedAt against STATE.md timestamps).
#
# Costs: 0 API calls when nothing changed (ETag 304), 2 when activity detected
#         (1 for Events, 1 for PR list in check-pr-work.sh).
#
# Usage: ./check-pr-work-etag.sh [path/to/STATE.md]
#
# Dependencies: curl, gh CLI

set -euo pipefail

REPO="jline/jline3"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ETAG_FILE="${SCRIPT_DIR}/.pr-loop-etag"
HEADER_TMP=$(mktemp)
BODY_TMP=$(mktemp)
trap 'rm -f "$HEADER_TMP" "$BODY_TMP"' EXIT

# Read cached ETag from previous run (if any)
CACHED_ETAG=""
if [ -f "$ETAG_FILE" ]; then
  CACHED_ETAG=$(cat "$ETAG_FILE")
fi

# Build the conditional request
ETAG_HEADER=()
if [ -n "$CACHED_ETAG" ]; then
  ETAG_HEADER=(-H "If-None-Match: $CACHED_ETAG")
fi

# Tier 1: Conditional Events API request
HTTP_CODE=$(curl -s \
  -o "$BODY_TMP" \
  -D "$HEADER_TMP" \
  -w "%{http_code}" \
  -H "Authorization: token $(gh auth token)" \
  -H "Accept: application/vnd.github+json" \
  "${ETAG_HEADER[@]}" \
  "https://api.github.com/repos/${REPO}/events?per_page=5" 2>/dev/null) || true

# Save the new ETag regardless of outcome (even on 304, the ETag is returned)
NEW_ETAG=$(grep -i '^etag:' "$HEADER_TMP" 2>/dev/null | awk '{print $2}' | tr -d '\r\n' || true)
if [ -n "$NEW_ETAG" ]; then
  echo -n "$NEW_ETAG" > "$ETAG_FILE"
fi

case "$HTTP_CODE" in
  304)
    echo "No repo activity since last check (ETag 304, free). Skipping."
    exit 1
    ;;
  200)
    echo "Repo activity detected (ETag changed). Checking PRs..."
    # Tier 2: Run the detailed PR check
    exec "${SCRIPT_DIR}/check-pr-work.sh" "$@"
    ;;
  *)
    # On any error (auth, network, etc.), fall through to the real check
    # so ETag failures never block reviews
    echo "Events API returned ${HTTP_CODE:-error}. Falling through to PR check."
    exec "${SCRIPT_DIR}/check-pr-work.sh" "$@"
    ;;
esac
