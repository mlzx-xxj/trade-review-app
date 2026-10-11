#!/usr/bin/env bash
# Run only on the repository owner's trusted computer. Never enable shell tracing.
set -euo pipefail
signing_dir=${1:?Usage: bash scripts/configure-v03-signing.sh /private/path/trade-review-v03}
repo=mlzx-xxj/trade-review-app
for name in trade-review-v03.p12 keystore-base64.txt keystore-password.txt key-alias.txt certificate-sha256.txt; do
  test -s "$signing_dir/$name" || { printf 'Missing signing file: %s\n' "$name" >&2; exit 1; }
done
certificate=$(tr -d ':[:space:]' < "$signing_dir/certificate-sha256.txt" | tr '[:upper:]' '[:lower:]')
[[ "$certificate" =~ ^[0-9a-f]{64}$ ]] || { printf 'Invalid public certificate fingerprint\n' >&2; exit 1; }
# Check permissions before writing anything; the Codex integration returned 403 here.
gh api "repos/$repo/actions/secrets/public-key" > /dev/null
gh variable set TRADE_REVIEW_UPGRADE_CERT_SHA256 --repo "$repo" --body "$certificate"
gh secret set TRADE_REVIEW_KEYSTORE_PASSWORD --repo "$repo" --body-file "$signing_dir/keystore-password.txt"
gh secret set TRADE_REVIEW_KEY_PASSWORD --repo "$repo" --body-file "$signing_dir/keystore-password.txt"
gh secret set TRADE_REVIEW_KEY_ALIAS --repo "$repo" --body-file "$signing_dir/key-alias.txt"
# Enable fixed signing last, after the pin and other credentials have been written.
gh secret set TRADE_REVIEW_KEYSTORE_BASE64 --repo "$repo" --body-file "$signing_dir/keystore-base64.txt"
printf 'Signing Secrets and certificate pin configured. Run Android CI and verify SIGNING.txt before distributing its APK.\n'
