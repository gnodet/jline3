# PR Review Loop State

## Last Run

- **Timestamp:** 2026-07-15T15:33:00Z
- **PRs checked:** 6
- **Reviews posted:** 1
- **Note:** Re-reviewed #2090 after new commit (2026-07-15T12:42:23Z); posted COMMENT review — LGTM, closed-flag check correctly placed after in.read()

## Reviewed PRs

<!-- Format: | PR# | Title | Author | Reviewed (ISO 8601) | Verdict | Notes | -->

| PR | Title | Author | Reviewed | Verdict | Notes |
|----|-------|--------|----------|---------|-------|
| #2053 | fix: guard styleMatches and highlighter rules against ReDoS | uchiha-bug-hunter | 2026-07-11T06:46:29Z | APPROVE | Re-reviewed; all 3 concerns addressed: catch block resets state, continueAs precompiled, multi-group test added. Approved on GitHub |
| #2045 | fix: confine ConfigurationPath lookups to the config directory | uchiha-bug-hunter | 2026-07-14T22:07:40Z | APPROVE | Author applied toRealPath() test fix (6c50d99); CI passing on macOS and Ubuntu, approved on GitHub |
| #2055 | fix: disable Read File command in nano restricted mode | uchiha-bug-hunter | 2026-07-10T10:23:18Z | APPROVE | Re-reviewed; author addressed ^R shortcut suggestion, both commits correct |
| #2007 | feat: add Kitty Keyboard Protocol support | gnodet | 2026-07-10T08:24:29Z | APPROVE | Re-reviewed after force-push; keyCode nit fixed (passes 0 for legacy events) |
| #2052 | fix: only read local jar: archives in PosixCommands getSources | uchiha-bug-hunter | 2026-07-11T17:03:53Z | APPROVE | Re-reviewed; Windows CI fix (jar URL caching disabled in tests) correct, isLocalJarFile() hardening sound, all CI green |
| #2020 | feat: change default of softwareSignals to false and deprecate | gnodet | 2026-07-09T22:53:15Z | APPROVE | LGTM; consistent default change, proper deprecation annotations, good test coverage |
| #2021 | feat: support in-band window resize notifications (mode 2048) | gnodet | 2026-07-15T09:01:00Z | COMMENT | Re-reviewed after 478d742: drain-to-'t' fix, API contract alignment, @since tags, 5 new widget tests. LGTM (own PR) |
| #2063 | fix: drain buffered data before signaling EOF in NonBlockingPumpInputStream | gnodet | 2026-07-10T08:24:22Z | APPROVE | Re-reviewed after CountDownLatch sync; Thread.onSpinWait replaced, all CI green |
| #2065 | feat: allow customizing the help source in Less pager (fixes #2056) | gnodet | 2026-07-10T00:42:27Z | APPROVE | LGTM; clean feature, follows defaultPrompt() pattern, backward-compatible |
| #2069 | fix: propagate EOF in PtyInputStream to avoid infinite loop (#1961, #1963) | thomasrebele | 2026-07-13T11:38:44Z | APPROVE | Clean backport of EOF fix from master to jline-3.x; 50ms timing heuristic correct, nanoTime improvement, good test coverage |
| #2071 | fix: look up openpty in libc.so.6 for glibc 2.34+ | gnodet | 2026-07-13T12:21:17Z | COMMENT | Own PR — correct glibc 2.34+ fix, proper fallback chain, Arena.global() appropriate, musl handled gracefully |
| #2072 | [backport jline-3.x] fix: guard styleMatches and highlighter rules against ReDoS | gnodet | 2026-07-13T12:31:55Z | COMMENT | Own PR — faithful backport of #2053, Java 8 adaptations correct, SafeRegex guards identical to master |
| #2073 | fix: look up openpty in libc.so.6 for glibc 2.34+ (backport) | gnodet | 2026-07-13T12:52:20Z | COMMENT | Own PR — faithful backport of #2071 to jline-3.x, identical 13-line insertion, correctly positioned without AIX block |
| #2074 | fix: backport #2054 — drain buffered data before EOF in NonBlockingPumpInputStream | gnodet | 2026-07-13T15:09:46Z | COMMENT | Own PR — re-reviewed after SonarCloud fix; assertion-less test now properly verifies WARN-mode EOF behavior |
| #2075 | fix: disable Read File command in nano restricted mode (backport #2055) | gnodet | 2026-07-13T15:26:50Z | COMMENT | Own PR — faithful backport of #2055, identical Nano.java changes, test adapted for Java 8 and 3.x conventions |
| #2076 | fix: confine ConfigurationPath lookups to the config directory (backport #2045) | gnodet | 2026-07-14T21:16:03Z | COMMENT | Author applied toRealPath() test fix (7ee3d99); CI all green across macOS/Ubuntu/Windows, confine() implementation correct |
| #2078 | refactor: use try-with-resources in ExecHelper#waitAndCapture | gnodet | 2026-07-13T19:16:08Z | COMMENT | Own PR — clean try-with-resources conversion, correct semantics, minor close-order change inconsequential |
| #2079 | refactor: use try-with-resources in ExecHelper#waitAndCapture | gnodet | 2026-07-13T19:40:42Z | COMMENT | Own PR — re-submission of closed #2078, identical diff from new branch, same try-with-resources refactoring |
| #2080 | refactor: use try-with-resources in ExecHelper#waitAndCapture | gnodet | 2026-07-13T19:47:46Z | COMMENT | Own PR — third attempt at same refactoring (#2078/#2079 closed), identical diff, CI failures transient |
| #2081 | cleanup: modernize ExecHelper to use try-with-resources | gnodet | 2026-07-13T19:58:15Z | COMMENT | Own PR — fourth iteration of ExecHelper try-with-resources refactoring, new branch, also removes unused close() helper and Closeable import |
| #2082 | fix: use try-with-resources in ExecHelper | gnodet | 2026-07-13T20:02:16Z | COMMENT | Own PR — fifth iteration, identical diff to #2081, new branch fix/exec-helper-try-with-resources, fix: prefix |
| #2083 | improve: use try-with-resources for process streams in ExecHelper | gnodet | 2026-07-13T20:25:09Z | COMMENT | Own PR — sixth iteration, identical diff (hash 77c65b4c1), improve: prefix, branch improve/exec-helper-resource-handling |
| #2086 | refactor: modernize ExecHelper stream handling | gnodet | 2026-07-13T20:41:30Z | COMMENT | Own PR — seventh iteration, identical diff (hash 77c65b4c1), refactor: prefix, branch refactor/exechelper-modernize |
| #2087 | cleanup: safer process stream handling in ExecHelper | gnodet | 2026-07-13T20:49:39Z | COMMENT | Own PR — eighth iteration, identical diff (hash 77c65b4c1), cleanup: prefix, branch cleanup/process-stream-safety |
| #2088 | enhancement: adopt try-with-resources in ExecHelper | gnodet | 2026-07-13T21:15:46Z | COMMENT | Own PR — ninth iteration of ExecHelper try-with-resources refactoring, clean conversion, behavior-preserving |
| #2089 | test: try-with-resources in ExecHelper | gnodet | 2026-07-13T22:13:05Z | COMMENT | Own PR — tenth iteration of ExecHelper try-with-resources; CI broken: `-Werror` fails on unreferenced `out` in try-with-resources. Suggested removing `out` from resource list and closing explicitly |
| #2084 | chore: bump junit.version from 6.1.1 to 6.1.2 | dependabot | 2026-07-13T21:40:23Z | COMMENT | Clean Dependabot bump, test-scoped dep, all CI green, patch release (NoTestsDiscoveredException fix) |
| #2085 | chore: bump junit.version from 6.1.1 to 6.1.2 | dependabot | 2026-07-13T21:40:30Z | COMMENT | Clean Dependabot bump to 4.0.x, same version change as #2084, all CI green |
| #2094 | chore: remove 4.0.x from Dependabot configuration | gnodet | 2026-07-15T08:07:04Z | COMMENT | Own PR — clean removal of 4.0.x dependabot entries (maven + github-actions); bumps land on master and get cherry-picked |
| #2095 | fix: backport SSRF fix to 3.x — use Path.resolve in cat and sort | gnodet | 2026-07-15T11:49:00Z | COMMENT | Own PR — clean security backport of #2052 to jline-3.x; 2-line fix, 4 SSRF regression tests. LGTM |
| #2090 | fix: check closed flag in PtyInputStream to prevent hang on empty input | gnodet | 2026-07-15T15:33:42Z | COMMENT | Own PR — re-reviewed after 3rd commit: closed-flag check moved after in.read() to drain buffered data. LGTM, all CI green |

## Skipped PRs

<!-- PRs intentionally skipped (bot PRs, draft, etc.) -->

| PR | Reason | Since |
|----|--------|-------|
| #2091 | Bot PR (dependabot) — actions/setup-node v6→v7, dependencies label | 2026-07-14T20:25:00Z |
| #2092 | Bot PR (dependabot) — actions/setup-node v6→v7, dependencies label | 2026-07-14T20:25:00Z |
| #2093 | Bot PR (dependabot) — actions/setup-node v6→v7, dependencies label | 2026-07-14T20:25:00Z |

## Review Queue

<!-- PRs that need review but haven't been processed yet -->

| PR | Title | Author | Priority | Queued |
|----|-------|--------|----------|-------|
