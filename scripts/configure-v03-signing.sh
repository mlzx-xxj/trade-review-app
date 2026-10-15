#!/usr/bin/env bash
# Public repositories must not provision signing keys as Repository Secrets.
# Keep this entry point to fail safely before reading any private path or argument.
set -euo pipefail
printf '%s\n' 'Legacy Repository Secrets uploader disabled. The owner must configure the original identity in main-only Environment v03-signing-main; see docs/PUBLIC-CI-SECURITY.md. No private file was read.' >&2
exit 1
