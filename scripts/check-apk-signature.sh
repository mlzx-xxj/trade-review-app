#!/usr/bin/env bash
set -euo pipefail
apk_path=${1:?APK path required}
expected_cert=${TRADE_REVIEW_UPGRADE_CERT_SHA256:-8272cb328cfdbc2e585ce88b71c1425f18abc5a57d6082bc69b8c305a154eb82}
expected_cert=$(printf '%s' "$expected_cert" | tr -d ': ' | tr '[:upper:]' '[:lower:]')
actual_cert=$("${ANDROID_HOME:?}/build-tools/35.0.0/apksigner" verify --print-certs "$apk_path" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' | tr '[:upper:]' '[:lower:]')
test -n "$actual_cert"
printf 'APK certificate SHA-256: %s\n' "$actual_cert"
mkdir -p dist
printf 'versionName=0.2.0\nversionCode=2\ncertificateSha256=%s\n' "$actual_cert" > dist/SIGNING.txt
if [[ ${TRADE_REVIEW_EXISTING_KEY_CONFIGURED:-false} == true ]]; then
  if [[ "$actual_cert" != "$expected_cert" ]]; then
    printf '%s\n' 'Existing key does not match the selected V0.1 certificate. Upgrade publication refused.' >&2
    exit 1
  fi
  printf 'upgrade_verified=true\n' >> "${GITHUB_OUTPUT:?}"
  cp "$apk_path" dist/TradeReview-v0.2-upgrade.apk
  printf 'Same selected V0.1 signer verified. Check installed APK uses this certificate before updating.\n' >> dist/SIGNING.txt
else
  printf 'upgrade_verified=false\n' >> "${GITHUB_OUTPUT:?}"
  cp "$apk_path" dist/TradeReview-v0.2-test.apk
  printf 'TEST ONLY: existing V0.1 private key unavailable. This APK is for a clean test device. Do not uninstall an existing app or use this APK as an upgrade.\n' >> dist/SIGNING.txt
  printf '%s\n' '::warning::V0.1 private key not configured. Build/testing continues; in-place upgrade publication is blocked.'
fi
(cd dist && sha256sum ./*.apk > SHA256SUMS.txt)
