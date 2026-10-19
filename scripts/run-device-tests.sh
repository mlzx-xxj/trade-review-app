#!/usr/bin/env bash
# Run as ONE emulator-runner command so the status survives and reports are copied.
set -uo pipefail
if [[ "${V100_CHECK_BOOT:-false}" == true ]]; then
  mkdir -p ci-stability
  [[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r\n')" == 1 ]] || { echo 'Boot checks require a disposable emulator' >&2; exit 1; }
  adb shell wm size > ci-stability/boot-display.txt || exit 1
  adb shell wm density >> ci-stability/boot-display.txt || exit 1
  # AVD heap-size does not guarantee the regular app growth limit. Record actual
  # boot properties; never conceal a small heap/OOM by removing the 10k corpus.
  adb shell getprop dalvik.vm.heapgrowthlimit > ci-stability/boot-heap.txt || exit 1
  adb shell getprop dalvik.vm.heapsize >> ci-stability/boot-heap.txt || exit 1
  # Finish SystemUI/keyguard startup before Gradle competes for the runner CPU.
  sleep 20
  adb logcat -d -s ActivityManager:I ActivityTaskManager:I AndroidRuntime:E > ci-stability/boot-logcat.txt || exit 1
  adb shell dumpsys activity lastanr > ci-stability/boot-last-anr.txt || exit 1
  [[ -s ci-stability/boot-logcat.txt && -s ci-stability/boot-last-anr.txt ]] && command -v grep >/dev/null || { echo 'Boot health evidence unavailable' >&2; exit 1; }
  # A Google app can log its own "ANR in" text using the ActivityManager tag.
  # Use the platform's recorded ANR state, retaining both sources for inspection.
  if ! grep -Fq '<no ANR has occurred since boot>' ci-stability/boot-last-anr.txt || grep -Fq 'FATAL EXCEPTION' ci-stability/boot-logcat.txt; then
    adb exec-out screencap -p > ci-stability/boot-failure.png || true
    echo 'Emulator boot ANR/crash detected before app tests; logs retained, no dialog dismissed and no tests claimed passed' >&2
    exit 1
  fi
fi
if [[ "${V100_CHECK_BOOT:-false}" == true ]]; then
  # Only after the original boot-health gate. Keep the original logs/ANR result.
  bash "$(dirname "${BASH_SOURCE[0]}")/ci-emulator-heap.sh" || exit 1
  sleep 20
  IFS= read -r new_boot_since < ci-stability/heap-new-boot-since.txt
  adb logcat -d -T "$new_boot_since.000" -s ActivityManager:I ActivityTaskManager:I AndroidRuntime:E > ci-stability/post-heap-logcat.txt || exit 1
  adb shell dumpsys activity lastanr > ci-stability/post-heap-last-anr.txt || exit 1
  [[ -s ci-stability/post-heap-logcat.txt && -s ci-stability/post-heap-last-anr.txt ]] || exit 1
  grep -Fq '<no ANR has occurred since boot>' ci-stability/post-heap-last-anr.txt || exit 1
  if grep -Fq 'FATAL EXCEPTION' ci-stability/post-heap-logcat.txt; then exit 1; fi
fi
device_test_status=0
mkdir -p ci-stability
# Preserve the exact in-flight test/lifecycle even if a platform IPC call never
# returns. A timeout is a failing run; it cannot produce successful evidence.
adb logcat -v threadtime -s TestRunner:I LifecycleMonitor:D ActivityTaskManager:I AndroidRuntime:E '*:S' > ci-stability/native-live-logcat.txt 2>&1 &
native_log_pid=$!
timeout --signal=TERM --kill-after=30s 20m ./gradlew --no-daemon --build-cache --init-script scripts/ci-test-execution.gradle --stacktrace -Pandroid.testInstrumentationRunnerArguments.ciExpectedHeapMiB=256 :app:connectedDebugAndroidTest || device_test_status=$?
kill "$native_log_pid" 2>/dev/null || true
if [[ "${V08_STABILITY_PROBES:-false}" == true ]]; then
  # Independently collect process evidence even after a native failure. Never
  # replace its failing exit status with a successful probe or publish the run.
  probe_status=0
  python3 scripts/run-stability-probes.py || probe_status=$?
  if [[ "$device_test_status" == 0 ]]; then device_test_status=$probe_status; fi
fi
if [[ "${V200_PACKAGE_PROBES:-false}" == true ]]; then
  package_status=0
  args=(--directory delivered-packages --aapt "$ANDROID_HOME/build-tools/35.0.0/aapt" --apksigner "$ANDROID_HOME/build-tools/35.0.0/apksigner")
  if [[ -f delivered-packages/old-fixed.apk ]]; then
    args+=(--old-apk delivered-packages/old-fixed.apk)
  fi
  python3 scripts/run-v200-package-probes.py "${args[@]}" || package_status=$?
  if [[ "$device_test_status" == 0 ]]; then device_test_status=$package_status; fi
fi
if [[ "$device_test_status" == 0 && "${V200_MAIN_PROBES:-false}" == true ]]; then
  # Full unfiltered suite has already succeeded; this additional real-form journey
  # and force-stop reopen retain its raw XML and do not replace/shorten that suite.
  python3 scripts/run-entry-experience.py || device_test_status=$?
fi
if [[ "$device_test_status" == 0 && "${V200_MAIN_PROBES:-false}" == true ]]; then
  python3 scripts/run-v210-visual.py || device_test_status=$?
fi
if [[ "$device_test_status" == 0 && "${V200_MAIN_PROBES:-false}" == true ]]; then
  python3 scripts/run-v210-consistency.py || device_test_status=$?
fi
mkdir -p ci-screenshots
mkdir -p ci-metrics
if command -v adb >/dev/null; then
  # Original consistency PNGs use the same permission-free MediaStore transport
  # as native metrics, surviving UTP's uninstall even when a regression fails.
  mkdir -p ci-v200
  adb pull /sdcard/Download/TradeReview-v210-consistency ci-v200/consistency-native >/dev/null 2>&1 || true
  adb pull /sdcard/Download/TradeReview-v04-ui ci-device-screenshots >/dev/null 2>&1 || true
  adb pull /sdcard/Download/TradeReview-v05-ui ci-v05-device-screenshots >/dev/null 2>&1 || true
  adb pull /sdcard/Download/TradeReview-v06-ui ci-v06-device-screenshots >/dev/null 2>&1 || true
  adb pull /sdcard/Download/TradeReview-v07-ui ci-v07-device-screenshots >/dev/null 2>&1 || true
  adb pull /sdcard/Download/TradeReview-v08-metrics ci-metrics >/dev/null 2>&1 || true
fi
while IFS= read -r screenshot_path; do
  cp "$screenshot_path" "ci-screenshots/${screenshot_path##*/}" || exit 1
done < <(if command -v rg >/dev/null; then rg --files --no-ignore app/build/outputs ci-v200 ci-device-screenshots ci-v05-device-screenshots ci-v06-device-screenshots ci-v07-device-screenshots -g '*.png' 2>/dev/null || true; else find app/build/outputs ci-v200 ci-device-screenshots ci-v05-device-screenshots ci-v06-device-screenshots ci-v07-device-screenshots -type f -name '*.png' 2>/dev/null; fi)
while IFS= read -r entry_screenshot; do
  cp "$entry_screenshot" "ci-screenshots/${entry_screenshot##*/}" || exit 1
done < <(find ci-v200/entry-experience -type f -name '*.png' 2>/dev/null)
if [[ -s ci-stability/26-v08-process-restart-font.png ]]; then
  cp ci-stability/26-v08-process-restart-font.png ci-screenshots/
fi
if [[ "$device_test_status" == 0 ]]; then
  for screenshot in 01-home 02-calendar-heatmap 03-daily-details 04-combined-filters 05-statistics 06-pnl-chart 07-dark-settings 08-dark-red-calendar 09-professional-auto 10-risk-confirmation 11-risk-rules 12-r-analytics 13-professional-dark 14-trade-evidence 15-week-report 16-month-report 17-review-goals 18-full-zip-preview 19-ocr-recognition 20-ocr-confirmation 21-smart-insights 22-preflight-checklist; do
    if [[ ! -s "ci-screenshots/$screenshot.png" ]]; then
      printf 'Required emulator screenshot missing: %s; publication refused\n' "$screenshot" >&2
      exit 1
    fi
  done
  if [[ "${V200_PACKAGE_PROBES:-false}" == true ]]; then
    for prefix in {27..32} {40..55}; do
      compgen -G "ci-screenshots/${prefix}-*.png" >/dev/null || { echo "Missing V2.0 screenshot $prefix" >&2; exit 1; }
    done
    for prefix in {70..77}; do
      compgen -G "ci-screenshots/${prefix}-*.png" >/dev/null || { echo "Missing actual entry screenshot $prefix" >&2; exit 1; }
    done
  fi
fi
exit "$device_test_status"
