#!/usr/bin/env bash
set -euo pipefail

repo_root="$(git rev-parse --show-toplevel)"
current_root="$repo_root"

allow_current_dirty=0
if [[ "${1:-}" == "--allow-current-dirty" ]]; then
  allow_current_dirty=1
fi

echo "LangBangML worktree audit"
echo "Current: $current_root"
echo

dirty_other=0
dirty_current=0
branch_gap=0
missing_worktree=0
allow_preserved_wip="${LANGBANGML_ALLOW_PRESERVED_WIP:-0}"
preserved_wip_prefix="WIP preserve LangBangML worktree"

while IFS= read -r worktree; do
  [[ -n "$worktree" ]] || continue
  if ! git -C "$worktree" rev-parse --git-dir >/dev/null 2>&1; then
    missing_worktree=1
    echo "[missing] $worktree"
    echo "  listed by git worktree but not present on disk; run git worktree prune when safe."
    echo
    continue
  fi
  branch="$(git -C "$worktree" branch --show-current 2>/dev/null || true)"
  head="$(git -C "$worktree" rev-parse --short HEAD)"
  status="$(git -C "$worktree" status --porcelain)"
  marker="clean"
  if [[ -n "$status" ]]; then
    marker="dirty"
    if [[ "$worktree" == "$current_root" ]]; then
      dirty_current=1
    else
      dirty_other=1
    fi
  fi

  echo "[$marker] ${branch:-detached} $head $worktree"
  if [[ -n "$status" ]]; then
    echo "$status" | sed 's/^/  /'
  fi

  if [[ "$worktree" != "$current_root" && -n "$branch" ]]; then
    missing="$(git -C "$current_root" log --oneline --max-count=8 "HEAD..$branch" 2>/dev/null || true)"
    if [[ -n "$missing" ]]; then
      echo "  commits on $branch not in current branch:"
      echo "$missing" | sed 's/^/    /'
      if [[ "$allow_preserved_wip" == "1" ]] && \
        git -C "$current_root" log --format=%s "HEAD..$branch" | grep -Evq "^${preserved_wip_prefix}"; then
        branch_gap=1
      elif [[ "$allow_preserved_wip" == "1" ]]; then
        echo "  preserved WIP branch gap allowed by LANGBANGML_ALLOW_PRESERVED_WIP=1"
      else
        branch_gap=1
      fi
    fi
  fi
  echo
done < <(git worktree list --porcelain | awk '/^worktree / {print substr($0, 10)}')

if [[ "$dirty_current" -eq 1 && "$allow_current_dirty" -ne 1 ]]; then
  echo "Current worktree is dirty. Commit/stash it, or rerun with --allow-current-dirty while iterating." >&2
  exit 1
fi

if [[ "$dirty_other" -eq 1 ]]; then
  echo "Another worktree is dirty. Inspect and reconcile relevant changes before building/installing to tablets." >&2
  exit 1
fi

if [[ "$branch_gap" -eq 1 ]]; then
  echo "Another worktree branch has commits not present here. Confirm they are unrelated or merge/cherry-pick before tablet install." >&2
  exit 1
fi

if [[ "$missing_worktree" -eq 1 ]]; then
  echo "A missing/prunable worktree is registered. Clean it up with git worktree prune after confirming it is obsolete." >&2
  exit 1
fi

scripts/check-tablet-regressions.sh

echo "Worktree audit passed."
