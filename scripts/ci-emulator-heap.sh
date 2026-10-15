#!/usr/bin/env bash
# Supported userdebug AVD provisioning, never a phone or production App change.
set -euo pipefail
[[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r\n')" == 1 ]] || { echo 'Heap provisioning requires a disposable emulator' >&2; exit 1; }
mkdir -p ci-stability
adb root > ci-stability/heap-adbd.txt
adb wait-for-device
[[ "$(adb shell id -u | tr -d '\r\n')" == 0 ]] || { echo 'This CI image must support root ADB for VM provisioning' >&2; exit 1; }
# -prop only accepts qemu.* on current emulators. Set actual ART properties before
# restarting Zygote; regular App processes still run under their normal UID.
adb shell stop
adb shell setprop dalvik.vm.heapgrowthlimit 256m
adb shell setprop dalvik.vm.heapsize 512m
adb shell setprop sys.boot_completed 0
adb shell start
for ((attempt=0; attempt<120; attempt++)); do
  if [[ "$(adb shell getprop sys.boot_completed | tr -d '\r\n')" == 1 ]]; then break; fi
  sleep 1
done
[[ "$(adb shell getprop sys.boot_completed | tr -d '\r\n')" == 1 ]] || { echo 'VM restart did not complete; tests not run' >&2; exit 1; }
adb shell wm dismiss-keyguard
adb shell getprop dalvik.vm.heapgrowthlimit > ci-stability/configured-heap.txt
adb shell getprop dalvik.vm.heapsize >> ci-stability/configured-heap.txt
[[ "$(adb shell getprop dalvik.vm.heapgrowthlimit | tr -d '\r\n')" == 256m ]] || exit 1
[[ "$(adb shell getprop dalvik.vm.heapsize | tr -d '\r\n')" == 512m ]] || exit 1
