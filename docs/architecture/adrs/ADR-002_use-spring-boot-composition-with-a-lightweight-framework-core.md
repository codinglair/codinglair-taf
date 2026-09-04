# ADR-002: Use Spring Boot composition with a lightweight framework core

**Status:** Accepted  
**Date:** 2026-07-24  
**Decision owners:** Product Owner and Solution Architecture  
**Scope:** Codinglair TAF product family

## Context

The legacy framework uses class-name factories and property loading. The target needs typed configuration, optional modules, security, MCP, data, and integration support, while consumer test libraries must not be forced to become server applications.

## Decision

- Use Spring Boot 4.x for auto-configuration, typed properties, integration wiring, and platform services.
- Keep core contracts lightweight and independent of Spring where practical.
- Optional modules contribute conditional auto-configuration.
- Configure logical capability instances and aliases rather than implementation class names.
- Use Java 21, compatible Spring AI 2.x, Maven Wrapper, and pinned BOM/plugin versions.

## Consequences

- Spring supplies mature composition and configuration.
- Core modules remain independently consumable and testable.
- Auto-configuration and context tests become required.
- Care is required to keep Spring types from leaking into public core contracts.

## Alternatives considered

- Retain custom factories — rejected because they duplicate DI and remain weakly typed.
- Make every module a Spring Boot application — rejected because it harms library portability.

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
