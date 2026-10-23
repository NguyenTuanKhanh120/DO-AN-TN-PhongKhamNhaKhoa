# Frontend Rules for Related React/TypeScript Work

Use these rules only when the requested feature also requires React/TypeScript frontend changes. They remain subject to the actual repository's conventions.

- Feature layout: src/features/<name>/{components,hooks,services,utils,types,constants,index.ts}.
- Data fetching and mutations live in TanStack Query hooks under hooks/ calling services/; components stay presentational where practical.
- Forms: react-hook-form + zod (@hookform/resolvers), schema in the feature's utils/validation.ts.
- Loading / error / empty states go through src/components/common/AsyncStateView.tsx instead of three hand-rolled branches.
- Types are explicit: no any, no unnecessary as; cross-feature domain types live in src/types/.
- Keep "use client" only where interaction actually needs it; no secrets in client-side code.

## Reviewer self-checks

- Hooks: rules of hooks respected, dependency arrays complete and minimal, no state derivable from props or other state, no effect doing what a query should do.
- Types: no any, no unchecked !, no as unknown as; discriminated unions instead of pairs of booleans that can contradict each other.
- Data layer: query keys consistent and unique, cache invalidation correct after mutations, pending and error states actually reach the UI, no silent catch.
- Forms: the zod schema is the single validation source; server-side errors map through the project's existing form-error helper; no second validation implementation in JSX.
- UI states: loading, empty, error and success all handled; stable list keys; no unescaped user content; user-facing strings follow project conventions.
- Next.js: use client placement, server/client boundary respected, nothing sensitive in the client bundle, no data fetching in render.
- Styling: Tailwind classes consistent with the surrounding UI; no one-off style system.

Follow the actual repository's layout, API client, UI strings, styling system, and test conventions when they differ from these examples.
