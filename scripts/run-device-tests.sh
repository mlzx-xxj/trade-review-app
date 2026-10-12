#!/usr/bin/env bash
# Run as ONE emulator-runner command so the status survives and reports are copied.
set -uo pipefail
device_test_status=0
./gradlew --no-daemon --no-build-cache --stacktrace :app:connectedDebugAndroidTest || device_test_status=$?
mkdir -p ci-screenshots
mkdir -p ci-metrics
if command -v adb >/dev/null; then
  adb pull /sdcard/Download/TradeReview-v04-ui ci-device-screenshots >/dev/null 2>&1 || true
  adb pull /sdcard/Download/TradeReview-v05-ui ci-v05-device-screenshots >/dev/null 2>&1 || true
  adb pull /sdcard/Download/TradeReview-v06-ui ci-v06-device-screenshots >/dev/null 2>&1 || true
  adb pull /sdcard/Download/TradeReview-v07-ui ci-v07-device-screenshots >/dev/null 2>&1 || true
  adb pull /sdcard/Download/TradeReview-v08-metrics ci-metrics >/dev/null 2>&1 || true
fi
while IFS= read -r screenshot_path; do
  cp "$screenshot_path" "ci-screenshots/${screenshot_path##*/}" || exit 1
done < <(if command -v rg >/dev/null; then rg --files --no-ignore app/build/outputs ci-device-screenshots ci-v05-device-screenshots ci-v06-device-screenshots ci-v07-device-screenshots -g '*.png' 2>/dev/null || true; else find app/build/outputs ci-device-screenshots ci-v05-device-screenshots ci-v06-device-screenshots ci-v07-device-screenshots -type f -name '*.png' 2>/dev/null; fi)
if [[ "$device_test_status" == 0 ]]; then
  for screenshot in 01-home 02-calendar-heatmap 03-daily-details 04-combined-filters 05-statistics 06-pnl-chart 07-dark-settings 08-dark-red-calendar 09-professional-auto 10-risk-confirmation 11-risk-rules 12-r-analytics 13-professional-dark 14-trade-evidence 15-week-report 16-month-report 17-review-goals 18-full-zip-preview 19-ocr-recognition 20-ocr-confirmation 21-smart-insights 22-preflight-checklist; do
    if [[ ! -s "ci-screenshots/$screenshot.png" ]]; then
      printf 'Required emulator screenshot missing: %s; publication refused\n' "$screenshot" >&2
      exit 1
    fi
  done
fi
exit "$device_test_status"
