# Android emulator and Appium environment

This Compose stack provisions the infrastructure consumed by `taf-mobile-appium`; the controller never owns containers or emulator authorization. It uses Android 14/API 34 on x86_64, a separate Appium 2 server, and container-network-only ADB. The emulator is attached only to the internal mobile network. Appium bridges that network to its loopback-only host port `4724` through a separate ingress network.

## Prerequisites

### Hardware Virtualization (Required)

The emulator requires hardware virtualization support. Enable nested virtualization:

**Windows with Docker Desktop:**

1. In Docker Desktop, go to Settings → General
2. Enable "Use WSL 2 based engine" if not already enabled
3. Restart Docker Desktop

**WSL2 nested virtualization setup:**

```powershell
# Run as Administrator
wmi-object -query 'root\virtualization\v2' | where-object {$_.HyperVisorEnabled -eq $false}

enable-wsl2nestedvirtualization

wsl --shutdown  # Then restart Docker Desktop
```

**Linux:**

```bash
# Verify KVM is available
ls -la /dev/kvm

# If missing, install QEMU/KVM (Debian/Ubuntu example):
sudo apt install qemu-kvm libvirt-daemon-system libvirt-daemon-system qemu-system-x86
sudo usermod -aG kvm $USER
```

### Resources

- at least 4 CPU cores and 6 GB free memory for reliable emulator startup
- Recommended: 8 CPU cores and 16 GB memory for faster emulation
- the committed Compose service allocates 2 GiB to `/dev/shm`; setting a `SHM_SIZE` environment
  variable does not change Docker shared memory and is rejected by `verify-stack.ps1`

### Troubleshooting

**Error: `no such file or directory: '/dev/kvm'`**

This error means hardware virtualization is not enabled or accessible.

**Option 1: Enable nested virtualization (Recommended)**

Follow the steps above to enable nested virtualization on WSL2.

**Option 2: Use software emulation (Slower but works without KVM)**

```bash
# Down the current stack
docker compose --file containers/android-emulator/compose.yaml down --remove-orphans

# Modify compose.yaml to remove the /dev/kvm device mount (see above)
# Add -no-kvm to EMULATOR_ADDITIONAL_ARGS if supported by your image version

# Rebuild and start
docker compose --file containers/android-emulator/compose.yaml build
docker compose --file containers/android-emulator/compose.yaml up
```

Note: Software emulation is significantly slower (1-2x CPU speed) compared to hardware-accelerated emulation (near-native speed).

Run `./verify-stack.ps1` before startup. Run `./smoke.ps1` for the complete gate: Compose readiness, Appium status, ADB connectivity, UiAutomator2 session creation/basic page-source interaction, application restoration, and container cleanup. The default readiness timeout is 360 seconds and can be changed with `-ReadyTimeoutSeconds`.

The equivalent manual lifecycle is:

```powershell
docker compose --file containers/android-emulator/compose.yaml up --detach --wait --wait-timeout 360
$env:TAF_ANDROID_APPIUM_URL = 'http://127.0.0.1:4724'
$env:TAF_ANDROID_DEVICE_NAME = 'taf-api34'
$env:TAF_ANDROID_DEVICE_ID = 'android-emulator:5555'
$env:TAF_ANDROID_APP_PACKAGE = 'com.android.settings'
$env:TAF_ANDROID_APP_ACTIVITY = '.Settings'
.\mvnw.cmd -pl codinglair-taf-runtime/taf-mobile-appium -am verify -Pandroid-emulator
docker compose --file containers/android-emulator/compose.yaml down --remove-orphans
```

`TAF_ANDROID_APPIUM_PORT` changes the loopback host port. `TAF_ANDROID_BOOT_START_PERIOD` changes the emulator health-check grace period. Do not put credentials in either variable or in Compose overrides.

The emulator intentionally uses its image-owned Android home without a persistent volume. Testing showed that mounting an Android-home cache prevents this pinned image from initializing reliably. Each container therefore starts from the pinned image state, and normal `down --remove-orphans` removes its writable device/app state.

Both images are pinned by tag and immutable digest. Upgrades require reviewing image provenance/licensing/security, resolving new digests, and rerunning the full smoke. The stack mounts no Docker socket and does not use privileged mode; the emulator receives only `/dev/kvm`. The emulator has an explicit exception from `no-new-privileges` because the pinned image's initialization fails under that restriction. Appium retains `no-new-privileges`, listens only on loopback, and the Compose network is internal.

If startup fails, collect bounded diagnostics with `docker compose --file containers/android-emulator/compose.yaml ps` and `docker compose --file containers/android-emulator/compose.yaml logs --tail 200`, then run `down --remove-orphans`. Do not publish raw device logs if the tested application may contain sensitive data.
