#!/bin/sh
# Derived from google/android-emulator-container-scripts at commit
# 0654f694b46794fae4b178f1e1a17cb60c5d2d34 (Apache-2.0).
set -eu

test -c /dev/kvm || { echo >&2 'KVM_UNAVAILABLE: /dev/kvm is required.'; exit 78; }
test -r /dev/kvm && test -w /dev/kvm || {
  echo >&2 'KVM_PERMISSION_DENIED: runtime uid 10001 cannot read/write /dev/kvm.'
  exit 77
}

rm -rf /data/taf-api34.avd
cp -R "${ANDROID_AVD_HOME}/taf-api34.avd" /data/taf-api34.avd
sed -i 's#^path=.*#path=/data/taf-api34.avd#' "${ANDROID_AVD_HOME}/taf-api34.ini"
rm -f /data/taf-api34.avd/*.lock

adb start-server
socat TCP-LISTEN:5555,reuseaddr,fork TCP:127.0.0.1:5557 &

exec emulator -avd taf-api34 -ports 5556,5557 -no-window -noaudio \
  -no-boot-anim -no-snapshot -wipe-data -gpu swiftshader_indirect \
  -skip-adb-auth -no-metrics -feature -Vulkan
