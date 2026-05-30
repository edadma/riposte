---
title: "Modules"
weight: 1
---

# Modules

Riposte ships as three independently published artifacts under the `io.github.edadma`
organization. The two sibling artifacts depend on the core transitively.

## riposte (core)

The virtual-DOM library. Import everything with `import io.github.edadma.riposte.*`.

- **VNodes & the DSL** — element functions, `AttrKey`/`:=`, typed `EventKey`s,
  `when`/`unless`, `portal`, `errorBoundary`, `unsafeHtml`, SVG namespacing.
- **Hooks** — `useState`, `useEffect`, refs, `useSyncExternalStore`, `useTransition`.
- **Rendering** — `render(component, container)` mounts and reconciles.

## riposte-atoms

Atomic, identity-based shared state built on the core's store seam.
Import with `import io.github.edadma.riposte.atoms.*`.

- **Atoms** — `atom(initial)`, derived `atom(get => …)`, writable-derived atoms,
  `selectAtom`, `atomFamily`, `atomWithStorage`, `atomLoadable`.
- **Hooks** — `useAtom`, `useAtomValue`, `useSetAtom` (require a `Store`).
- **Provider** — `StoreProvider` scopes a store to a subtree.

## riposte-router

Client-side routing for SPAs.
Import with `import io.github.edadma.riposte.router.*`.

- **Setup** — `Router(mode = History | Hash)` provides the routing context.
- **Routes** — `Routes`, `route`, nested children, `index`, `Outlet`.
- **Navigation** — `Link`, `NavLink`, `navigate`.
- **Params & query** — `useParams`, `useSearchParams`.

## Versions

All three are versioned together. The current published version is `0.0.1`.
