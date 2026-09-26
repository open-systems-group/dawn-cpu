<!--
Read CONTRIBUTING.md before opening a PR.
PR title format: `area: imperative summary`, e.g. `rob: stall retire while a store is pending`.
The title and this description become the squash-merge commit, so keep them accurate.
Open as a draft until you've done the verification below.
-->

Fixes #

## What and why

<!-- What does this change, and why is it needed? For design decisions, link the issue or Discord discussion. -->

## RTL impact

<!-- Tick one. -->

- [ ] None (docs, scripts, simulation harness, programs)
- [ ] Refactor: RTL changes but behavior is identical
- [ ] Functional RTL change
- [ ] FPGA scaffold / Vivado project change (IP configuration changed: yes / no)

## Verification

<!--
See "Verifying your change" in CONTRIBUTING.md. Say exactly what you ran.
If you didn't run something, say so; that's fine.
-->

- [ ] `./scripts/generate-verilog.sh` succeeds
- [ ] `./scripts/simulate.sh ./programs/mandelbrot.c <cycles>` gives the correct frame
- [ ] `./scripts/simulate.sh ./programs/pong.c <cycles>` gives the correct frame
- [ ] Tested on FPGA, board: <!-- Saturn5P / Urbana / … -->

Cycle limit used:

<!-- Attach generated/frame.png. For performance-affecting changes, show main vs. this branch at the same cycle limit. -->

## Checklist

- [ ] One topic; unrelated changes and refactors are in separate PRs
- [ ] Formatted with scalafmt, and no reformatting of untouched code
- [ ] No `generated/` files and no unrelated Vivado `.gen/` / `.ip_user_files/` churn
- [ ] Debug `printf`s removed
- [ ] README / docs updated if behavior or usage changed
- [ ] AI tools were used for this PR (if ticked, say how below)

<!-- Anything reviewers should look at closely, known limitations, or follow-up work: -->
