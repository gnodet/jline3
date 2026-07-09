# Loop Budget — JLine3 PR Review

> Primary loop: **PR Babysitter** (review-focused)

## Daily limits

| Loop | Max runs/day | Max tokens/day | Max sub-agent spawns/run |
|------|--------------|----------------|--------------------------|
| PR Review | 24 (every 1h) | 10M | 6 (3 reviewers + 3 verifiers) |

## On budget exceed

1. Switch to report-only mode (triage without posting reviews)
2. Append event to `loop-run-log.md`
3. Log warning in STATE.md High Priority section

## Kill switch

- Set `loop-pause-all` flag in STATE.md to halt all loop activity
- Resume only after human clears the flag
