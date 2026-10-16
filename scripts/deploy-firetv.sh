#!/usr/bin/env bash
# Build the app and install it on a Fire TV (or any Android TV device) over wireless ADB.
#
# Usage: scripts/deploy-firetv.sh [--debug] [--no-build] [--no-launch] [--logs]
#
# Installs the signed release build ("Jellyfin Custom") by default. --debug installs the debug build
# ("Jellyfin Custom Debug") instead, which is a separate app next to it.
#
# The device address is read from (first match wins):
#   1. FIRETV_HOST environment variable, e.g. FIRETV_HOST=192.168.1.50
#   2. firetv.host in local.properties (gitignored), e.g. firetv.host=192.168.1.50
# A port can be included (host:port), otherwise 5555 is used.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD_TYPE=release
BUILD=1
LAUNCH=1
LOGS=0

for arg in "$@"; do
	case "$arg" in
		--debug) BUILD_TYPE=debug ;;
		--no-build) BUILD=0 ;;
		--no-launch) LAUNCH=0 ;;
		--logs) LOGS=1 ;;
		-h|--help) sed -n '2,12p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
		*) echo "error: unknown argument: $arg" >&2; exit 2 ;;
	esac
done

fail() { echo "error: $*" >&2; exit 1; }

command -v adb >/dev/null || fail "adb not found. Install it with: brew install --cask android-platform-tools"

# Resolve device address
HOST="${FIRETV_HOST:-}"
if [[ -z "$HOST" && -f "$ROOT/local.properties" ]]; then
	HOST="$(sed -n 's/^firetv\.host[[:space:]]*=[[:space:]]*//p' "$ROOT/local.properties" | tail -n1)"
fi
[[ -n "$HOST" ]] || fail "no device configured. Set FIRETV_HOST or add 'firetv.host=<ip>' to local.properties"
[[ "$HOST" == *:* ]] || HOST="$HOST:5555"

# Java 21 is required by the build; prefer it over an older default JDK
if [[ "$BUILD" == 1 && -x /usr/libexec/java_home ]]; then
	JAVA_HOME="$(/usr/libexec/java_home -v 21 2>/dev/null)" || fail "JDK 21 not found (see .fork-state/ENVIRONMENT.md)"
	export JAVA_HOME
fi

if [[ "$BUILD" == 1 ]]; then
	TASK="assemble$(tr '[:lower:]' '[:upper:]' <<< "${BUILD_TYPE:0:1}")${BUILD_TYPE:1}"
	echo "==> Building ($TASK)"
	"$ROOT/gradlew" -p "$ROOT" "$TASK" --console=plain -q || fail "build failed"
fi

APK="$(ls -t "$ROOT"/app/build/outputs/apk/"$BUILD_TYPE"/*.apk 2>/dev/null | head -n1 || true)"
[[ "$APK" != *-unsigned.apk ]] || fail "release APK is unsigned. Configure signing in ~/.gradle/gradle.properties (see docs/development.md)"
[[ -n "$APK" ]] || fail "no $BUILD_TYPE APK found in app/build/outputs/apk/$BUILD_TYPE (build first or drop --no-build)"

echo "==> Connecting to $HOST"
CONNECT_OUTPUT="$(adb connect "$HOST" 2>&1 || true)"
echo "$CONNECT_OUTPUT"
if ! adb -s "$HOST" get-state >/dev/null 2>&1; then
	fail "could not connect to $HOST. Check that the device is on, ADB debugging is enabled, and accept the authorization prompt on the TV if one appears"
fi

# Read the application id from the APK so the script keeps working when the id changes
SDK_DIR="${ANDROID_HOME:-$(sed -n 's/^sdk\.dir=//p' "$ROOT/local.properties" 2>/dev/null)}"
AAPT2="$(ls "$SDK_DIR"/build-tools/*/aapt2 2>/dev/null | sort -V | tail -n1 || true)"
PACKAGE=""
[[ -n "$AAPT2" ]] && PACKAGE="$("$AAPT2" dump packagename "$APK" 2>/dev/null || true)"

echo "==> Installing $(basename "$APK")"
adb -s "$HOST" install -r "$APK" || fail "install failed. A signature mismatch with an installed build requires uninstalling it first (this deletes its data)"

if [[ "$LAUNCH" == 1 ]]; then
	if [[ -n "$PACKAGE" ]]; then
		echo "==> Launching $PACKAGE"
		adb -s "$HOST" shell monkey -p "$PACKAGE" -c android.intent.category.LEANBACK_LAUNCHER 1 >/dev/null 2>&1 \
			|| echo "warning: could not launch $PACKAGE" >&2
	else
		echo "warning: could not determine the package name, skipping launch" >&2
	fi
fi

if [[ "$LOGS" == 1 && -n "$PACKAGE" ]]; then
	echo "==> Streaming logs for $PACKAGE (Ctrl-C to stop)"
	sleep 2
	PID="$(adb -s "$HOST" shell pidof -s "$PACKAGE" | tr -d '\r')"
	[[ -n "$PID" ]] || fail "$PACKAGE is not running"
	exec adb -s "$HOST" logcat --pid="$PID"
fi

echo "==> Done"
