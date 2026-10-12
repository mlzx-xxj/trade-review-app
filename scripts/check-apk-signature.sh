#!/usr/bin/env bash
set -euo pipefail
apk_path=${1:?APK path required}
configured=${TRADE_REVIEW_EXISTING_KEY_CONFIGURED:-false}
[[ "$configured" == true || "$configured" == false ]] || { printf 'Invalid signing configuration state\n' >&2; exit 1; }
expected_cert=${TRADE_REVIEW_UPGRADE_CERT_SHA256:-}
expected_cert=$(printf '%s' "$expected_cert" | tr -d ': ' | tr '[:upper:]' '[:lower:]')
verification=$("${ANDROID_HOME:?}/build-tools/35.0.0/apksigner" verify --print-certs "$apk_path")
mapfile -t signer_certs < <(printf '%s\n' "$verification" | sed -n 's/^Signer #[0-9][0-9]* certificate SHA-256 digest: //p' | tr '[:upper:]' '[:lower:]')
[[ ${#signer_certs[@]} == 1 ]] || { printf 'Exactly one APK signer is required; publication refused\n' >&2; exit 1; }
actual_cert=${signer_certs[0]}
[[ "$actual_cert" =~ ^[0-9a-f]{64}$ ]] || exit 1
printf 'APK certificate SHA-256: %s\n' "$actual_cert"
mkdir -p dist
if [[ "$configured" == true ]]; then
  pin_path="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/docs/V03-CERTIFICATE-SHA256.txt"
  committed_cert=$(tr -d ':[:space:]' < "$pin_path" | tr '[:upper:]' '[:lower:]')
  if [[ ! "$expected_cert" =~ ^[0-9a-f]{64}$ || ! "$committed_cert" =~ ^[0-9a-f]{64}$ || "$actual_cert" != "$expected_cert" || "$actual_cert" != "$committed_cert" ]]; then
    printf '%s\n' 'Fixed key certificate pin is missing or mismatched. Upgrade publication refused.' >&2
    exit 1
  fi
fi
printf 'versionName=0.8.0\nversionCode=8\napplicationId=com.tradereview.app\nsourceCommit=%s\ncertificateSha256=%s\nGitHubActionsSecretsConfigured=%s\n' "${GITHUB_SHA:-unknown}" "$actual_cert" "$configured" > dist/SIGNING.txt
if [[ "$configured" == true ]]; then
  printf 'upgrade_verified=true\n' >> "${GITHUB_OUTPUT:?}"
  cp "$apk_path" dist/TradeReview-v0.8-stable.apk
  printf 'upgradeVerified=true\nsigningOrigin=GitHub Actions Secrets\nFixed V0.3 signer verified against both repository variable and committed V0.3 certificate pin. Future upgrades require this same private key and a larger versionCode. Back up local data before changing installation identity; no uninstall or phone data deletion is performed by CI.\n' >> dist/SIGNING.txt
else
  printf 'upgrade_verified=false\n' >> "${GITHUB_OUTPUT:?}"
  cp "$apk_path" dist/TradeReview-v0.8-test.apk
  printf 'upgradeVerified=false\nsigningOrigin=temporary runner Debug key\nTEST ONLY: no fixed signing key configured. This temporary key does not establish a sustainable V0.8 upgrade identity. Back up local data before changing installation identity; no uninstall or phone data deletion is performed by CI.\n' >> dist/SIGNING.txt
  printf '%s\n' '::warning::Fixed V0.3 key not configured. Temporary test APK only; long-term in-place upgrades are not guaranteed.'
fi
(cd dist && sha256sum ./*.apk > SHA256SUMS.txt)
