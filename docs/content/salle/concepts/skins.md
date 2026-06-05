---
title: "The skin system"
weight: 1
---

The idea that organizes salle: **components describe intent, not appearance.** A button
doesn't know it's blue or rounded — it knows it's a `Color.Primary`, `Size.Md`,
`ButtonVariant.Solid`. A **`Skin`** translates that intent into the CSS classes of one
concrete styling system, and the active skin is read from context. So you can re-skin an
entire app's look from a single provider at the root, with no change at any call site.

Two skins ship:

- **`SalleSkin`** (the default) — salle's own look. It emits `salle-*` classes whose rules
  ship inside the artifact and self-install on first render, so an app that configures
  nothing still gets a styled UI — no stylesheet to link.
- **`DaisySkin`** — emits the [DaisyUI](https://daisyui.com/) class vocabulary
  (`btn btn-primary btn-sm`). The styles come from DaisyUI + Tailwind in your own CSS
  build; salle just produces the class names.

Choose one with `SkinProvider` near the root; everything below it picks it up through
`useSkin`:

```scala
import io.github.edadma.riposte.salle.*

render(
  SkinProvider(DaisySkin) {
    App()
  },
  dom.document.getElementById("app"),
)
```

Writing your own skin is implementing the `Skin` trait — one method per component, mapping
its semantic props to your classes.

## The style vocabulary

Two enums are shared by every component that has them, so the same values mean the same
thing everywhere:

- **`Color`** — `Default`, `Primary`, `Secondary`, `Accent`, `Neutral`, `Info`,
  `Success`, `Warning`, `Error` (mirrors DaisyUI's palette). `Default` applies no color
  modifier — the component keeps its base look.
- **`Size`** — `Xs`, `Sm`, `Md`, `Lg`, `Xl`.

A component's *variant* axis is specific to it, so each declares its own enum — e.g.
`ButtonVariant` (`Solid`, `Outline`, `Dash`, `Soft`, `Ghost`, `Link`).

Theming — the light/dark palette that sits *on top* of whichever skin renders a component
— is covered next, in [Light & dark theming](/salle/concepts/theming/).
