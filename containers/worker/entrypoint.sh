#!/usr/bin/env bash
set -euo pipefail

umask 0077
if (( $# > 0 )); then
  exec "$@"
fi

# The worker Java module is an invocation boundary, not a daemon. Keeping PID 1
# idle provides a health-checkable job pod that accepts an explicit governed command.
exec sleep infinity
