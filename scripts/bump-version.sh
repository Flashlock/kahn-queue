#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  scripts/bump-version.sh <part> <language>

Where:
  <part>     one of: patch | minor | major
  <language> one of: java | python | typescript | go

Behavior:
  - Updates the language's version metadata (where applicable)
  - Creates a git commit: "chore(release): <language> vX.Y.Z"
  - Creates an annotated git tag: "<language>/vX.Y.Z"

Notes:
  - Requires a POSIX shell environment (Git Bash / WSL).
  - Refuses to run if the git working tree is dirty.
  - Does not push (you push the tag yourself).
EOF
}

die() { echo "error: $*" >&2; exit 1; }

require_clean_git() {
  if ! git diff --quiet || ! git diff --cached --quiet; then
    die "working tree is dirty. Commit/stash changes first."
  fi
  if [ -n "$(git ls-files --others --exclude-standard)" ]; then
    die "untracked files present. Commit/stash/remove first."
  fi
}

semver_bump() {
  local cur="$1" part="$2"
  [[ "$cur" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]] || die "not a semver: $cur"
  local major="${BASH_REMATCH[1]}" minor="${BASH_REMATCH[2]}" patch="${BASH_REMATCH[3]}"
  case "$part" in
    patch) patch=$((patch + 1)) ;;
    minor) minor=$((minor + 1)); patch=0 ;;
    major) major=$((major + 1)); minor=0; patch=0 ;;
    *) die "invalid part: $part" ;;
  esac
  echo "${major}.${minor}.${patch}"
}

latest_tag_version_or_default() {
  local lang="$1"
  local pattern="${lang}/v*"
  local latest
  latest="$(git tag --list "${pattern}" | sed -E "s#^${lang}/v##" | sort -V | tail -n 1 || true)"
  if [ -z "$latest" ]; then
    echo "1.0.0"
  else
    echo "$latest"
  fi
}

read_file_version() {
  local lang="$1"
  case "$lang" in
    typescript)
      # "version": "X.Y.Z"
      grep -E '"version"[[:space:]]*:[[:space:]]*"[0-9]+\.[0-9]+\.[0-9]+"' typescript/package.json \
        | head -n 1 \
        | sed -E 's/.*"version"[[:space:]]*:[[:space:]]*"([^"]+)".*/\1/'
      ;;
    python)
      # version = "X.Y.Z"
      grep -E '^version[[:space:]]*=[[:space:]]*"[0-9]+\.[0-9]+\.[0-9]+"' python/pyproject.toml \
        | head -n 1 \
        | sed -E 's/^version[[:space:]]*=[[:space:]]*"([^"]+)".*/\1/'
      ;;
    java)
      # version = (...) ?: "X.Y.Z"
      grep -E '^[[:space:]]*version[[:space:]]*=' java/build.gradle.kts \
        | head -n 1 \
        | sed -E 's/.*\?:[[:space:]]*"([^"]+)".*/\1/'
      ;;
    go)
      # Go has no in-file version; release via tags only.
      latest_tag_version_or_default go
      ;;
    *)
      die "unknown language: $lang"
      ;;
  esac
}

write_file_version() {
  local lang="$1" next="$2"
  case "$lang" in
    typescript)
      # Replace only the first match of the version field.
      sed -i.bak -E "0,/\"version\"[[:space:]]*:[[:space:]]*\"[0-9]+\.[0-9]+\.[0-9]+\"/s//\"version\": \"${next}\"/" typescript/package.json
      rm -f typescript/package.json.bak
      ;;
    python)
      sed -i.bak -E "s/^version[[:space:]]*=[[:space:]]*\"[0-9]+\.[0-9]+\.[0-9]+\"/version = \"${next}\"/" python/pyproject.toml
      rm -f python/pyproject.toml.bak
      ;;
    java)
      # Update the default version string (the value after ?: "...").
      sed -i.bak -E "s/(^[[:space:]]*version[[:space:]]*=[^?]*\\?:[[:space:]]*\")([0-9]+\.[0-9]+\.[0-9]+)(\".*$)/\\1${next}\\3/" java/build.gradle.kts
      rm -f java/build.gradle.kts.bak
      ;;
    go)
      # No metadata to update.
      ;;
    *)
      die "unknown language: $lang"
      ;;
  esac
}

main() {
  if [ "${1:-}" = "-h" ] || [ "${1:-}" = "--help" ]; then usage; exit 0; fi
  local part="${1:-}" lang="${2:-}"
  [ -n "$part" ] && [ -n "$lang" ] || { usage; exit 1; }

  case "$part" in patch|minor|major) ;; *) die "invalid part: $part" ;; esac
  case "$lang" in java|python|typescript|go) ;; *) die "invalid language: $lang" ;; esac

  require_clean_git

  local cur next tag

  if [ "$lang" = "go" ]; then
    cur="$(latest_tag_version_or_default go)"
  else
    cur="$(read_file_version "$lang")"
    [ -n "$cur" ] || die "could not read current version for $lang"
  fi

  # Ensure baseline is at least 1.0.0 for older packages.
  # If current is < 1.0.0, start from 1.0.0 and then bump the requested part.
  # (Example: part=patch => 1.0.1)
  local baseline="$cur"
  if printf '%s\n%s\n' "1.0.0" "$cur" | sort -V -C; then
    baseline="$cur"
  else
    baseline="1.0.0"
  fi

  next="$(semver_bump "$baseline" "$part")"
  tag="${lang}/v${next}"

  # Avoid re-tagging an existing version.
  if git rev-parse -q --verify "refs/tags/${tag}" >/dev/null; then
    die "tag already exists: ${tag}"
  fi

  if [ "$lang" != "go" ]; then
    write_file_version "$lang" "$next"
    git add -A
    git commit -m "chore(release): ${lang} v${next}"
  else
    # For go-only bumps, we still create a lightweight release commit for traceability,
    # without changing files.
    git commit --allow-empty -m "chore(release): go v${next}"
  fi

  git tag -a "${tag}" -m "${tag}"

  echo "Created:"
  echo "  version: ${cur} -> ${next}"
  echo "  tag: ${tag}"
  echo ""
  echo "Next:"
  echo "  git push origin HEAD"
  echo "  git push origin ${tag}"
}

main "$@"

