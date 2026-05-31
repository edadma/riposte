---
title: "Component library (salle)"
weight: 5
---

**salle** is a component library for Riposte — the styled, ready-made widgets layer that
sits on top of the core's elements and hooks. (The name is the fencing *salle*: the hall
where the bouts happen.) It's a separate published artifact that depends only on
`riposte`'s public API, so it adds no weight to an app that doesn't use it.

```scala
libraryDependencies += "io.github.edadma" %%% "riposte-salle" % "0.0.1"
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

## The skin system

The idea that organizes salle: **components describe intent, not appearance.** A button
doesn't know it's blue or rounded — it knows it's a `Color.Primary`, `Size.Md`,
`ButtonVariant.Solid`. A **`Skin`** translates that intent into the CSS classes of one
concrete styling system, and the active skin is read from context. So you can re-skin an
entire app's look from a single provider at the root, with no change at any call site.

Two skins ship:

- **`SalleSkin`** (the default) — salle's own look. It emits `salle-*` classes whose rules
  live in `salle.css`. An app that configures nothing still gets a styled UI.
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

## Components

### Button

```scala
Button("Save")                                             // default
Button("Delete", color = Color.Error)
Button("Cancel", color = Color.Primary, variant = ButtonVariant.Outline)
Button("Go", size = Size.Sm, onClick = () => navigate("/next"))
Button("Disabled", disabled = true)
```

The three style axes — `color`, `variant`, `size` — are independent. The disabled state is
reflected both as the HTML attribute and as `data-state`, for styling hooks.

### Input

A single-line text field that's *controlled* when you pass `value = Some(...)` (your app
owns the text and is notified through `onChange`) or *uncontrolled* when you don't (salle
holds it internally, seeded from `defaultValue`):

```scala
// Controlled — the app drives the value:
val (email, setEmail, _) = useState("")
Input(value = Some(email), onChange = setEmail, inputType = "email")

// Uncontrolled — salle holds the text:
Input(defaultValue = "draft", placeholder = "Notes")

// Validation state:
Input(invalid = true, color = Color.Error)
```

`invalid` switches to the error treatment and sets `aria-invalid`.

### Checkbox and Toggle

Both share the Input's controlled/uncontrolled model (`checked: Option[Boolean]` +
`defaultChecked`), and both render a `<label>` around the box when given a non-empty
`label` so clicking the text toggles it. `Toggle` is the same control styled as a switch,
with `role="switch"` and `aria-checked` for assistive tech:

```scala
Checkbox(label = "Accept terms", onChange = setAccepted)
Checkbox(label = "Subscribed", defaultChecked = true)

Toggle(label = "Wi-Fi", defaultChecked = true)
Toggle(label = "Dark mode", checked = Some(dark), onChange = setDark)
```

## useControllable

The hook every salle form control is built on, and one you can reuse for your own
controlled/uncontrolled components. Given the optional controlled value, a default, and an
`onChange`, it returns a `(current, set)` pair that does the right thing in either mode:

```scala
val (current, set) = useControllable(value, default, onChange)
```

In **controlled** mode (`value` is `Some`) `set` only calls `onChange` — the next render's
`Some` carries the new value back. In **uncontrolled** mode (`value` is `None`) `set`
updates internal state *and* calls `onChange` as a notification. Writing a component
against this pair means its body is identical in both modes.

## Theming the default look

`SalleSkin`'s styles live in `salle.css`. Include it once in your app, then put any
overrides after it. Every rule sits in a low-priority `@layer salle` cascade layer, so
your own unlayered CSS always wins — no `!important`, no specificity fights. Two ways to
retheme, both plain CSS:

1. **Values** — override the `--salle-*` custom properties (colors, radius, focus ring) at
   `:root` or under a `[data-theme=…]` block.
2. **Rules** — restyle a selector directly (say, add a shadow to `.salle-btn`).

```css
:root {
  --salle-radius: 0.25rem;
  --salle-color-primary: #0c8599;
}
```

Classes follow a BEM-ish convention: a base (`.salle-btn`) plus independent modifiers for
color (`--primary`), variant (`--outline`), and size (`--sm`). Each color modifier sets a
single custom property that the variant rules reinterpret, so color × variant combine
without a rule per pair.
