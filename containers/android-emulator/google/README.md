# Controlled Google Android emulator candidate

This directory is the MOB-003 Candidate A build definition. It uses Google
`android-emulator-container-scripts` commit
`0654f694b46794fae4b178f1e1a17cb60c5d2d34` as the authoritative design source,
but keeps Python and SDK tooling off the host and out of the runtime image.

The candidate is Android 14/API 34 Google APIs x86_64 revision 14 with stable
emulator 37.1.11. All downloads and the linux/amd64 Ubuntu base are immutable.
Read the Android SDK terms before explicitly accepting them:

```powershell
.\containers\android-emulator\google\build.ps1 -AcceptAndroidSdkLicense -Clean
```

```sh
ANDROID_SDK_LICENSE_ACCEPTED=true ./containers/android-emulator/google/build.sh
```

The image is ephemeral local/CI qualification state and must not be pushed,
exported, uploaded, transferred, cached remotely, or delivered to consumers.
MOB-003 qualification is complete under approval `MOB-003-GOV-001`; the accepted
GitHub evidence is run `90050184653`. The production `../compose.yaml` remains
separate and is not a consumer or production adoption path for this image.

This recipe is only a Codinglair TAF development health checkpoint. Framework
consumers provide their own physical devices, emulators, private Appium grids,
or cloud device farms through the existing Appium configuration contract.

After scanning the exact candidate and builder digests into the SARIF locations
under `target/mob-003/`, complete and verify the local evidence with:

```powershell
.\containers\android-emulator\google\complete-evidence.ps1
.\containers\android-emulator\google\verify.ps1
.\containers\android-emulator\google\verify-compose.ps1
.\containers\android-emulator\google\qualify.ps1 -Runs 2
.\containers\android-emulator\google\qualify.ps1 -Runs 1 -ControlledFailure
```

The evidence command preserves reviewed license dispositions. It will not allow
an all-`ALLOWED` inventory to pass without `-ApprovalReference '<decision>'`.
The accepted evidence has SEC-003 result `PASS` and is bound to the restricted
reference-qualification model by `MOB-003-GOV-001`.

The verification scripts and their output under `target/mob-003/` are the local evidence source.
