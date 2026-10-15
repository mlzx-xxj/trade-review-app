#!/usr/bin/env bash
# Historical filename; only the owner-approved permanent V2 identity from Issue #12.
# No Gradle, arbitrary input path, PR, or key generation is allowed here.
set -euo pipefail
python3 scripts/ci-sign-original.py
