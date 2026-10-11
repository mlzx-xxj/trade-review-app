#!/usr/bin/env bash
set -euo pipefail
apk_path=${1:?APK path required}
expected_cert=${TRADE_REVIEW_UPGRADE_CERT_SHA256:-}
expected_cert=$(printf '%s' "$expected_cert" | tr -d ': ' | tr '[:upper:]' '[:lower:]')
actual_cert=$("${ANDROID_HOME:?}/build-tools/35.0.0/apksigner" verify --print-certs "$apk_path" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' | tr '[:upper:]' '[:lower:]')
test -n "$actual_cert"
printf 'APK certificate SHA-256: %s\n' "$actual_cert"
mkdir -p dist
printf 'versionName=0.3.0\nversionCode=3\ncertificateSha256=%s\n' "$actual_cert" > dist/SIGNING.txt
if [[ ${TRADE_REVIEW_EXISTING_KEY_CONFIGURED:-false} == true ]]; then
  if [[ ! "$expected_cert" =~ ^[0-9a-f]{64}$ || "$actual_cert" != "$expected_cert" ]]; then
    printf '%s\n' 'Fixed key certificate pin is missing or mismatched. Upgrade publication refused.' >&2
    exit 1
  fi
  printf 'upgrade_verified=true\n' >> "${GITHUB_OUTPUT:?}"
  cp "$apk_path" dist/TradeReview-v0.3-stable.apk
  printf 'Fixed V0.3 signer verified against repository certificate pin. Future upgrades require this same private key and a larger versionCode. Old differently signed test versions need a manual uninstall first.\n' >> dist/SIGNING.txt
else
  printf 'upgrade_verified=false\n' >> "${GITHUB_OUTPUT:?}"
  cp "$apk_path" dist/TradeReview-v0.3-test.apk
  printf 'TEST ONLY: no fixed signing key configured. This temporary key does not establish a sustainable V0.3 upgrade identity. With your permission, old test versions may be manually uninstalled before a fresh install; uninstall deletes local data. Future data should be backed up before switching to a stable signer.\n' >> dist/SIGNING.txt
  printf '%s\n' '::warning::Fixed V0.3 key not configured. Temporary test APK only; long-term in-place upgrades are not guaranteed.'
fi
(cd dist && sha256sum ./*.apk > SHA256SUMS.txt)
