# Architecture Decision Records (ADRs)

This directory contains Architecture Decision Records (ADRs) for the GrowthPrint backend.

## What is an ADR?

An Architecture Decision Record (ADR) is a document that describes an important architectural decision made during a project. Each ADR captures the context, alternatives considered, decision, and consequences.

## ADR Template

```markdown
# ADR [Number]: [Title]

## Status
[Proposed | Accepted | Deprecated | Superseded]

## Context
[What is the issue that we're facing that needs a decision?]

## Decision
[What is the change that we're proposing?]

## Consequences
[What becomes easier or more difficult to do because of this change?]
```

## ADR Index

- [ADR 001: Use Spring Boot Framework](./001-spring-boot-framework.md) - Accepted
- [ADR 002: Use PostgreSQL Database](./002-postgresql-database.md) - Accepted
- [ADR 003: Use Flyway for Migrations](./003-flyway-migrations.md) - Accepted

## Guidelines

1. Create an ADR for any significant architectural decision
2. Use the template above for consistency
3. Update the index when adding new ADRs
4. Mark deprecated decisions clearly
5. Link to related ADRs
6. Keep ADRs concise and focused

## References

- [Michael Nygard's ADR format](https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions)
- [Recording Architecture Decisions](https://www.thoughtworks.com/radar/techniques/practices/recording-architecture-decisions)
