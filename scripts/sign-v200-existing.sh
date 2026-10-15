#!/usr/bin/env bash
# No Gradle, arbitrary input path, PR, or replacement identity is allowed here.
set -euo pipefail
python3 scripts/ci-sign-original.py
