#!/usr/bin/env bash
set -euo pipefail
target="$RUNNER_TEMP/actionlint-ci"
mkdir -p "$target"
curl --fail --location --silent --show-error --connect-timeout 10 --max-time 60 https://github.com/rhysd/actionlint/releases/download/v1.7.7/actionlint_1.7.7_linux_amd64.tar.gz --output "$target/actionlint.tar.gz"
printf '%s  %s\n' 023070a287cd8cccd71515fedc843f1985bf96c436b7effaecce67290e7e0757 "$target/actionlint.tar.gz" | sha256sum --check --status
tar -xzf "$target/actionlint.tar.gz" -C "$target" actionlint
"$target/actionlint" -color
