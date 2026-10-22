#!/bin/sh
set -eu

work=/tmp/qe-132-001
rm -rf "$work"
mkdir -p "$work/scaffold-workspace/sut" "$work/scaffold-link-target"
printf '%s\n' '<project><modelVersion>4.0.0</modelVersion><groupId>qe</groupId><artifactId>sut</artifactId><version>1</version></project>' \
  > "$work/scaffold-workspace/sut/pom.xml"
ln -s "$work/scaffold-link-target" "$work/scaffold-workspace/linked-automation"

current_jars=$(find /workspace/target/staging-repository/com/codinglair/taf -type f \( \
  -name 'codinglair-taf-mcp-1.3.1.jar' -o \
  -name 'taf-mcp-security-1.3.1.jar' -o \
  -name 'taf-mcp-tools-1.3.1.jar' -o \
  -name 'taf-mcp-transport-stdio-1.3.1.jar' \) | sort | paste -sd: -)
printf '%s:%s\n' "$current_jars" '/opt/taf/stdio/lib/*' > /evidence/server-classpath.txt
before=$(sha256sum "$work/scaffold-workspace/sut/pom.xml" | cut -d' ' -f1)
set +e
java -cp /workspace/target/qe-132-001/client-classes Qe132001McpClient \
  /evidence/server-classpath.txt \
  "$work/scaffold-workspace" \
  "$work/scaffold-workspace/linked-automation" \
  "$work/scaffold-workspace/sut" \
  false > /evidence/response.log 2>&1
client_status=$?
set -e
cp "$work/scaffold-workspace/mcp-stderr.log" /evidence/mcp-stderr.log 2>/dev/null || true
test "$client_status" -eq 0
after=$(sha256sum "$work/scaffold-workspace/sut/pom.xml" | cut -d' ' -f1)

test "$before" = "$after"
grep -F 'filesystem-link automation ancestor is not allowed' /evidence/response.log >/dev/null
printf 'environment=linux-container; protectedPomUnchanged=true\n' > /evidence/environment.txt
