#!/usr/bin/env bash
# Run as ONE emulator-runner command so the status survives and reports are copied.
set -uo pipefail
device_test_status=0
./gradlew --no-daemon --no-build-cache --stacktrace :app:connectedDebugAndroidTest || device_test_status=$?
mkdir -p ci-screenshots
while IFS= read -r screenshot_path; do
  cp "$screenshot_path" "ci-screenshots/${screenshot_path##*/}" || exit 1
done < <(rg --files app/build/outputs -g '*.png' 2>/dev/null || true)
if [[ "$device_test_status" == 0 && ! -s ci-screenshots/07-dark-settings.png ]]; then
  printf 'Emulator tests finished but required screenshots were not exported; publication refused\n' >&2
  exit 1
fi
exit "$device_test_status"
