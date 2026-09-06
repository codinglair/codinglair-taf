# syntax=docker/dockerfile:1.7

ARG BASE_IMAGE=docker.io/library/ubuntu@sha256:1e0a86e57d247923571b75e0aaf48a1449cf8c543d51fb3e07a4a7d7bfa79316
ARG BUILDER_IMAGE=docker.io/library/python@sha256:781449467ffb6f04218f09b1ecdcdc7d22b289ee5da9ec498b024e24ad7a6db7
FROM ${BUILDER_IMAGE} AS downloader

ARG ANDROID_SDK_LICENSE_ACCEPTED=false
RUN test "${ANDROID_SDK_LICENSE_ACCEPTED}" = "true"

ARG EMULATOR_URL=https://dl.google.com/android/repository/emulator-linux_x64-15917651.zip
ARG EMULATOR_SHA256=95771e0ae431897b2a4bd2d97fa095f29a8b0624a7b216baf529f9306161c266
ARG SYSTEM_IMAGE_URL=https://dl.google.com/android/repository/sys-img/google_apis/x86_64-34_r14.zip
ARG SYSTEM_IMAGE_SHA256=783a40134baf4f3012d4464fbe1571b1612a0dbd2e7a44d14bd8328923443833
ARG PLATFORM_TOOLS_URL=https://dl.google.com/android/repository/platform-tools_r37.0.1-linux.zip
ARG PLATFORM_TOOLS_SHA256=d230f13842f60f782a8645f9c813f8f845bf36089ea7289f28c48f17979313f1

RUN python - <<'PY'
import hashlib
import os
import shutil
import tempfile
import urllib.request
import zipfile

sdk = "/out/sdk"
system_images = f"{sdk}/system-images/android-34/google_apis"
os.makedirs(system_images, exist_ok=True)

artifacts = (
    ("EMULATOR_URL", "EMULATOR_SHA256", sdk),
    ("PLATFORM_TOOLS_URL", "PLATFORM_TOOLS_SHA256", sdk),
    ("SYSTEM_IMAGE_URL", "SYSTEM_IMAGE_SHA256", system_images),
)
for url_name, checksum_name, destination in artifacts:
    archive = tempfile.NamedTemporaryFile(prefix="android-", suffix=".zip", delete=False)
    archive.close()
    try:
        with urllib.request.urlopen(os.environ[url_name]) as response, open(archive.name, "wb") as output:
            shutil.copyfileobj(response, output)
        with open(archive.name, "rb") as downloaded:
            actual = hashlib.file_digest(downloaded, "sha256").hexdigest()
        expected = os.environ[checksum_name]
        if actual != expected:
            raise RuntimeError(f"{url_name} checksum mismatch: expected {expected}, got {actual}")
        with zipfile.ZipFile(archive.name) as zipped:
            zipped.extractall(destination)
    finally:
        os.unlink(archive.name)

notices = (
    (f"{sdk}/emulator/NOTICE.txt", f"{sdk}/EMULATOR-NOTICE.txt"),
    (f"{sdk}/platform-tools/NOTICE.txt", f"{sdk}/PLATFORM-TOOLS-NOTICE.txt"),
    (f"{system_images}/x86_64/NOTICE.txt", f"{sdk}/SYSTEM-IMAGE-NOTICE.txt"),
)
for source, destination in notices:
    if not os.path.isfile(source):
        raise RuntimeError(f"Required notice is missing: {source}")
    shutil.copyfile(source, destination)

if not os.path.isdir(f"{system_images}/x86_64"):
    raise RuntimeError("System image did not preserve the required x86_64 directory layout")
PY

FROM ${BASE_IMAGE} AS runtime

ARG DEBIAN_FRONTEND=noninteractive
ARG UBUNTU_SNAPSHOT=20260810T000000Z
COPY --from=downloader /etc/ssl/certs/ca-certificates.crt /etc/ssl/certs/ca-certificates.crt
RUN sed -i "s#http://archive.ubuntu.com/ubuntu/#http://snapshot.ubuntu.com/ubuntu/${UBUNTU_SNAPSHOT}/#g; s#http://security.ubuntu.com/ubuntu/#http://snapshot.ubuntu.com/ubuntu/${UBUNTU_SNAPSHOT}/#g" /etc/apt/sources.list.d/ubuntu.sources \
    && apt-get -o Acquire::Retries=5 -o Acquire::http::Timeout=30 -o Acquire::https::Timeout=30 update \
    && apt-get -o Acquire::Retries=5 -o Acquire::http::Timeout=30 -o Acquire::https::Timeout=30 install --yes --no-install-recommends \
      ca-certificates libc6 libdbus-1-3 libfontconfig1 libgcc-s1 libgl1 libnss3 \
      libpulse0 libstdc++6 libx11-6 libxcb1 libxcomposite1 libxcursor1 libxdamage1 \
      libxext6 libxfixes3 libxi6 libxkbfile1 socat zlib1g \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 emulator \
    && useradd --uid 10001 --gid 10001 --create-home --home-dir /home/emulator emulator \
    && mkdir -p /android/sdk /home/emulator/.android/avd /data \
    && chown -R 10001:10001 /android /home/emulator /data

COPY --from=downloader --chown=10001:10001 /out/sdk/ /android/sdk/
COPY --chown=10001:10001 entrypoint.sh /android/entrypoint.sh
COPY --chown=10001:10001 avd/ /home/emulator/.android/avd/
RUN chmod 0555 /android/entrypoint.sh \
    && find /android/sdk/emulator /android/sdk/platform-tools -type f -exec chmod 0555 {} +

ENV ANDROID_HOME=/android/sdk \
    ANDROID_SDK_ROOT=/android/sdk \
    ANDROID_AVD_HOME=/home/emulator/.android/avd \
    HOME=/home/emulator \
    PATH=/android/sdk/emulator:/android/sdk/platform-tools:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin \
    QT_QPA_PLATFORM=offscreen

LABEL org.opencontainers.image.source="https://github.com/google/android-emulator-container-scripts/tree/0654f694b46794fae4b178f1e1a17cb60c5d2d34" \
      org.opencontainers.image.licenses="Android-SDK-License AND Apache-2.0" \
      com.codinglair.taf.android.api="34" \
      com.codinglair.taf.emulator.version="37.1.11"

USER 10001:10001
WORKDIR /android
EXPOSE 5555
HEALTHCHECK --interval=10s --timeout=10s --start-period=180s --retries=30 \
  CMD adb connect 127.0.0.1:5557 >/dev/null 2>&1 && test "$(adb -s 127.0.0.1:5557 get-state)" = device && test "$(adb -s 127.0.0.1:5557 shell getprop sys.boot_completed | tr -d '\r')" = 1
ENTRYPOINT ["/android/entrypoint.sh"]
