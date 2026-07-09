---
name: loop-guard
description: >
  Circuit breaker for fix-capable loops. Before each iteration, append the last
  attempt to loop-ledger.json and run loop-context --check; if it escalates,
  stop and hand the human a clean summary instead of looping in vain.
user_invocable: true
---

# Loop Guard (Circuit Breaker)

You keep a fix loop from burning tokens on a problem it cannot solve. You wrap
every iteration of an action skill (`minimal-fix`, `ci-triage`, `dependency-triage`, ...)
with a deterministic circuit-breaker check powered by
[`loop-context`](https://github.com/cobusgreyling/loop-engineering/tree/main/tools/loop-context).

The breaker needs no LLM call, so it is cheap enough to run on every iteration.

## The ledger

`loop-ledger.json` records the loop's goal, its pattern/level, and one entry per
attempt:

```json
{ "goal": "Get failing CI green", "pattern": "ci-sweeper", "level": "L2", "attempts": [] }
```

`pattern` and `level` are seeded by `loop-init`; the breaker ignores them but the
budget step below reads them to size `--token-budget` from real cost data.

After every iteration, append what you just tried:

```json
{ "iteration": 3, "action": "patch flaky auth test", "outcome": "failure",
  "error": "AssertionError: expected 200 got 500", "tokensUsed": 1800 }
```

`outcome` is `success | failure | noop`. Always include `error` on failures --
that is how the breaker detects a repeated (stagnant) failure.

## Before each iteration

1. Append the previous attempt to `loop-ledger.json`.
2. Run the breaker check.
3. Act on the result:
   - **continue** -> proceed with next iteration
   - **STOP** -> The breaker tripped -- same error N times in a row, too many
     consecutive failures, the token budget, or the iteration cap. Do not retry.

## On escalate

1. Write a clean, pruned summary for the human.
2. Write the escalation into STATE.md High Priority (or open an issue).
3. Exit the loop. A human decides the next step.

## Rules

- Never widen thresholds just to keep looping -- escalation is a feature, not a failure.
- Never edit the ledger to hide a repeated error; the breaker exists to catch it.
- Defaults: 3x same error, 5 consecutive failures, 10 iterations.

## Interaction with other skills

- `minimal-fix` / `ci-triage` -- record each attempt's outcome + error in the ledger.
- `loop-verifier` -- a verifier rejection is a `failure`; log it so repeats trip the breaker.
- `loop-constraints` -- honors "escalate after N attempts"; this skill makes it mechanical.
- `loop-budget` -- the per-run token budget here governs within-run spend;
  loop-budget.md still governs the *daily* cap across runs.
