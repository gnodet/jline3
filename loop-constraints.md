# Loop Constraints — JLine3 PR Review

> Constraints here are **binding** -- the agent MUST follow them.

## Push & Merge
- Never merge, close, or label any PR
- Never push code -- this loop is review-only
- Never create PRs -- this loop only reviews existing PRs

## Scope
- Never review draft PRs
- Never review bot PRs (dependabot, renovate, github-actions)
- Max 3 PRs per loop iteration
- Max 2000 lines changed per PR (skip larger, flag for manual)

## Review Quality
- Always run verifier sub-agent before posting findings
- Never post findings that the verifier marked as false positives
- Always check git history (git log, git blame) before flagging issues
- Always review against `.oss-ai-helper-rules/` project rules
- Never auto-approve PRs > 100 lines -- record verdict only

## Paths (never flag as issues unless genuine security concern)
- Generated code in `src/generated/` -- ignore formatting/style findings

## Communication
- All posted reviews must include AI attribution disclaimer
- All posted reviews must include `_Claude Code on behalf of Guillaume Nodet_`
- Never close issues or PRs

## Budget
- If token spend hits 80% of daily cap, switch to report-only
- If `loop-pause-all` is active in STATE.md, exit immediately
- Max 3 attempts per PR; escalate after (tracked in loop-ledger.json)

---
<!-- Add your own rules below. Use plain English. The loop reads this verbatim. -->
