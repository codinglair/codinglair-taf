#!/bin/sh
set -eu

if [ "${ANDROID_SDK_LICENSE_ACCEPTED:-false}" != true ]; then
  echo >&2 'Read https://developer.android.com/studio/terms and set ANDROID_SDK_LICENSE_ACCEPTED=true.'
  exit 64
fi

image="${TAF_ANDROID_IMAGE:-codinglair-taf/android-emulator:mob-003-api34}"
repository_root=$(CDPATH= cd -- "$(dirname "$0")/../../.." && pwd)
mkdir -p "$repository_root/target/mob-003"
docker buildx build --platform linux/amd64 --load --provenance=mode=max \
  --attest type=sbom,generator=docker.io/docker/buildkit-syft-scanner@sha256:187e1892a7752c9384c59aba9517dd8e40610b748c72773e87b63720514463c2 \
  --metadata-file "$repository_root/target/mob-003/build-metadata.json" \
  --build-arg ANDROID_SDK_LICENSE_ACCEPTED=true \
  --build-arg SOURCE_DATE_EPOCH=1786291200 \
  --tag "$image" "$(dirname "$0")"
docker image inspect "$image" --format '{{.Os}}/{{.Architecture}} {{.Id}}'
