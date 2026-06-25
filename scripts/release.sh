#!/usr/bin/env bash
set -euo pipefail

EXPECTED_REPO="oncet886/SmsQuickForwarder"
EXPECTED_REMOTE="https://github.com/${EXPECTED_REPO}.git"
EXPECTED_APP_ID="com.oncet.smsquickforwarder"
EXPECTED_CERT_SHA256="BC:29:EC:4B:7D:30:CC:1B:48:33:0E:C7:07:CC:E8:0D:57:C2:52:31:3F:F1:9C:A4:B7:CE:00:D8:14:57:27:17"
EXPECTED_ALIAS="smsquickforwarder"
KEYSTORE_FILE="keystore/smsquickforwarder-release.jks"
KEYSTORE_PROPERTIES="keystore/keystore.properties"

usage() {
  echo "Usage: $0 <versionName> <versionCode> \"<release title>\" \"<release notes>\"" >&2
  exit 2
}

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

[[ $# -eq 4 ]] || usage

VERSION_NAME="$1"
VERSION_CODE="$2"
RELEASE_TITLE="$3"
RELEASE_NOTES="$4"
TAG="v${VERSION_NAME}"
APK_NAME="SmsQuickForwarder-v${VERSION_NAME}-${VERSION_CODE}-release.apk"
SHA_FILE="${APK_NAME}.sha256"
RELEASE_NOTES_FILE="$(mktemp)"

cleanup() {
  rm -f "$RELEASE_NOTES_FILE"
}
trap cleanup EXIT

command_exists() {
  command -v "$1" >/dev/null 2>&1
}

find_android_tool() {
  local tool="$1"
  local candidate=""
  if [[ -n "${ANDROID_HOME:-}" && -x "${ANDROID_HOME}/build-tools/35.0.0/${tool}" ]]; then
    echo "${ANDROID_HOME}/build-tools/35.0.0/${tool}"
    return
  fi
  if [[ -n "${ANDROID_SDK_ROOT:-}" && -x "${ANDROID_SDK_ROOT}/build-tools/35.0.0/${tool}" ]]; then
    echo "${ANDROID_SDK_ROOT}/build-tools/35.0.0/${tool}"
    return
  fi
  candidate="$(find "${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}" -path "*/build-tools/*/${tool}" -type f 2>/dev/null | sort -V | tail -n 1 || true)"
  [[ -n "$candidate" ]] || fail "Android build tool not found: ${tool}"
  echo "$candidate"
}

normalize_sha256() {
  echo "$1" | tr '[:lower:]' '[:upper:]' | sed 's/../&:/g; s/:$//'
}

current_version_name() {
  sed -nE "s/[[:space:]]*versionName[[:space:]]+['\"]([^'\"]+)['\"].*/\1/p" app/build.gradle | head -n 1
}

current_version_code() {
  sed -nE "s/[[:space:]]*versionCode[[:space:]]+([0-9]+).*/\1/p" app/build.gradle | head -n 1
}

check_sensitive_files() {
  local tracked
  tracked="$(git ls-files | grep -Ei '(\.jks$|\.keystore$|keystore\.properties$|keystore\.zip$)' || true)"
  if [[ -n "$tracked" ]]; then
    if [[ "$tracked" != "keystore/keystore.properties.example" ]]; then
      echo "$tracked" >&2
      fail "Sensitive signing file is tracked by Git."
    fi
  fi

  local staged
  staged="$(git diff --cached || true)"
  if echo "$staged" | grep -Eiq '(BEGIN .*PRIVATE KEY|ghp_[A-Za-z0-9_]+|github_pat_[A-Za-z0-9_]+)'; then
    fail "Staged diff appears to contain a private key or GitHub token."
  fi
  local password_lines
  password_lines="$(echo "$staged" | grep -Ei '^[+][^+]*(storePassword|keyPassword)[[:space:]]*=' || true)"
  if [[ -n "$password_lines" ]] && ! echo "$password_lines" | grep -Eq '(change-me|your-store-password|your-key-password)'; then
    fail "Staged diff may contain a real signing password."
  fi
  local phone_lines
  phone_lines="$(echo "$staged" | grep -E '^[+][^+]*(\+?1?[2-9][0-9]{2}[- .)]*[0-9]{3}[- .]*[0-9]{4}|\+[0-9]{8,})' || true)"
  if [[ -n "$phone_lines" ]] && ! echo "$phone_lines" | grep -Eq '(555[- .)]*01|555010|example|placeholder)'; then
    fail "Staged diff may contain a complete private phone number."
  fi
}

check_repo() {
  git rev-parse --is-inside-work-tree >/dev/null
  [[ "$(git branch --show-current)" == "main" ]] || fail "Current branch must be main."
  [[ ! -d .git/rebase-merge && ! -d .git/rebase-apply ]] || fail "Rebase in progress."
  [[ ! -f .git/MERGE_HEAD ]] || fail "Merge in progress."

  local origin_url
  origin_url="$(git remote get-url origin 2>/dev/null || true)"
  [[ "$origin_url" == "$EXPECTED_REMOTE" || "$origin_url" == "git@github.com:oncet886/SmsQuickForwarder.git" ]] || fail "origin points to wrong remote: ${origin_url}"

  command_exists gh || fail "gh CLI is required."
  gh auth status >/dev/null || fail "GitHub CLI is not logged in. Run gh auth login before publishing."
  gh auth status 2>&1 | grep -q "oncet886" || fail "GitHub CLI must be logged in as oncet886."

  git fetch origin
  git pull --ff-only origin main
}

check_version() {
  [[ "$VERSION_CODE" =~ ^[0-9]+$ ]] || fail "versionCode must be numeric."
  local old_name old_code
  old_name="$(current_version_name)"
  old_code="$(current_version_code)"
  [[ -n "$old_name" && -n "$old_code" ]] || fail "Could not read current version from app/build.gradle."
  (( VERSION_CODE > old_code )) || fail "New versionCode ${VERSION_CODE} must be greater than current ${old_code}."
  [[ "$VERSION_NAME" != "$old_name" ]] || fail "New versionName must differ from current ${old_name}."

  if git rev-parse -q --verify "refs/tags/${TAG}" >/dev/null; then
    fail "Local tag already exists: ${TAG}"
  fi
  if git ls-remote --exit-code --tags origin "refs/tags/${TAG}" >/dev/null 2>&1; then
    fail "Remote tag already exists: ${TAG}"
  fi
  if gh release view "$TAG" --repo "$EXPECTED_REPO" >/dev/null 2>&1; then
    fail "GitHub Release already exists: ${TAG}"
  fi
}

update_version() {
  perl -0pi -e "s/versionCode\\s+\\d+/versionCode ${VERSION_CODE}/" app/build.gradle
  perl -0pi -e "s/versionName\\s+['\"][^'\"]+['\"]/versionName '${VERSION_NAME}'/" app/build.gradle
  grep -q "applicationId '${EXPECTED_APP_ID}'" app/build.gradle || fail "applicationId changed or missing."
}

update_changelog() {
  local today entry rest
  today="$(date +%F)"
  entry="## [${VERSION_NAME}] - ${today}

### Changed
- ${RELEASE_NOTES}

"
  if [[ -f CHANGELOG.md ]]; then
    rest="$(tail -n +2 CHANGELOG.md)"
    printf '# Changelog\n\n%s%s\n' "$entry" "$rest" > CHANGELOG.md
  else
    printf '# Changelog\n\n%s' "$entry" > CHANGELOG.md
  fi
}

run_build() {
  [[ -f "$KEYSTORE_FILE" ]] || fail "Missing Release keystore: ${KEYSTORE_FILE}"
  [[ -f "$KEYSTORE_PROPERTIES" ]] || fail "Missing Release signing properties: ${KEYSTORE_PROPERTIES}"

  export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"
  export GRADLE_USER_HOME="${GRADLE_USER_HOME:-/private/tmp/codex-gradle-home}"
  export ANDROID_HOME="${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}"
  export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-/opt/homebrew/share/android-commandlinetools}"

  if [[ -x ./gradlew ]]; then
    ./gradlew clean testDebugUnitTest assembleRelease
  else
    gradle --no-daemon clean testDebugUnitTest assembleRelease
  fi
}

copy_apk() {
  local built
  built="$(find app/build/outputs/apk/release -name '*release.apk' -type f | head -n 1)"
  [[ -n "$built" ]] || fail "Release APK not found."
  cp "$built" "$APK_NAME"
}

verify_apk() {
  local aapt apksigner badging manifest signer_sha signer_sha_norm app_id version_name version_code
  aapt="$(find_android_tool aapt)"
  apksigner="$(find_android_tool apksigner)"

  badging="$("$aapt" dump badging "$APK_NAME")"
  app_id="$(echo "$badging" | sed -nE "s/package: name='([^']+)'.*/\1/p")"
  version_code="$(echo "$badging" | sed -nE "s/.*versionCode='([^']+)'.*/\1/p")"
  version_name="$(echo "$badging" | sed -nE "s/.*versionName='([^']+)'.*/\1/p")"

  [[ "$app_id" == "$EXPECTED_APP_ID" ]] || fail "Unexpected applicationId: ${app_id}"
  [[ "$version_name" == "$VERSION_NAME" ]] || fail "Unexpected versionName: ${version_name}"
  [[ "$version_code" == "$VERSION_CODE" ]] || fail "Unexpected versionCode: ${version_code}"
  if echo "$badging" | grep -q "application-debuggable"; then
    fail "Release APK is debuggable."
  fi

  manifest="$("$aapt" dump xmltree "$APK_NAME" AndroidManifest.xml)"
  if echo "$manifest" | grep -q "android:debuggable"; then
    fail "Release manifest contains android:debuggable."
  fi

  "$apksigner" verify --verbose --print-certs "$APK_NAME" > "${APK_NAME}.apksigner.txt"
  grep -Eq "Verified using v[2-9].*: true" "${APK_NAME}.apksigner.txt" || fail "APK is not verified with v2 or newer signing."
  signer_sha="$(sed -nE 's/.*Signer #1 certificate SHA-256 digest: ([a-fA-F0-9]+).*/\1/p' "${APK_NAME}.apksigner.txt" | head -n 1)"
  signer_sha_norm="$(normalize_sha256 "$signer_sha")"
  rm -f "${APK_NAME}.apksigner.txt"
  [[ "$signer_sha_norm" == "$EXPECTED_CERT_SHA256" ]] || fail "Signing certificate SHA-256 mismatch: ${signer_sha_norm}"
}

generate_checksums() {
  local apk_sha
  apk_sha="$(shasum -a 256 "$APK_NAME" | awk '{print $1}')"
  printf '%s  %s\n' "$apk_sha" "$APK_NAME" > "$SHA_FILE"
  md5 "$APK_NAME" || true
}

commit_source() {
  git status
  git diff
  git diff --cached
  git add -A
  check_sensitive_files
  git diff --cached --name-only | grep -Ev '(\.apk$|\.sha256$|\.jks$|\.keystore$|keystore\.properties$|keystore\.zip$)' >/dev/null || fail "No source changes staged for release commit."
  git commit -m "release: ${TAG}"
}

push_and_tag() {
  git push origin main
  git tag -a "$TAG" -m "SmsQuickForwarder ${TAG}"
  git push origin "$TAG"
}

create_release() {
  local apk_sha release_url
  apk_sha="$(awk '{print $1}' "$SHA_FILE")"
  cat > "$RELEASE_NOTES_FILE" <<EOF
## SmsQuickForwarder ${TAG}

Version code: ${VERSION_CODE}

### Changes

${RELEASE_NOTES}

### Downloads

- \`${APK_NAME}\`

### Verification

APK SHA-256:

\`${apk_sha}\`

Signing certificate SHA-256:

\`${EXPECTED_CERT_SHA256}\`

### Installation

- 第一次从 Debug 版切换到 Release 版时，需要先卸载 Debug 版。
- 后续使用相同签名的 Release 版本可以直接覆盖升级。
- 本应用只处理普通 SMS，不支持直接读取 RCS。
- 自动发送短信可能产生运营商费用。
EOF

  gh release create "$TAG" \
    "$APK_NAME" \
    "$SHA_FILE" \
    --repo "$EXPECTED_REPO" \
    --title "$RELEASE_TITLE" \
    --notes-file "$RELEASE_NOTES_FILE"

  release_url="$(gh release view "$TAG" --repo "$EXPECTED_REPO" --json url -q .url)"
  echo "GitHub Release: ${release_url}"
}

final_verify() {
  gh release view "$TAG" --repo "$EXPECTED_REPO"
  git status
}

check_repo
check_sensitive_files
check_version
update_version
update_changelog
run_build
copy_apk
verify_apk
generate_checksums
commit_source
push_and_tag
create_release
final_verify
