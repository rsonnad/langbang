# Tablet Build Integrity

This repo often has several active worktrees. A tablet install must represent the
full accepted product state, not only the feature branch that happened to build
last.

## Failure Mode

The repeated regression pattern is:

1. A fix is made in one worktree but left dirty, local-only, or on a different
   branch.
2. A separate feature branch builds a higher `versionCode`.
3. That APK installs cleanly on the tablets, so the header looks current.
4. Previously accepted behavior disappears because the installed APK came from a
   branch that did not contain the other fix.

Version freshness is not feature completeness.

## Required Gate Before Tablet Install

Before `assemble*`, `install*`, or manual `adb install` for a tablet-facing
build, run:

```bash
scripts/check-worktree-integrity.sh --allow-current-dirty
```

Then reconcile the output:

- Dirty current worktree is allowed only for the feature currently being tested.
- Dirty other worktrees are blockers until inspected.
- Commits on another local worktree branch that are not in the current branch
  are blockers until confirmed unrelated or merged/cherry-picked.
- A fix that should survive future builds must be committed before another
  worktree installs over the tablets.
- If this gate fails, do not continue to `adb install`. Fix the branch first.

## Regression Guard

The worktree gate also runs:

```bash
scripts/check-tablet-regressions.sh
```

This check encodes accepted tablet-visible behavior that has regressed before,
including the Verbs/Phrases panel, related phrase playback, by-pronoun Play All,
and the `with [x] vars` verb play-limit wording. Add to this script when a
tablet-visible fix becomes part of the accepted product state.

If another worktree contains unrelated long-running work, state that explicitly
in the implementation notes before installing.

## Commit Discipline

- Do not leave accepted UI or behavior fixes only as dirty files.
- Do not build a tablet APK from a feature branch without checking whether the
  tablet currently has behavior from another local branch/worktree.
- Prefer one integration branch/worktree for tablet candidates. Feature branches
  can be tested locally, but tablet installs must be made from a branch that
  contains every accepted tablet-visible fix.
- When restoring a lost fix, identify the source worktree/commit in the commit
  message or final notes.

## Verification

After installing, verify all three layers:

```bash
adb -s <serial> shell 'dumpsys package com.sponic.langbangml.enpl | grep -E "versionCode|versionName"'
adb -s <serial> exec-out screencap -p > /tmp/langbang-proof.png
```

Report installed version, source branch/commit, and screenshot-backed behavior.
