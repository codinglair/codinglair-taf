# ADR-015: Provide an incremental functional Kind reference deployment

**Status:** Accepted  
**Date:** 2026-07-24  
**Decision owners:** Product Owner and Solution Architecture  
**Scope:** Codinglair TAF product family

## Context

The software must be containerized and Kubernetes/cloud ready, and the owner uses Docker and Kind locally. Architecture-ready manifests alone do not prove that the system deploys or operates in Kubernetes.

## Decision

- Provide a functional reference deployment suitable for Kind.
- Add container images, Deployments, Services, ConfigMaps, secret references, probes, resource requests/limits, persistence where required, MongoDB internal/external configuration, MCP exposure, and documented setup.
- Expand the deployment as components become available in each phase.
- Defer production-grade ingress, HA, backup automation, TLS, autoscaling, and full production OIDC until corresponding deployment work is approved.
- Continuously validate the reference deployment on a fresh Kind cluster.

## Consequences

- Kubernetes readiness is executable rather than aspirational.
- Local developers may still run Maven or containers without Kind.
- The reference deployment grows incrementally.
- Production hardening remains a distinct scope.

## Alternatives considered

- Architecture-ready only — rejected because it cannot satisfy local Kind deployment.
- Full production platform immediately — rejected because many services and requirements are not yet implemented.

## Compliance and verification

- Architecture and dependency tests shall enforce machine-verifiable boundaries.
- The applicable implementation assignments shall include unit, integration, contract, security, and compatibility tests.
- Public contract or schema changes shall update the Legacy Contract Compatibility Matrix and migration guidance.
- Deviations require a superseding ADR or an explicitly approved amendment.

## Related documents

- Test Automation Framework and Quality Intelligence Platform BRD v1.0
- Codinglair TAF and Quality Intelligence SAD v1.0
- Codinglair TAF Engineering and DevOps Implementation Plan v1.0
- Legacy Contract Compatibility Matrix, when created
