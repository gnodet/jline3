# PR Review Loop State

## Last Run

- **Timestamp:** 2026-07-13T12:31:55Z
- **PRs checked:** 11
- **Reviews posted:** 1

## Reviewed PRs

<!-- Format: | PR# | Title | Author | Reviewed (ISO 8601) | Verdict | Notes | -->

| PR | Title | Author | Reviewed | Verdict | Notes |
|----|-------|--------|----------|---------|-------|
| #2053 | fix: guard styleMatches and highlighter rules against ReDoS | uchiha-bug-hunter | 2026-07-11T06:46:29Z | APPROVE | Re-reviewed; all 3 concerns addressed: catch block resets state, continueAs precompiled, multi-group test added. Approved on GitHub |
| #2045 | fix: confine ConfigurationPath lookups to the config directory | uchiha-bug-hunter | 2026-07-11T06:46:34Z | APPROVE | Re-reviewed; both concerns addressed: confine() uses toAbsolutePath().normalize(), two new tests added. Approved on GitHub |
| #2055 | fix: disable Read File command in nano restricted mode | uchiha-bug-hunter | 2026-07-10T10:23:18Z | APPROVE | Re-reviewed; author addressed ^R shortcut suggestion, both commits correct |
| #2007 | feat: add Kitty Keyboard Protocol support | gnodet | 2026-07-10T08:24:29Z | APPROVE | Re-reviewed after force-push; keyCode nit fixed (passes 0 for legacy events) |
| #2052 | fix: only read local jar: archives in PosixCommands getSources | uchiha-bug-hunter | 2026-07-11T17:03:53Z | APPROVE | Re-reviewed; Windows CI fix (jar URL caching disabled in tests) correct, isLocalJarFile() hardening sound, all CI green |
| #2020 | feat: change default of softwareSignals to false and deprecate | gnodet | 2026-07-09T22:53:15Z | APPROVE | LGTM; consistent default change, proper deprecation annotations, good test coverage |
| #2021 | feat: support in-band window resize notifications (mode 2048) | gnodet | 2026-07-09T22:53:20Z | APPROVE | LGTM; mirrors hasFocusSupport/trackFocus pattern, correct Size.of argument order |
| #2063 | fix: drain buffered data before signaling EOF in NonBlockingPumpInputStream | gnodet | 2026-07-10T08:24:22Z | APPROVE | Re-reviewed after CountDownLatch sync; Thread.onSpinWait replaced, all CI green |
| #2065 | feat: allow customizing the help source in Less pager (fixes #2056) | gnodet | 2026-07-10T00:42:27Z | APPROVE | LGTM; clean feature, follows defaultPrompt() pattern, backward-compatible |
| #2069 | fix: propagate EOF in PtyInputStream to avoid infinite loop (#1961, #1963) | thomasrebele | 2026-07-13T11:38:44Z | APPROVE | Clean backport of EOF fix from master to jline-3.x; 50ms timing heuristic correct, nanoTime improvement, good test coverage |
| #2071 | fix: look up openpty in libc.so.6 for glibc 2.34+ | gnodet | 2026-07-13T12:21:17Z | COMMENT | Own PR — correct glibc 2.34+ fix, proper fallback chain, Arena.global() appropriate, musl handled gracefully |
| #2072 | [backport jline-3.x] fix: guard styleMatches and highlighter rules against ReDoS | gnodet | 2026-07-13T12:31:55Z | COMMENT | Own PR — faithful backport of #2053, Java 8 adaptations correct, SafeRegex guards identical to master |

## Skipped PRs

<!-- PRs intentionally skipped (bot PRs, draft, etc.) -->

| PR | Reason | Since |
|----|--------|-------|

## Review Queue

<!-- PRs that need review but haven't been processed yet -->

| PR | Title | Author | Priority | Queued |
|----|-------|--------|----------|-------|
