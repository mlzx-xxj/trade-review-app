#!/usr/bin/env bash
# Only the repository owner's existing V0.3 identity; never generate a key.
set -euo pipefail
# Issue #12: old V0.3 private key is lost; this historical signing route is retired.
printf '%s\n' 'Historical V1 signing disabled. Use the reviewed V2 protected-main pipeline and V200-PHONE-SIGNING.md.' >&2
exit 1
configured=$(python3 scripts/signing-config-state.py)
mkdir -p dist signing-fixture
if [[ "$configured" == true ]]; then
  pin=$(tr -d '\r\n' < docs/V03-CERTIFICATE-SHA256.txt)
  [[ "${TRADE_REVIEW_UPGRADE_CERT_SHA256:-}" == "$pin" ]] || { echo 'Original public certificate Variable is absent or mismatched' >&2; exit 1; }
  umask 077
  key_path="$RUNNER_TEMP/trade-review-existing.keystore"
  trap 'rm -f "$key_path"' EXIT
  printf '%s' "$EXISTING_KEY_BASE64" | base64 --decode > "$key_path"
  signer="$ANDROID_HOME/build-tools/35.0.0/apksigner"
  "$signer" sign --ks "$key_path" --ks-key-alias "$EXISTING_KEY_ALIAS" --ks-pass env:EXISTING_STORE_PASSWORD --key-pass env:EXISTING_KEY_PASSWORD --out dist/TradeReview-v1.0-fixed-candidate.apk app/build/outputs/apk/release/app-release-unsigned.apk
  python3 scripts/v100-identity.py --apk dist/TradeReview-v1.0-fixed-candidate.apk --kind release --directory dist --aapt "$ANDROID_HOME/build-tools/35.0.0/aapt" --apksigner "$signer" --source "$SOURCE_COMMIT"
  # Separate, synthetic emulator-only fixture. Never deliver this test APK as an app.
  "$signer" sign --ks "$key_path" --ks-key-alias "$EXISTING_KEY_ALIAS" --ks-pass env:EXISTING_STORE_PASSWORD --key-pass env:EXISTING_KEY_PASSWORD --out signing-fixture/original-signed-test.apk app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
  "$signer" verify --print-certs signing-fixture/original-signed-test.apk > "$RUNNER_TEMP/fixture-public-certificate.txt"
  python3 - "$RUNNER_TEMP/fixture-public-certificate.txt" "$pin" <<'PYPIN'
import re,sys
certs=re.findall(r'^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]{64})$',open(sys.argv[1]).read(),re.M)
assert certs==[sys.argv[2]], 'Upgrade fixture does not use original identity'
PYPIN
fi
python3 - "$configured" <<'PYSTATE'
import json,os,sys
from pathlib import Path
Path('dist/SIGNING-STATUS.json').write_text(json.dumps(dict(sourceCommit=os.environ['SOURCE_COMMIT'],configured=sys.argv[1]=='true',originalPinVerified=sys.argv[1]=='true',realPhoneAcceptance='not-confirmed'),indent=2)+'\n')
PYSTATE
printf 'configured=%s\n' "$configured" >> "$GITHUB_OUTPUT"
