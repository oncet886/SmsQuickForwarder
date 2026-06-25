#!/usr/bin/env bash
set -euo pipefail

EXPECTED_REPO="oncet886/SmsQuickForwarder"
EXPECTED_REMOTE="https://github.com/${EXPECTED_REPO}.git"

usage() {
  echo "Usage: $0 \"<commit message>\"" >&2
  exit 2
}

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

[[ $# -eq 1 ]] || usage
COMMIT_MESSAGE="$1"

check_repo() {
  git rev-parse --is-inside-work-tree >/dev/null
  [[ "$(git branch --show-current)" == "main" ]] || fail "Current branch must be main."
  [[ ! -d .git/rebase-merge && ! -d .git/rebase-apply ]] || fail "Rebase in progress."
  [[ ! -f .git/MERGE_HEAD ]] || fail "Merge in progress."
  local origin_url
  origin_url="$(git remote get-url origin 2>/dev/null || true)"
  [[ "$origin_url" == "$EXPECTED_REMOTE" || "$origin_url" == "git@github.com:oncet886/SmsQuickForwarder.git" ]] || fail "origin points to wrong remote: ${origin_url}"
  command -v gh >/dev/null 2>&1 || fail "gh CLI is required."
  gh auth status >/dev/null || fail "GitHub CLI is not logged in. Run gh auth login before pushing."
  gh auth status 2>&1 | grep -q "oncet886" || fail "GitHub CLI must be logged in as oncet886."
}

check_sensitive_files() {
  local tracked staged
  tracked="$(git ls-files | grep -Ei '(\.jks$|\.keystore$|keystore\.properties$|keystore\.zip$)' || true)"
  if [[ -n "$tracked" && "$tracked" != "keystore/keystore.properties.example" ]]; then
    echo "$tracked" >&2
    fail "Sensitive signing file is tracked by Git."
  fi
  staged="$(git diff --cached || true)"
  if echo "$staged" | grep -Eiq '(BEGIN .*PRIVATE KEY|ghp_[A-Za-z0-9_]+|github_pat_[A-Za-z0-9_]+)'; then
    fail "Staged diff appears to contain a private key or GitHub token."
  fi
  local phone_lines
  phone_lines="$(echo "$staged" | grep -E '^[+][^+]*(\+?1?[2-9][0-9]{2}[- .)]*[0-9]{3}[- .]*[0-9]{4}|\+[0-9]{8,})' || true)"
  if [[ -n "$phone_lines" ]] && ! echo "$phone_lines" | grep -Eq '(555[- .)]*01|555010|example|placeholder)'; then
    fail "Staged diff may contain a complete private phone number."
  fi
}

run_tests() {
  export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"
  export GRADLE_USER_HOME="${GRADLE_USER_HOME:-/private/tmp/codex-gradle-home}"
  export ANDROID_HOME="${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}"
  export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-/opt/homebrew/share/android-commandlinetools}"

  if [[ -x ./gradlew ]]; then
    ./gradlew testDebugUnitTest
  else
    gradle --no-daemon testDebugUnitTest
  fi
}

check_repo
check_sensitive_files

if [[ -z "$(git status --porcelain)" ]]; then
  echo "No changes to commit."
  exit 0
fi

run_tests
git add -A
check_sensitive_files
git diff --cached --name-only | grep -Ev '(\.apk$|\.sha256$|\.jks$|\.keystore$|keystore\.properties$|keystore\.zip$)' >/dev/null || fail "No safe files staged."
git commit -m "$COMMIT_MESSAGE"
git pull --ff-only origin main
git push origin main
