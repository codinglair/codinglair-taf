#!/usr/bin/env bash
set -euo pipefail

readonly ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
readonly OUT="${APPLE_EVIDENCE_DIR:-$ROOT/target/ver-130-002}"
readonly DEVICE_NAME="${APPLE_DEVICE_NAME:-TAF VER-130-002 iPhone}"
readonly DEVICE_TYPE="${APPLE_DEVICE_TYPE:-com.apple.CoreSimulator.SimDeviceType.iPhone-16}"
readonly RUNTIME="${APPLE_RUNTIME:-com.apple.CoreSimulator.SimRuntime.iOS-18-5}"
readonly PLATFORM_VERSION="${APPLE_PLATFORM_VERSION:-18.5}"
readonly XCODE_PATH="${APPLE_XCODE_PATH:-/Applications/Xcode_16.4.app/Contents/Developer}"
readonly APPIUM_VERSION="${APPIUM_VERSION:-3.0.0}"
readonly XCUITEST_VERSION="${XCUITEST_VERSION:-10.0.0}"
readonly TAF_VERSION="${TAF_VERSION:-1.2.0}"
readonly CANDIDATE_REPO="$OUT/candidate-repository"
readonly APP="$OUT/TafAppleFixture.app"
readonly WEB_PORT="${APPLE_WEB_PORT:-8765}"
readonly APPIUM_PORT="${APPLE_APPIUM_PORT:-4723}"
export APPIUM_HOME="$OUT/appium-home"

udid=""
appium_pid=""
web_pid=""
candidate_log=""
appium_raw_log=""

sanitize_text_file() {
  local source="$1"
  local destination="$2"
  local sanitized_temp
  [[ -f "$source" ]] || return 1
  sanitized_temp="$(mktemp "${TMPDIR:-/tmp}/ver-130-002-sanitized.XXXXXX")"
  if ! python3 - "$source" "$sanitized_temp" <<'PY'
import re
import sys

source, destination = sys.argv[1:]
text = open(source, encoding="utf-8", errors="replace").read()

# Appium may echo provider capabilities, HTTP headers, or credential-bearing URLs. Preserve
# protocol flow and session IDs while removing values that can authenticate or identify users.
sensitive_key = r"(?:authorization|proxy-authorization|cookie|set-cookie|password|passwd|token|secret|api[-_]?key|access[-_]?key|credential|username|user[-_]?name)"
text = re.sub(
    rf'(?i)(["\']{sensitive_key}["\']\s*[:=]\s*)(["\'][^"\']*["\']|[^,\s}}]+)',
    r'\1"<redacted>"',
    text,
)
text = re.sub(
    rf'(?im)^({sensitive_key}\s*:\s*).+$',
    r'\1<redacted>',
    text,
)
text = re.sub(
    r'(?i)(https?://)[^/@\s:]+:[^/@\s]+@',
    r'\1<redacted>@',
    text,
)
text = re.sub(
    rf'(?i)([?&](?:{sensitive_key})=)[^&#\s]+',
    r'\1<redacted>',
    text,
)
open(destination, "w", encoding="utf-8").write(text)
PY
  then
    rm -f "$sanitized_temp"
    return 1
  fi
  mv "$sanitized_temp" "$destination"
}

capture_diagnostics() {
  mkdir -p "$OUT/logs"

  if [[ -n "$appium_pid" ]]; then
    curl --silent --show-error --max-time 5 "http://127.0.0.1:$APPIUM_PORT/status" \
      > "$OUT/appium-status-final.json" 2> "$OUT/logs/appium-status-final-error.log" || true
    local sessions_raw
    sessions_raw="$(mktemp "${TMPDIR:-/tmp}/ver-130-002-sessions.XXXXXX.json")"
    curl --silent --show-error --max-time 5 "http://127.0.0.1:$APPIUM_PORT/sessions" \
      > "$sessions_raw" 2> "$OUT/logs/appium-sessions-final-error.log" || true
    sanitize_text_file "$sessions_raw" "$OUT/appium-sessions-final.json" || true
    rm -f "$sessions_raw"
  fi

  if [[ -n "$udid" ]]; then
    xcrun simctl list devices > "$OUT/simctl-devices-final.txt" 2>&1 || true
    xcrun simctl list --json > "$OUT/simctl-list-final.json" 2>&1 || true
  fi

}

sanitize_appium_log() {
  [[ -n "$appium_raw_log" && -f "$appium_raw_log" ]] || return 0
  sanitize_text_file "$appium_raw_log" "$OUT/logs/appium-sanitized.log" || \
    printf '%s\n' 'Appium log sanitization failed; raw log was not retained.' \
      > "$OUT/logs/appium-sanitization-error.log"
  rm -f "$appium_raw_log"
  appium_raw_log=""
}

cleanup() {
  local status=$?
  set +e
  capture_diagnostics
  if [[ -n "$appium_pid" ]]; then
    kill "$appium_pid" 2>/dev/null || true
    wait "$appium_pid" 2>/dev/null || true
  fi
  sanitize_appium_log
  if [[ "$status" -ne 0 && -f "$OUT/logs/appium-sanitized.log" ]]; then
    printf '%s\n' '::group::Sanitized Appium failure diagnostics (last 400 lines)'
    tail -n 400 "$OUT/logs/appium-sanitized.log"
    printf '%s\n' '::endgroup::'
  fi
  if [[ -n "$web_pid" ]]; then kill "$web_pid" 2>/dev/null || true; fi
  if [[ -n "$udid" ]]; then
    xcrun simctl shutdown "$udid" 2>/dev/null || true
    xcrun simctl delete "$udid" 2>/dev/null || true
  fi
  mkdir -p "$OUT/logs"
  if [[ -n "$candidate_log" && -f "$candidate_log" ]]; then
    mv "$candidate_log" "$OUT/logs/candidate-build.log" 2>/dev/null || true
  fi
  printf '%s\n' "exit=$status ownedSimulatorDeleted=$([[ -z "$udid" ]] && echo not-created || echo attempted)" > "$OUT/cleanup.txt"
  exit "$status"
}
trap cleanup EXIT INT TERM

export DEVELOPER_DIR="$XCODE_PATH"

command -v java > /dev/null
command -v node > /dev/null
command -v npm > /dev/null
command -v python3 > /dev/null
command -v curl > /dev/null
command -v codesign > /dev/null
command -v xcodebuild > /dev/null
command -v xcrun > /dev/null
[[ "$(java -version 2>&1 | head -n 1)" == *'version "25'* ]] || { echo 'Java 25 is required' >&2; exit 10; }
[[ "$(node --version)" == v22.12.0 ]] || { echo 'Node 22.12.0 is required' >&2; exit 11; }
[[ -d "$XCODE_PATH" ]] || { echo "Selected Xcode is unavailable: $XCODE_PATH" >&2; exit 12; }

candidate_log="$(mktemp "${TMPDIR:-/tmp}/ver-130-002-candidate.XXXXXX.log")"
"$ROOT/mvnw" -B -ntp clean deploy -Prelease-staging -DskipTests \
  "-DaltDeploymentRepository=taf-candidate::default::file:$CANDIDATE_REPO" \
  | tee "$candidate_log"
mkdir -p "$OUT/logs" "$CANDIDATE_REPO"
mv "$candidate_log" "$OUT/logs/candidate-build.log"
candidate_log=""

xcodebuild -version | tee "$OUT/xcode-version.txt"
xcrun simctl list --json > "$OUT/simctl-list.json"
xcrun simctl list runtimes | grep -F "$RUNTIME" > "$OUT/selected-runtime.txt" || {
  echo "Selected runtime is unavailable: $RUNTIME" >&2; exit 13;
}
xcrun simctl list devicetypes | grep -F "$DEVICE_TYPE" > "$OUT/selected-device-type.txt" || {
  echo "Selected device type is unavailable: $DEVICE_TYPE" >&2; exit 14;
}

sdk="$(xcrun --sdk iphonesimulator --show-sdk-path)"
arch="$(uname -m)"
mkdir -p "$APP"
cp "$ROOT/qualification/apple-simulator/fixture/Info.plist" "$APP/Info.plist"
xcrun swiftc "$ROOT/qualification/apple-simulator/fixture/AppDelegate.swift" \
  -parse-as-library \
  -sdk "$sdk" -target "${arch}-apple-ios${PLATFORM_VERSION}-simulator" \
  -framework UIKit -framework WebKit -o "$APP/TafAppleFixture"
codesign --force --sign - "$APP"
shasum -a 256 "$APP/TafAppleFixture" > "$OUT/fixture-sha256.txt"

udid="$(xcrun simctl create "$DEVICE_NAME" "$DEVICE_TYPE" "$RUNTIME")"
xcrun simctl boot "$udid"
xcrun simctl bootstatus "$udid" -b
xcrun simctl install "$udid" "$APP"

python3 -m http.server "$WEB_PORT" --bind 127.0.0.1 \
  --directory "$ROOT/qualification/apple-simulator/web" > "$OUT/logs/web-server.log" 2>&1 &
web_pid=$!

npm install --no-save --prefix "$OUT/appium" "appium@$APPIUM_VERSION" \
  > "$OUT/logs/appium-install.log" 2>&1
"$OUT/appium/node_modules/.bin/appium" driver install "xcuitest@$XCUITEST_VERSION" \
  > "$OUT/logs/xcuitest-install.log" 2>&1
appium_raw_log="$(mktemp "${TMPDIR:-/tmp}/ver-130-002-appium.XXXXXX.log")"
"$OUT/appium/node_modules/.bin/appium" --address 127.0.0.1 --port "$APPIUM_PORT" \
  > "$appium_raw_log" 2>&1 &
appium_pid=$!

for _ in {1..60}; do
  if curl --fail --silent "http://127.0.0.1:$APPIUM_PORT/status" > "$OUT/appium-status.json"; then break; fi
  sleep 1
done
curl --fail --silent "http://127.0.0.1:$APPIUM_PORT/status" > "$OUT/appium-status.json"

export APPLE_DEVICE_NAME="$DEVICE_NAME"
export APPLE_DEVICE_UDID="$udid"
export APPLE_PLATFORM_VERSION="$PLATFORM_VERSION"
export APPLE_TEST_URL="http://127.0.0.1:$WEB_PORT/index.html"

candidate_uri="file://$CANDIDATE_REPO"
"$ROOT/mvnw" -B -ntp -f "$ROOT/qualification/apple-simulator/pom.xml" \
  "-Dtaf.version=$TAF_VERSION" "-Dtaf.candidate.repository=$candidate_uri" \
  -Dtaf.apple.live=true test \
  | tee "$OUT/logs/live-smoke.log"

set +e
"$ROOT/mvnw" -B -ntp -f "$ROOT/qualification/apple-simulator/pom.xml" \
  "-Dtaf.version=$TAF_VERSION" "-Dtaf.candidate.repository=$candidate_uri" \
  -Dtaf.apple.live=true \
  -Dtaf.apple.controlledFailure=true \
  '-Dtest=AppleSimulatorSmokeTest#controlledFailureStillUsesNormalSessionCleanup' test \
  > "$OUT/logs/controlled-failure.log" 2>&1
controlled_status=$?
set -e
[[ "$controlled_status" -ne 0 ]] || { echo 'Controlled failure unexpectedly passed' >&2; exit 20; }
sessions="$(curl --fail --silent "http://127.0.0.1:$APPIUM_PORT/sessions")"
python3 -c 'import json,sys; data=json.loads(sys.argv[1]); assert data.get("value") == [], data' "$sessions"
printf '%s\n' 'expectedFailureObserved=true appiumSessionsAfterCleanup=0' > "$OUT/controlled-failure-cleanup.txt"

git -C "$ROOT" rev-parse HEAD > "$OUT/tested-sha.txt"
git -C "$ROOT" diff --binary | shasum -a 256 > "$OUT/working-tree-diff-sha256.txt"
find "$CANDIDATE_REPO/com/codinglair/taf" -type f \( -name '*.jar' -o -name '*.pom' \) \
  -exec shasum -a 256 {} + > "$OUT/candidate-artifact-sha256.txt"
npm --prefix "$OUT/appium" exec appium -- --version > "$OUT/appium-version.txt"
"$OUT/appium/node_modules/.bin/appium" driver list --installed --json > "$OUT/appium-drivers.json"
node --version > "$OUT/node-version.txt"
npm --version > "$OUT/npm-version.txt"
sw_vers > "$OUT/macos-version.txt"
find "$APPIUM_HOME" -type f -ipath '*webdriveragent*' -name package.json -exec shasum -a 256 {} + \
  > "$OUT/wda-package-sha256.txt"
{
  printf '%s\n' \
    'evidenceLayer=REAL_APPLE_SIMULATOR' \
    "tafVersion=$TAF_VERSION" \
    'javaClientVersion=10.1.1' \
    'seleniumVersion=4.43.0' \
    "appiumVersion=$APPIUM_VERSION" \
    "xcuitestVersion=$XCUITEST_VERSION" \
    "runtime=$RUNTIME" \
    "deviceType=$DEVICE_TYPE" \
    "platformVersion=$PLATFORM_VERSION" \
    'deviceFamily=IPHONE' \
    'deviceKind=SIMULATOR' \
    'topology=LOCAL_HOST' \
    'modes=NATIVE,HYBRID,SAFARI'
  java -version 2>&1 | head -n 1
  node --version
  npm --version
  xcodebuild -version
  sw_vers
} > "$OUT/compatibility-manifest.txt"
printf '%s\n' 'hostedStatus=PASSED' 'physicalDeviceStatus=UNVERIFIED' > "$OUT/outcome.txt"
