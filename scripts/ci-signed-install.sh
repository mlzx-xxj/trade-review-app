#!/usr/bin/env bash
set -euo pipefail
mkdir -p ci-v200
[[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r\n')" == 1 ]] || exit 1
sleep 20
adb logcat -d -s ActivityManager:I ActivityTaskManager:I AndroidRuntime:E > ci-v200/boot-logcat.txt
adb shell dumpsys activity lastanr > ci-v200/boot-last-anr.txt
[[ -s ci-v200/boot-logcat.txt && -s ci-v200/boot-last-anr.txt ]] || exit 1
grep -Fq '<no ANR has occurred since boot>' ci-v200/boot-last-anr.txt || exit 1
if grep -Fq 'FATAL EXCEPTION' ci-v200/boot-logcat.txt; then exit 1; fi
args=(--directory delivered-packages --aapt "$ANDROID_HOME/build-tools/35.0.0/aapt" --apksigner "$ANDROID_HOME/build-tools/35.0.0/apksigner")
if [[ -f delivered-packages/old-fixed.apk ]]; then
  args+=(--old-apk delivered-packages/old-fixed.apk --upgrade-fixture delivered-packages/upgrade-fixture.apk)
fi
python3 scripts/run-v200-package-probes.py "${args[@]}"
