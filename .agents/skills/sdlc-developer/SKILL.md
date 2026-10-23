---
name: sdlc-developer
description: Implement and verify Spring Boot backend changes and related React/TypeScript frontend changes in an existing repository. Use for feature implementation, bug fixes, persistence changes, API changes, and implementation-focused self-review; do not use for review-only requests.
---

# SDLC Developer

Implement the requested code change directly in the existing project. This skill covers Spring Boot backend work and, when the requested behavior crosses the API boundary, its related frontend work. It focuses on code implementation and verification.

## Before editing

1. Read the repository's AGENTS.md, GEMINI.md, and other applicable local instructions.
2. Inspect the existing implementation and trace the affected flow end to end. For backend work, inspect the relevant Controller, Service, Repository, Entity, DTO, Mapper, validation, exception, security, configuration, migration, and tests. For frontend work, inspect the feature components, hooks, services, types, validation, API client, and tests.
3. Search for existing methods, queries, DTOs, mappers, error codes, validators, utilities, and shared components before adding anything. Check schema and migration conventions before changing persistence.
4. Identify the minimum set of files and modules needed. Preserve existing API and database contracts unless the request requires a change; coordinate a backend contract change with its frontend caller.

## Implement

- Make the smallest complete change that satisfies the request. Follow local architecture, naming, formatting, and established patterns.
- Apply the Spring Boot rules in [references/spring-boot-rules.md](references/spring-boot-rules.md).
- When frontend code is in scope, also apply [references/frontend-rules.md](references/frontend-rules.md).
- Keep responsibilities separated, module boundaries intact, and API/entity boundaries explicit. Reuse before extending; extend before creating.
- Do not add unrelated refactors, speculative abstractions, dependencies, files, comments, or documentation.

## Verify and clean up

1. Run the relevant existing project checks and tests for the changed code. Add task-specific temporary test files only when needed to verify behavior; run them, then remove only those temporary files created for this task. Never delete or rewrite pre-existing tests, fixtures, test utilities, or production source files as test cleanup. If the project already has a test that needs updating, preserve it and report that it remains.
2. Check success behavior and applicable validation, authentication, authorization, not-found, conflict, and failure cases. Confirm backend and frontend contracts agree when both are affected.
3. Review the final diff for scope, duplicate or unused code, secrets, unsafe data exposure, cross-module Repository dependencies, migration consistency, and accidental test artifacts.
4. If a check cannot run, report the exact command and the reason. Do not claim verification that was not performed.

## References

- Spring Boot rules: [references/spring-boot-rules.md](references/spring-boot-rules.md)
- Frontend rules: [references/frontend-rules.md](references/frontend-rules.md)
- Inherited code guidance: [references/inherited-code-guidance.md](references/inherited-code-guidance.md)

## Completion response

Summarize the implementation, list changed files, and state which checks ran and their results. Mention any assumptions, limitations, or remaining test files. Do not create extra documentation artifacts unless requested.



