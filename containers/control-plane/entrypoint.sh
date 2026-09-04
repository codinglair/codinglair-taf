#!/usr/bin/env bash
set -euo pipefail

exec java -cp '/opt/taf/app.jar:/opt/taf/lib/*' \
  com.codinglair.taf.mcp.http.TafMcpHttpApplication "$@"
