# Spring Boot Project Coding Rules

Apply these rules when implementing or modifying Spring Boot backend code. First follow the existing project architecture and conventions; task requirements and existing API/database contracts take priority over general preferences.

## Architecture and layering

- Keep responsibilities separate: Controller handles HTTP requests and responses; Service owns business logic and workflows; Repository accesses data; Entity/Model represents persisted/domain data; DTO defines API input/output; Mapper converts Entity and DTO.
- A Controller must never call a Repository directly. Route Repository operations through the Service layer.
- Do not return Entity/Model objects from methods used by Controllers. Use request/response DTOs and Mappers where appropriate.
- Follow the existing Service/ServiceImpl pattern. Keep methods focused, avoid duplicated business logic, and add only what the feature requires.

## Repository and JPA

- Prefer clear Spring Data JPA derived query methods when they express the query well.
- For boolean/existence checks, prefer existsBy... over fetching an Entity.
- Keep Repository methods concise and focused on data access; do not put business logic there.
- Reuse existing Repository methods and avoid duplicate queries.

## Validation, exceptions, and startup

- Keep validation configuration separate from business logic. Follow and reuse the project's existing validation mechanism.
- Never throw RuntimeException directly. Use the existing custom exception mechanism (for example, AppException), add error codes only when needed, and rely on established global exception handling and response conventions.
- Keep status, error code, message, and language consistent with the project.
- Add startup initialization only when required. Follow the existing startup workflow: start application, validate required configuration, check required initial data, and create it if absent. Never hardcode sensitive configuration.

## DTOs, Entities, Lombok, and naming

- Do not expose Entity/Model objects through Controller APIs. Use Request/Response DTOs and the project's Mapper conventions for conversion.
- Follow existing Lombok usage (such as @Data, @NoArgsConstructor, @AllArgsConstructor, @RequiredArgsConstructor, or the project's chosen subset). Avoid hand-written boilerplate when Lombok is already the convention.
- Use clear, consistent Java/Spring names for Entity, DTO, Repository, Service, ServiceImpl, Controller, Mapper, Exception, and ErrorCode.

## Module boundaries and reuse

- A Service directly injects and uses only Repositories owned by its module/responsibility.
- For another module's data or business operation, call that module's Service and reuse its existing method. Add a Service method only when required functionality is missing. Do not duplicate queries in another module, expose Repositories as a workaround, or create a Service solely to wrap a Repository call when a suitable Service exists.
- Avoid circular Service dependencies. If one appears necessary, revisit module responsibilities and the exposed operations.
- Before creating code, search for equivalent Service and Repository methods, DTOs, Mappers, ErrorCodes, validation, utilities, exception handling, configuration, and shared components. Prefer reuse, then extension, then a new component only when needed.

## API, data, transactions, and security

- Follow the project's REST conventions: GET retrieves, POST creates, PUT replaces where appropriate, PATCH partially updates, and DELETE removes. Prefer resource-oriented URLs, consistent naming, appropriate status codes, and DTO-based request/response contracts.
- Inspect the schema, relationships, constraints, and indexes before persistence changes. Reuse existing tables where appropriate. If schema changes are required, use the project's versioned migration mechanism; do not manually alter production schema. Keep Entity mappings and migrations aligned and avoid unrelated database changes.
- Use transactions when a multi-record business operation must be atomic, following the project's transaction pattern. Avoid unnecessary transactions for simple reads; use read-only transactions where the project convention supports them.
- Preserve existing authentication and authorization. Do not trust client-provided identity, role, or ownership when it should come from authenticated context. Do not expose sensitive data or log credentials, tokens, or secrets.

## Code quality, configuration, and scope

- Keep the implementation readable and maintainable. Avoid duplication, dead code, unused imports/methods/variables/dependencies/configuration, unnecessary comments, and over-engineering.
- Store passwords, API keys, JWT secrets, database credentials, and other sensitive values in .env or the project's appropriate environment configuration. Never hardcode sensitive values.
- Modify only code required by the request. Do not touch unrelated modules or create unnecessary classes, methods, configuration, dependencies, or artifacts. Before adding one, check if existing code can be reused.

## Verification checklist

Before finishing, where applicable:

- Verify the main success scenario and expected validation errors.
- Verify relevant authentication and authorization behavior, not-found cases, and business conflicts.
- Run the project's existing relevant tests and gates using its established patterns.
- Confirm no unnecessary files or unrelated edits, unused code, duplication, or cross-module Repository dependencies were introduced.
- Confirm DTO/Entity separation, exception and validation conventions, authorization, migration/schema consistency, API contract compatibility, and established architecture.

When rules conflict, use this priority: existing project architecture and patterns; explicit task requirements; existing API/database contracts; these general rules; personal preference.

