# ADR-015: Provide an Incremental Functional Kind Reference Deployment

**Source:** Inherited decision text from SAD 1.12 as carried into SAD 1.13. Historical status/date/owners are not supplied here.

**Context** The software must be containerized and Kubernetes/cloud ready, and the owner uses Docker and Kind locally. Architecture-ready manifests alone do not prove that the system deploys or operates in Kubernetes.

**Decision**

- Provide a functional reference deployment suitable for Kind.
- Add container images, Deployments, Services, ConfigMaps, secret references, probes, resource requests/limits, persistence where required, MongoDB internal/external configuration, MCP exposure, and documented setup.
- Expand the deployment as components become available in each phase.
- Defer production-grade ingress, HA, backup automation, TLS, autoscaling, and full production OIDC until corresponding deployment work is approved.
- Continuously validate the reference deployment on a fresh Kind cluster.

**Consequences**

- Kubernetes readiness is executable rather than aspirational.
- Local developers may still run Maven or containers without Kind.
- The reference deployment grows incrementally.
- Production hardening remains a distinct scope.
