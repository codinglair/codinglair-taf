#!/usr/bin/env bash
set -euo pipefail

mode="${1:-help}"
maven=(./mvnw -B -ntp)

case "$mode" in
  unit-contract)
    "${maven[@]}" -pl codinglair-taf-runtime/taf-messaging-aws -am spotless:check verify
    ;;
  localstack)
    localstack_image="${TAF_LOCALSTACK_IMAGE:-localstack/localstack:4.8.1}"
    "${maven[@]}" -pl codinglair-taf-runtime/taf-messaging-aws -am spotless:check verify -Pcontainers \
      "-Dlocalstack.image=$localstack_image"
    ;;
  mcp-contract-leak)
    "${maven[@]}" \
      -pl taf-mcp-server/taf-mcp-contracts,taf-mcp-server/taf-mcp-resources,taf-mcp-server/taf-mcp-tools \
      -am verify -Pmcp-gate,security
    ;;
  consumer-smoke)
    "${maven[@]}" clean deploy -Drevision=1.1.0 -Prelease-staging -DskipTests
    "${maven[@]}" -N -Drevision=1.1.0 -Pconsumer-smoke verify
    ;;
  full-reactor)
    "${maven[@]}" clean verify \
      -Pdependency-analysis,architecture,api-compatibility,schema-compatibility
    ;;
  *)
    echo "Usage: $0 {unit-contract|localstack|mcp-contract-leak|consumer-smoke|full-reactor}" >&2
    exit 2
    ;;
esac
