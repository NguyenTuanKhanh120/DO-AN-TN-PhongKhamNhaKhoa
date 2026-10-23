# Inherited Code Instructions

Use this extended implementation and self-review guidance when a task needs deeper acceptance-criteria traceability or a broad final diff review. Pipeline artifact creation, Git workflow, and stage-specific report contracts are excluded.

## sdlc-developer: Applicable implementation steps

2. Explore the existing code the spec tells you to reuse. Read it before writing anything that
   could duplicate it.
3. Plan the change file by file. Keep the plan in your notes; do not create planning files.
4. Implement - the smallest change that satisfies every AC.
5. Add or update tests and verification exactly as the spec's test plan requires.
6. Run the project gates and fix failures before reporting.
7. Self-review your own diff with the checklist below.

## sdlc-developer: Rules

- Scope: only the files this feature needs. Unrelated refactors, formatting churn and
  "while we are here" changes are review findings, not goodwill.
- Reuse before create. A new helper next to an existing one that does the same job is a DRY
  violation the reviewer will find and reject.
- Follow the project's layout and naming exactly (see GEMINI.md).
- Keep the code simple: small functions, honest names, no dead code, no commented-out code, no
  speculative abstraction, no framework or dependency the spec did not allow.
- Comments only where the "why" is non-obvious. Never narrate what the code already says.


## Self-review checklist before reporting

- Every AC implemented and covered by the spec's verification plan.
- Error, loading, empty and permission states handled wherever the UI or API needs them.
- No new duplication of existing logic (search before you claim this).
- No new dependency unless the spec allows it; if unavoidable, say so in the REPORT.
- Gates pass, with the output kept short and factual.
- Diff contains nothing unrelated.

## TypeScript / React specifics (this project)

- Feature layout: `src/features/<name>/{components,hooks,services,utils,types,constants,index.ts}`.
- Data fetching and mutations live in TanStack Query hooks under `hooks/` calling `services/`;
  components stay presentational where practical.
- Forms: react-hook-form + zod (`@hookform/resolvers`), schema in the feature's `utils/validation.ts`.
- Loading / error / empty states go through `src/components/common/AsyncStateView.tsx` instead of
  three hand-rolled branches.
- Types are explicit: no `any`, no unnecessary `as`; cross-feature domain types live in `src/types/`.
- Keep `"use client"` only where interaction actually needs it; no secrets in client-side code.

## sdlc-code-reviewer: Method - run all ten passes, in order

## Method - run all ten passes, in order

1. **AC traceability.** Map every acceptance criterion to the code that satisfies it and the test
   or verification step that proves it. An AC with no code or no proof is a `blocker`.
2. **Read whole files.** A hunk without its file hides duplicated helpers, dead code and broken
   invariants.
3. **DRY sweep - mandatory; this pass catches most of the waste.**
   - List every new function, hook, component, class, DTO, constant and type the diff introduces.
   - For each, search the repository for an existing equivalent: grep distinctive identifiers,
     string literals and field names. Start with the shared building blocks named in GEMINI.md.
   - Take 5+ line blocks from the diff and grep their distinctive lines to find near-copies, both
     inside this feature and in other features.
   - The same logic living in two features belongs in the shared location, not in both.
   - Report every duplication as `major` with both locations and the shared home it should get.
   - Flag the inverse too: an abstraction introduced for a single caller, or an existing shared
     helper bent out of shape to serve one caller.
4. **Clean code pass.** Names say what things are; functions do one thing and stay short; nesting
   is shallow; no dead code, no commented-out code, no leftover debug output, no magic values, no
   speculative generality; comments explain "why" and never restate the code.
5. **Correctness pass.** Logic, boundary conditions, off-by-one, null/undefined/empty handling,
   error paths, async ordering, race conditions, state updates, sorting, pagination, filtering,
   permission checks.
6. **Contract pass.** Types and signatures are honest; API request/response shapes match the spec;
   no unsafe casts; existing callers keep working.
7. **Security pass.** Injection, unvalidated input, secrets in code or logs, missing authorisation,
   unsafe rendering, careless path or URL handling.
8. **Test pass.** Tests assert behaviour, cover the ACs and the edge cases the spec lists, fail for
   the right reason, and contain no production shortcuts (`if (test)` branches, disabled
   validation, mocked-away logic that hides bugs).
9. **Conventions pass.** Layout, naming and reuse rules from GEMINI.md.
10. **Run the gates yourself** (commands in GEMINI.md). A failing gate is a `blocker`; quote the
    first meaningful error lines.

### TypeScript / React checklist

- Hooks: rules of hooks respected, dependency arrays complete and minimal, no state derivable from
  props or other state, no effect doing what a query should do.
- Types: no `any`, no unchecked `!`, no `as unknown as`; discriminated unions instead of pairs of
  booleans that can contradict each other.
- Data layer: query keys consistent and unique, cache invalidation correct after mutations, pending
  and error states actually reach the UI, no silent `catch`.
- Forms: the zod schema is the single validation source; server-side errors map through
  `src/utils/form-errors` / `getApiErrorMessage`; no second validation implementation in JSX.
- UI states: loading, empty, error and success all handled; stable list keys; no unescaped user
  content; user-facing strings come from the same place as the rest of the feature.
- Next.js: `"use client"` placement, server/client boundary respected, nothing sensitive in the
  client bundle, no data fetching in render.
- Styling: Tailwind classes consistent with the surrounding UI, no one-off style system.
