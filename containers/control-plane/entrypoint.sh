#!/usr/bin/env bash
set -euo pipefail

workspace="${TAF_MCP_APPLICATION_WORKSPACE_ROOT:-/workspace}"
state_dir="${TAF_MCP_APPLICATION_STATE_ROOT:-/var/lib/taf-mcp}"

[[ "$workspace" == /workspace || "$workspace" == /workspace/* ]] || {
  printf 'taf-control-plane: workspace must be within /workspace\n' >&2
  exit 64
}
[[ "$state_dir" == /var/lib/taf-mcp || "$state_dir" == /var/lib/taf-mcp/* ]] || {
  printf 'taf-control-plane: state directory must be within /var/lib/taf-mcp\n' >&2
  exit 64
}

exec java -cp '/opt/taf/app.jar:/opt/taf/lib/*' \
  com.codinglair.taf.mcp.http.TafMcpHttpApplication "$@" \
  "--taf.mcp.application.workspace-root=$workspace" \
  "--taf.mcp.application.state-root=$state_dir"
