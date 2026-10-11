#!/usr/bin/env bash
set -euo pipefail
# Runs on GitHub's runner, outside the Android project to avoid requiring a
# local SDK or resolving the Android plugins before the wrapper is generated.
task_root="$(cd -- "$(dirname -- "$0")/.." && pwd)"
bootstrap_dir="$(mktemp -d)"
trap 'rm -rf "$bootstrap_dir"' EXIT
printf 'rootProject.name = "wrapper-bootstrap"\n' > "$bootstrap_dir/settings.gradle.kts"
printf '\n' > "$bootstrap_dir/build.gradle.kts"
distribution_sha="$(curl --fail --silent --show-error --location --max-time 60 https://services.gradle.org/distributions/gradle-8.11.1-bin.zip.sha256)"
if [[ ! "$distribution_sha" =~ ^[[:xdigit:]]{64}$ ]]; then
    echo 'Invalid official Gradle distribution checksum' >&2
    exit 1
fi
gradle --no-daemon -p "$bootstrap_dir" wrapper --gradle-version 8.11.1 --distribution-type bin --gradle-distribution-sha256-sum "$distribution_sha"
mkdir -p "$task_root/gradle/wrapper"
cp "$bootstrap_dir/gradlew" "$task_root/gradlew"
cp "$bootstrap_dir/gradlew.bat" "$task_root/gradlew.bat"
cp "$bootstrap_dir/gradle/wrapper/gradle-wrapper.jar" "$task_root/gradle/wrapper/gradle-wrapper.jar"
cp "$bootstrap_dir/gradle/wrapper/gradle-wrapper.properties" "$task_root/gradle/wrapper/gradle-wrapper.properties"
chmod +x "$task_root/gradlew"
