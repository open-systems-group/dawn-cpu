# Contributing to Dawn

Dawn does not have CI or an automated test suite yet, so changes are verified by hand and the current requirements are minimal. [Planned requirements](#planned-requirements) describes how they will be tightened as CI, ISA tests and a benchmark are added.

- [Asking questions](#asking-questions)
- [Opening an issue](#opening-an-issue)
- [Before you write code](#before-you-write-code)
- [Making changes](#making-changes)
- [Verifying your change](#verifying-your-change)
- [Opening a pull request](#opening-a-pull-request)
- [Review and merge](#review-and-merge)
- [AI-assisted contributions](#ai-assisted-contributions)
- [Labels](#labels)
- [Planned requirements](#planned-requirements)
- [Known limitations](#known-limitations)

## Asking questions

Ask questions on the [Open Systems Group Discord](https://discord.gg/nJFy65p2NC) rather than in issues. That includes "how do I build this", "why is X designed this way", and "is anyone working on Y".

## Opening an issue

Open an issue using one of the templates:

| Template | Use it for |
| --- | --- |
| **Bug report** | The core does the wrong thing: a wrong result, a hang, a garbled frame, a crash in simulation or on an FPGA. |
| **Feature / design proposal** | New microarchitecture, a new peripheral, performance work, or tooling. |
| **Build / setup problem** | You can't get the toolchain, Nix, sbt or Verilator working, or the simulator won't build or run. |
| **Performance issue** | A program takes more cycles than expected, a change made the core slower, or the Verilator simulation runs slowly. |
| **FPGA / synthesis issue** | Vivado fails, timing is not met, the design does not fit, or the core behaves differently on a board than in simulation. |
| **Documentation issue** | The README, this guide or a code comment is wrong, out of date or incomplete. |

Each template applies a `type:`, `topic:` or `area:` label; maintainers add the rest during triage (see [Labels](#labels)).

A good bug report lets someone else reproduce the problem **on the latest `main`** with a single command. At minimum, include:

- the commit hash;
- the program you ran, as source or a `.bin`;
- the exact command, including the cycle limit.

If you can, compare against a reference such as [Spike](https://github.com/riscv-software-src/riscv-isa-sim) or real RISC-V hardware. That shows the core is wrong rather than the program. Attach waveforms (VCD/FST) and long logs as files; don't paste them inline.

For a performance issue, give numbers that do not depend on your machine: cycle counts, how far `mandelbrot.c` renders within a fixed cycle limit, or CoreMark/MHz, measured on `main` and on the commit you are reporting. Simulation speed in kHz depends on the host CPU and `DAWN_PGO`, so report it only when the simulator itself is slow.

For an FPGA issue, name the board and Vivado version, and attach the timing summary and utilization report when timing or fit is the problem.

## Before you write code

- **Small fixes:** typos, script fixes, obvious bugs. Go straight to a PR.
- **Anything bigger:** new units, pipeline changes, cache or memory changes, new peripherals. Open a *Feature / design proposal* issue or talk to us on Discord first. Design problems found during code review usually mean rewriting the PR, so agree on the design first.
- **Check the open issues first.** Someone may already be working on it. If an issue is unassigned, comment on it to claim it.

## Making changes

### Setup

Follow [Getting Started](README.md#getting-started) in the README. In short: `nix develop` (or `direnv allow`), plus the xpack RISC-V GCC unpacked into the repo root.

### Code style

- **Format with scalafmt** using the repo's [`.scalafmt.conf`](.scalafmt.conf): 4-space indent, 140 columns. Metals formats on save. Please don't mix reformatting of untouched code into a functional change.
- **Match the existing Chisel conventions:**
  - `PascalCase` for modules and bundles (`ReorderBuffer`, `BufferEntry`);
  - `snake_case` for hardware signals and `io` fields (`write_ready`, `program_pointer`);
  - everything in `package RISCV`.
- **Explain what a new module does.** Start each new module with a block comment describing its purpose and behavior, as in [`src/core/ReorderBuffer.scala`](src/core/ReorderBuffer.scala). For a pipeline or memory change, also explain why the design is shaped the way it is.
- **Make new features switchable.** Where practical, put a new microarchitecture feature or size choice behind a constructor parameter, so it can be turned off or reverted without ripping it out.

### Keep PRs focused

- **One topic per PR.** A branch predictor change and a cache fix are two PRs.
- **Refactors go first, separately.** If a feature needs a refactor, send the refactor as its own PR with no functional change. A reviewer can then check the refactor by confirming the demo output is unchanged.

### Generated files

- **Never commit `generated/`.** It's emitted Verilog and simulation output, and it's gitignored.
- **Edit the Chisel sources under `src/`, never the emitted Verilog.**
- **Vivado project churn:** don't commit changes under `Saturn5PScaffold/*.gen/` or `Saturn5PScaffold/*.ip_user_files/` unless you actually changed an IP configuration. If you did, say so in the PR. Hand-written FPGA sources live in `Saturn5PScaffold/Saturn5PScaffold.srcs/sources_1/new/` and the constraints in `Saturn5PScaffold.srcs/constrs_1/`. The Vivado project builds from a copy of the Chisel output in `Saturn5PScaffold.srcs/sources_1/imports/generated/`; after regenerating, re-import the files from `generated/`. That copy is gitignored, so it is never committed.
- **Committing `.bin`/`.hex` programs:** only commit one when it's meant to be a shared demo or test program, and include its source or say where it came from.

## Verifying your change

There is no CI or automated test suite yet, so verification is manual. Every PR that touches `src/`, `simulation/` or `scripts/` should show the following.

1. **RTL generates cleanly:**
   ```sh
   ./scripts/generate-verilog.sh
   ```
2. **The demo programs still run correctly.** Use a fixed cycle limit so results are comparable:
   ```sh
   ./scripts/simulate.sh ./programs/mandelbrot.c 5000000
   ./scripts/simulate.sh ./programs/pong.c 5000000
   ```
   Check that `generated/frame.png` looks right and attach it to your PR. Pick a larger limit if your change needs more cycles to show up, and say which limit you used.
3. **For changes that affect timing behavior** (pipeline, caches, memory, branch prediction), compare against `main`:
   - **Performance:** run the same program with the same cycle limit on `main` and on your branch, and compare the frames. How far mandelbrot has rendered at a fixed cycle budget is a crude but useful performance measure until CoreMark lands (#2).
   - **Memory changes:** consider enabling `RANDOMIZE_LATENCY` in [`simulation/simulate_program.cpp`](simulation/simulate_program.cpp) locally to shake out ordering bugs. Don't commit that change.
4. **For FPGA changes:** re-import the regenerated RTL into the Vivado project, build, and say which board you tested on (Saturn5P / Kintex US+, Urbana, …) and which program you loaded with `./scripts/load.sh`.

If you did not run a step, say so in the PR, for example "not tested on FPGA". Do not tick a checkbox for a step you skipped.

## Opening a pull request

- **Branch names:** short and kebab-case, e.g. `bpu-gshare` or `fix-idq-dispatch`.
- **PR title:** the title becomes the commit message on `main` (we squash-merge), so write it as `area: imperative summary`, in lowercase:
  ```
  rob: stall retire while a store is pending
  bpu: add gshare predictor behind a parameter
  scripts: pick the Main app explicitly in generate-verilog.sh
  ```
  Use one of these areas, or a finer one if it's clearer:

  | Area | Covers |
  | --- | --- |
  | `fetch`, `decode`, `idq`, `rob`, `regs`, `bpu` | the core pipeline (`src/core`, `src/pipeline`) |
  | `alu`, `malu`, `lsu`, `jump` | execution units (`src/processing_elements`) |
  | `icache`, `dcache`, `l2`, `mem` | the memory hierarchy (`src/memory`) |
  | `uart`, `vga`, `timer`, `keyboard` | peripherals (`src/peripheral`) |
  | `sim`, `scripts`, `nix`, `fpga`, `programs`, `docs` | everything else |

- **Link the issue:** put `Fixes #N` in the description, or `Part of #N` for partial work.
- **Fill in the PR template.** In particular, say what RTL impact the change has and how you verified it.
- **Open it as a draft** until you've done the verification above, then mark it ready for review.
- **Allow edits from maintainers**, so we can make small fixes without a round trip.

## Review and merge

- **Approval:** every PR needs an approval from a maintainer **other than the author**. Trivial fixes to docs and scripts are the exception.
- **Responding to review:** push follow-up commits. Don't force-push over reviewed code mid-review, because it makes the new changes hard to see. Everything is squashed on merge, so intermediate commit messages don't matter.
- **Merging:** maintainers squash-merge. The PR title becomes the commit subject, and the description becomes the body, so keep both up to date.
- **If `main` breaks:** if a merged change breaks the demo programs on `main`, we revert it first and fix forward in a new PR. A revert isn't a judgment on the contribution.

## AI-assisted contributions

You may use AI tools, but:

- **Disclose it.** Tick the checkbox in the PR template and say briefly how the tool was used.
- **You're responsible for every line.** You must understand and be able to explain everything you submit, and answer review comments yourself.
- **Verify the results yourself.** Run the verification above.

PRs that are clearly unreviewed machine output are closed without review.

## Labels

Maintainers apply labels during triage; you don't need to.

| Label | Meaning |
| --- | --- |
| `type: bug`, `type: feature`, `type: build`, `type: docs` | What kind of issue or PR it is |
| `area: core`, `area: memory`, `area: peripheral`, `area: fpga`, `area: infra` | Which part of the project it touches |
| `topic: perf`, `topic: area`, `topic: timing` | PPA work: IPC, FPGA utilization, Fmax |
| `status: needs-repro` | We can't reproduce the bug yet; more info needed |
| `status: needs-design` | Needs agreement on the approach before code |
| `status: waiting-on-author` | Waiting for the author to respond or update the PR |
| `status: blocked` | Waiting on another issue or PR |
| `good first issue` | Small, well-scoped, good for newcomers |
| `help wanted` | Open for anyone outside the core team to take |

## Planned requirements

Requirements will be tightened in stages as CI, an ISA test suite and a benchmark are added. Each stage keeps the requirements of the stages before it. When a stage takes effect, this section, the PR template and the Stage 0 checklist will be updated in the same PR that adds the infrastructure, and the change will be announced on Discord. PRs opened before that date are reviewed under the old requirements.

### Stage 0: manual verification (current)

- Requirements: the steps in [Verifying your change](#verifying-your-change), with results pasted into the PR.
- Merge rule: one approval from a maintainer other than the author, except for trivial fixes to docs and scripts.
- Limitation: correctness is judged by looking at `frame.png`. Bugs that do not change the rendered frame within the cycle limit go unnoticed.

### Stage 1: CI on every pull request

Starts when a GitHub Actions workflow runs on pull requests. The workflow will run:

| Check | Command | Catches |
| --- | --- | --- |
| Formatting | `scalafmt --check` with the repo's `.scalafmt.conf` | Unformatted Scala |
| Compile and emit RTL | `sbt "runMain RISCV.Main"` | Chisel elaboration errors, firtool failures |
| Verilator lint | `verilator --lint-only` on `generated/filelist.f` | Width mismatches, undriven signals, combinational loops |
| Smoke simulation | `pong.c` and `mandelbrot.c` with a fixed cycle limit, output compared against reference frames committed to the repo | Functional regressions visible in the demos |

Changes for contributors:

- A PR cannot be merged while CI is failing. Maintainers can re-run a job; they do not merge over a failing check.
- A PR that intentionally changes a demo's output at the fixed cycle limit, for example a performance improvement that renders more of the frame, must update the reference frame in the same PR and say why it changed.
- `scripts/generate-verilog.sh` will switch to `sbt "runMain RISCV.Main"`, so generation no longer depends on the order of sbt's main-class menu.
- The Stage 0 manual steps remain required for anything CI does not cover, such as FPGA changes.

### Stage 2: ISA tests

Starts when [riscv-tests](https://github.com/riscv-software-src/riscv-tests) (`rv32ui`, `rv32um`) run in CI. [riscv-arch-test](https://github.com/riscv-non-isa/riscv-arch-test) is added after that.

- The upstream test environment uses CSRs and `ecall` to report pass or fail. Dawn has neither, so the tests will run with a custom environment header that reports the result by writing to an address the simulation harness watches.
- Every functional RTL change must pass the full suite.
- Every bug fix must add a test that fails before the fix and passes after it: either a new ISA-style test or a small program in `programs/` that checks its own result.
- Once the suites are in place, a lockstep comparison against [Spike](https://github.com/riscv-software-src/riscv-isa-sim) is planned. The core will expose each retired instruction (PC, destination register, value), and the harness will compare them one by one against Spike's commit log. This reports the first instruction that diverges, instead of a wrong frame millions of cycles later.

### Stage 3: performance reporting

Starts when CoreMark runs on the core (#2).

- PRs labelled `topic: perf`, and PRs that change the pipeline, caches, memory or branch predictor, must report CoreMark/MHz on `main` and on the branch, with the compiler flags and memory latency settings used.
- A PR that lowers CoreMark/MHz must explain why the regression is acceptable, for example a timing or area gain.
- Planned: CI posts the CoreMark result on each PR, so the numbers no longer have to be reported by hand.

### FPGA area and timing

This applies from Stage 0 onwards. PRs labelled `topic: area` or `topic: timing` must report Vivado utilization (LUTs, FFs, BRAM, DSP) and worst negative slack, before and after the change, for a named board and clock target.

## Known limitations

These are known limitations, not bugs, so please don't open issues for them. If you want to work on one, open a proposal.

*Last updated 2026-09.*

- **ISA:** the core is RV32IM and **unprivileged only**. There are no CSRs, traps, interrupts, privilege modes or MMU.
- **Simulation fidelity:** in simulation, the VGA clock runs at the processor clock, so `frame.png` only approximates what a real monitor shows.
- **Demo programs are compiled RV32I-only:** `scripts/simulate.sh` builds with `-march=rv32i`.
- **Fragile RTL generation:** `scripts/generate-verilog.sh` selects the `Main` app by its position in sbt's menu. Adding a new `object … extends App` can make it generate the wrong top.
