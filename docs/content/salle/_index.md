---
title: "Component library (salle)"
weight: 3
---

**salle** is a component library for Riposte — the styled, ready-made widgets layer that
sits on top of the core's elements and hooks. (The name is the fencing *salle*: the hall
where the bouts happen.) It's a separate published artifact that depends only on
`riposte`'s public API, so it adds no weight to an app that doesn't use it.

```scala
libraryDependencies += "io.github.edadma" %%% "riposte-salle" % "0.1.0"
```

```scala
import io.github.edadma.riposte.*
import io.github.edadma.riposte.salle.*

val Form = view {
  div(
    Input(placeholder = "Email", inputType = "email"),
    Checkbox(label = "Remember me", defaultChecked = true),
    Button("Sign in", color = Color.Primary, onClick = () => submit()),
  )
}
```

Every salle component is an ordinary function with default arguments, returning a `VNode`
— so you call it like any other component and override only the props you care about.

## How this section is organized

- **[Concepts](/salle/concepts/)** — the skin system, light/dark theming, and the
  `useControllable` hook the form controls are built on. Start here.
- **[Form controls](/salle/forms/)** — `Button`, `Input`, `Checkbox`/`Toggle`, `Select`,
  `Segmented`.
- **[Display](/salle/display/)** — `ImageCard`, `Image`/`Lightbox`, `Badge`/`Tag`,
  `Skeleton`, the `Spinner`/`Progress` busy indicators, the `Empty` placeholder, and
  `Descriptions`.
- **[Navigation](/salle/navigation/)** — `Pagination`, `Dropdown`, `Tabs`, and `Breadcrumb`.
- **[Overlays](/salle/overlays/)** — `Modal`, `Drawer`, `Toast`, and `Tooltip`.
- **[Layout](/salle/layout/)** — the `Layout`/`Navbar`/`Footer` page shell, plus the
  `Row`/`Col` grid and `Masonry`.
