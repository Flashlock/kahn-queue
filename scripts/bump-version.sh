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
  - Reads semver from each language’s metadata file (see below).
  - Applies one semver step: patch +0.0.1, minor, or major.
  - Writes the new version back, stages, and makes one commit (all languages together).
  - Tags that commit: e.g. 1.0.1 -> 1.0.2 and tag typescript/v1.0.2.

  Metadata: typescript/package.json, python/pyproject.toml, java/build.gradle.kts, go/VERSION.

Examples:
  scripts/bump-version.sh patch typescript python
  scripts/bump-version.sh minor java typescript python

Notes:
  - Requires a POSIX shell (Git Bash / WSL).
  - Refuses to run if the git working tree is dirty (unless only testing — still require clean).
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
    go)
      head -n 1 go/VERSION | tr -d '\r' | sed -E 's/^[[:space:]]+|[[:space:]]+$//g'
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
      printf '%s\n' "${next}" > go/VERSION
      ;;
    *)
      die "unknown language: $lang"
      ;;
  esac
}

git_add_version_files_for_lang() {
  local lang="$1"
  case "$lang" in
    typescript) git add typescript/package.json ;;
    python) git add python/pyproject.toml ;;
    java) git add java/build.gradle.kts ;;
    go) git add go/VERSION ;;
  esac
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
    cur="$(read_file_version "$lang")"
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
    git_add_version_files_for_lang "$lang"
  done

  local body=""
  for i in "${!langs[@]}"; do
    body="${body}- ${langs[$i]} ${curv[$i]} -> ${nextv[$i]}"$'\n'
  done

  git commit -m "chore(release): bump versions" -m "${body%$'\n'}"

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
