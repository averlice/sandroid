#!/usr/bin/env bash
# Installable debug-signed APKs; release signing can be configured separately.
set -euo pipefail

task_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
target="${1:-universal}"
if [[ $# -gt 1 ]]; then
    echo "Usage: $0 [arm32|arm64|universal|all]" >&2
    exit 2
fi
case "$target" in
    arm32|arm64|universal|all) ;;
    -h|--help)
        echo "Usage: $0 [arm32|arm64|universal|all]"
        echo "Outputs debug-signed APKs in build/. Default: universal (ARM32, ARM64, x86-64)."
        exit 0 ;;
    *) echo "Unknown target: $target (expected arm32, arm64, universal or all)" >&2; exit 2 ;;
esac

# Respect an explicit JDK; also find common macOS JDK 17 installations.
if [[ -z "${JAVA_HOME:-}" && "$(uname -s)" == Darwin ]]; then
    for task_jdk in /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
        /usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home; do
        if [[ -x "$task_jdk/bin/java" ]]; then
            export JAVA_HOME="$task_jdk"
            break
        fi
    done
    if [[ -z "${JAVA_HOME:-}" ]]; then
        JAVA_HOME="$(/usr/libexec/java_home -v 17 2>/dev/null || true)"
        [[ -z "$JAVA_HOME" ]] || export JAVA_HOME
    fi
fi

cd "$task_root"
build_apk() {
    local task_target="$1" task_abis
    case "$task_target" in
        arm32) task_abis=armeabi-v7a ;;
        arm64) task_abis=arm64-v8a ;;
        universal) task_abis=armeabi-v7a,arm64-v8a,x86_64 ;;
    esac
    ./gradlew :androidApp:assembleDebug "-PsandroidAbis=$task_abis"
    # Read the actual build version instead of maintaining it twice.
    local task_version task_output
    task_version="$(sed -n 's/^[[:space:]]*versionName = "\([^"]*\)".*/\1/p' AndroidApp/build.gradle.kts)"
    [[ -n "$task_version" ]] || { echo "Cannot read versionName" >&2; exit 1; }
    task_output="build/Sandroid-${task_version}-${task_target}.apk"
    mkdir -p build
    cp AndroidApp/build/outputs/apk/debug/androidApp-debug.apk "$task_output"
    echo "Built: $task_root/$task_output"
}

if [[ "$target" == all ]]; then
    for task_target in arm32 arm64 universal; do build_apk "$task_target"; done
else
    build_apk "$target"
fi
