# Loop Run Log — JLine3

Append one entry per run. Prune entries older than 30 days.

## Format

```json
{
  "run_id": "2026-07-09T08:15:00Z",
  "pattern": "pr-babysitter",
  "duration_s": 45,
  "items_found": 4,
  "actions_taken": 1,
  "escalations": 0,
  "tokens_estimate": 52000,
  "outcome": "report-only | fix-proposed | escalated | no-op"
}
```

## Recent Runs

<!-- Loop appends below this line -->

```json
{
  "run_id": "2026-07-09T22:11:00Z",
  "pattern": "pr-babysitter",
  "duration_s": 600,
  "items_found": 7,
  "actions_taken": 3,
  "escalations": 0,
  "tokens_estimate": 320000,
  "outcome": "report-only"
}
```

```json
{
  "run_id": "2026-07-15T14:10:00Z",
  "pattern": "pr-babysitter",
  "duration_s": 30,
  "items_found": 6,
  "actions_taken": 0,
  "escalations": 0,
  "tokens_estimate": 15000,
  "outcome": "no-op"
}
```
