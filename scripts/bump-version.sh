#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  scripts/bump-version.sh <part> <language> [<language> ...]

Where:
  <part>      one of: patch | minor | major
  <language>  one or more of: java | python | typescript | go

Behavior:
  - Current version: latest <language>/v* tag (sort -V), or 1.0.0 if none. For typescript,
    python, and java, the bump uses the higher of that tag version and the version in metadata
    (so files and tags can’t drift behind each other). Go has no version file — tags only.
  - Applies one semver step: patch, minor, or major.
  - Writes the new version into ts/py/java metadata, stages, and makes one commit.
  - For typescript, runs npm to refresh typescript/package-lock.json (needed for npm ci in CI).
  - Tags that commit: e.g. typescript/v1.0.2.

Examples:
  scripts/bump-version.sh patch typescript python
  scripts/bump-version.sh minor java typescript python

Notes:
  - Requires a POSIX shell (Git Bash / WSL).
  - Typescript bumps require npm on PATH (updates package-lock.json).
  - Refuses to run if the git working tree is dirty.
  - Refuses duplicate languages in one invocation.
  - Does not push (you push commits + tags yourself).
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

semver_max() {
  printf '%s\n' "$1" "$2" | sort -V | tail -n 1
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
      grep -E '"version"[[:space:]]*:[[:space:]]*"[0-9]+\.[0-9]+\.[0-9]+"' typescript/package.json \
        | head -n 1 \
        | sed -E 's/.*"version"[[:space:]]*:[[:space:]]*"([^"]+)".*/\1/'
      ;;
    python)
      grep -E '^version[[:space:]]*=[[:space:]]*"[0-9]+\.[0-9]+\.[0-9]+"' python/pyproject.toml \
        | head -n 1 \
        | sed -E 's/^version[[:space:]]*=[[:space:]]*"([^"]+)".*/\1/'
      ;;
    java)
      grep -E '^[[:space:]]*version[[:space:]]*=' java/build.gradle.kts \
        | head -n 1 \
        | sed -E 's/.*\?:[[:space:]]*"([^"]+)".*/\1/'
      ;;
    *)
      die "unknown language: $lang"
      ;;
  esac
}

current_version_for_bump() {
  local lang="$1"
  local tag_ver file_ver
  tag_ver="$(latest_tag_version_or_default "$lang")"
  if [ "$lang" = "go" ]; then
    echo "$tag_ver"
    return
  fi
  file_ver="$(read_file_version "$lang")"
  [ -n "$file_ver" ] || die "could not read current version for $lang"
  semver_max "$tag_ver" "$file_ver"
}

write_file_version() {
  local lang="$1" next="$2"
  case "$lang" in
    typescript)
      sed -i.bak -E "0,/\"version\"[[:space:]]*:[[:space:]]*\"[0-9]+\.[0-9]+\.[0-9]+\"/s//\"version\": \"${next}\"/" typescript/package.json
      rm -f typescript/package.json.bak
      ;;
    python)
      sed -i.bak -E "s/^version[[:space:]]*=[[:space:]]*\"[0-9]+\.[0-9]+\.[0-9]+\"/version = \"${next}\"/" python/pyproject.toml
      rm -f python/pyproject.toml.bak
      ;;
    java)
      sed -i.bak -E "s/(^[[:space:]]*version[[:space:]]*=[^?]*\\?:[[:space:]]*\")([0-9]+\.[0-9]+\.[0-9]+)(\".*$)/\\1${next}\\3/" java/build.gradle.kts
      rm -f java/build.gradle.kts.bak
      ;;
    go)
      ;;
    *)
      die "unknown language: $lang"
      ;;
  esac
}

git_add_version_files_for_lang() {
  local lang="$1"
  case "$lang" in
    typescript) git add typescript/package.json typescript/package-lock.json ;;
    python) git add python/pyproject.toml ;;
    java) git add java/build.gradle.kts ;;
    go) ;;
  esac
}

# After package.json version changes, lockfile root version and tree must match or npm ci fails.
refresh_typescript_lockfile() {
  ( cd typescript && npm install --package-lock-only --no-fund --no-audit )
}

validate_lang() {
  case "$1" in
    java|python|typescript|go) ;;
    *) die "invalid language: $1 (expected java, python, typescript, or go)" ;;
  esac
}

main() {
  if [ "${1:-}" = "-h" ] || [ "${1:-}" = "--help" ]; then usage; exit 0; fi
  local part="${1:-}"
  shift || true
  [ -n "$part" ] || { usage; exit 1; }

  case "$part" in patch|minor|major) ;; *) die "invalid part: $part (expected patch, minor, or major)" ;; esac
  [ "$#" -ge 1 ] || die "at least one language required"

  local -a langs=()
  local seen=""
  local a
  for a in "$@"; do
    validate_lang "$a"
    case " ${seen} " in
      *" ${a} "*) die "duplicate language: $a" ;;
    esac
    seen="${seen} ${a}"
    langs+=("$a")
  done

  require_clean_git

  local -a curv=()
  local -a nextv=()
  local lang cur next

  for lang in "${langs[@]}"; do
    cur="$(current_version_for_bump "$lang")"
    [ -n "$cur" ] || die "could not read current version for $lang"

    next="$(semver_bump "$cur" "$part")"

    curv+=("$cur")
    nextv+=("$next")
  done

  local i
  for i in "${!langs[@]}"; do
    write_file_version "${langs[$i]}" "${nextv[$i]}"
  done

  for lang in "${langs[@]}"; do
    if [ "$lang" = "typescript" ]; then
      refresh_typescript_lockfile
      break
    fi
  done

  local any_file=false
  for lang in "${langs[@]}"; do
    if [ "$lang" != "go" ]; then
      any_file=true
    fi
    git_add_version_files_for_lang "$lang"
  done

  local body=""
  for i in "${!langs[@]}"; do
    body="${body}- ${langs[$i]} ${curv[$i]} -> ${nextv[$i]}"$'\n'
  done

  if [ "$any_file" = true ]; then
    git commit -m "chore(release): bump versions" -m "${body%$'\n'}"
  else
    git commit --allow-empty -m "chore(release): bump versions" -m "${body%$'\n'}"
  fi

  for i in "${!langs[@]}"; do
    tag="${langs[$i]}/v${nextv[$i]}"
    git tag -a "${tag}" -m "${tag}"
  done

  echo "Committed and tagged:"
  for i in "${!langs[@]}"; do
    echo "  ${langs[$i]}/v${nextv[$i]}  (${curv[$i]} -> ${nextv[$i]})"
  done
  echo ""
  echo "Next:"
  echo "  git push origin HEAD"
  echo "  git push origin $(for i in "${!langs[@]}"; do echo -n "${langs[$i]}/v${nextv[$i]} "; done)"
}

main "$@"
