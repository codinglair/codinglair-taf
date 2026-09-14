#!/usr/bin/env bash
set -euo pipefail

mkdir -p target/aws-ci-source target/aws-ci-support
find . -path '*/target/surefire-reports/*' -type f \
  \( -name '*.xml' -o -name '*.txt' -o -name '*.json' -o -name '*.log' \) \
  -exec cp --parents '{}' target/aws-ci-source/ \;
javac -d target/aws-ci-support \
  build-support/ci/ArtifactSanitizer.java \
  build-support/ci/ArtifactLeakCheck.java
java -cp target/aws-ci-support ArtifactSanitizer target/aws-ci-source target/aws-ci-artifacts
java -cp target/aws-ci-support ArtifactLeakCheck target/aws-ci-artifacts
