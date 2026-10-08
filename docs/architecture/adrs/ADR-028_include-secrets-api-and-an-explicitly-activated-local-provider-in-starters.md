# ADR-028: Include Secrets API and an Explicitly Activated Local Provider in Starters

**Source:** Inherited decision text from SAD 1.12 as carried into SAD 1.13. Historical status/date/owners are not supplied here.

**Decision:** Every starter includes the Secrets API and local provider implementation. The provider activates only through explicit configuration or the documented local profile and is never a silent production fallback. Multiple enabled providers require explicit selection or routing. Generated content contains secret references only.

**Consequences:** Local onboarding is complete without weakening the existing secret boundary. Startup validation and leak tests must cover ambiguous provider selection and all generated examples.
