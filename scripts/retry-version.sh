#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  scripts/retry-version.sh <language>

Where:
  <language>  one of: java | python | typescript | go

Behavior:
  - Requires that the current HEAD commit has exactly one tag matching "<language>/vX.Y.Z"
    (points-at HEAD).
  - Deletes that tag locally and on origin.
  - Recreates the same annotated tag on the current HEAD and pushes it to origin.

Use this to re-trigger CI / publishing for the same version after fixing workflows, without
bumping semver again.

Examples:
  scripts/retry-version.sh java
  make retry-version java
EOF
}

die() { echo "error: $*" >&2; exit 1; }

validate_lang() {
  case "$1" in
    java|python|typescript|go) ;;
    *) die "invalid language: $1 (expected java, python, typescript, or go)" ;;
  esac
}

main() {
  if [ "${1:-}" = "-h" ] || [ "${1:-}" = "--help" ]; then usage; exit 0; fi
  local lang="${1:-}"
  [ -n "$lang" ] || { usage; exit 1; }
  validate_lang "$lang"

  if ! git rev-parse -q --verify HEAD >/dev/null; then
    die "not a git repository or invalid HEAD"
  fi

  local -a tags=()
  local t
  while IFS= read -r t; do
    [ -n "$t" ] && tags+=("$t")
  done < <(git tag --points-at HEAD 2>/dev/null | grep -E "^${lang}/v[0-9]+\\.[0-9]+\\.[0-9]+$" | sort -u || true)

  if [ "${#tags[@]}" -eq 0 ]; then
    die "no ${lang}/v* tag points at HEAD. Retry only works when the current commit is tagged for that language."
  fi
  if [ "${#tags[@]}" -gt 1 ]; then
    die "multiple ${lang}/v* tags on HEAD: ${tags[*]}. Resolve manually."
  fi

  local tag="${tags[0]}"

  if ! git remote get-url origin >/dev/null 2>&1; then
    die "no git remote named 'origin' (add it or push tags yourself)"
  fi

  echo "Retrying tag: ${tag} (on $(git rev-parse --short HEAD))"

  git tag -d "${tag}"
  git push origin ":refs/tags/${tag}"
  git tag -a "${tag}" -m "${tag}"
  git push origin "${tag}"

  echo "Done. Pushed ${tag} to origin."
}

main "$@"
