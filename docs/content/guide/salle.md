---
title: "Component library (salle)"
weight: 5
---

**salle** is a component library for Riposte — the styled, ready-made widgets layer that
sits on top of the core's elements and hooks. (The name is the fencing *salle*: the hall
where the bouts happen.) It's a separate published artifact that depends only on
`riposte`'s public API, so it adds no weight to an app that doesn't use it.

```scala
libraryDependencies += "io.github.edadma" %%% "riposte-salle" % "0.0.2"
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

## Light & dark theming

Skins decide *which design system* renders a component; **themes** decide its *light/dark
palette*. They're orthogonal: a single global theme name drives a `data-theme` attribute on
the document root, and both skins respond to it — `SalleSkin` through the `[data-theme="…"]`
token blocks in `salle.css`, `DaisySkin` through DaisyUI's own themes.

The theme set is **open**. salle ships `light` and `dark`; `system` follows the OS
preference; and any other name selects whatever `[data-theme="…"]` block your app defines
(`"dracula"`, a brand theme, …).

`useTheme()` is the hook. It returns a named tuple and, on first read, installs salle's
theme management — setting `data-theme`, persisting the choice to `localStorage`, following
the OS while on `"system"`, and syncing across tabs:

```scala
val t = useTheme()
//  t.theme    : String        — the stored name ("system" / "light" / "dark" / custom)
//  t.resolved : String        — the concrete theme ("system" resolved to the OS pref)
//  t.setTheme : String => Unit — select any theme name
//  t.toggle   : () => Unit     — flip light ⇄ dark
```

For the common cases salle ships two ready components:

```scala
ThemeToggle                              // an icon button (sun/moon): light ⇄ dark
ThemeSelect(Seq("system", "light", "dark"))   // a <select> bound to the active theme
```

Both follow the active skin, and `ThemeSelect` is the way to surface a larger open theme
set. To define your own theme, add a `[data-theme="name"]` block (overriding the
`--salle-*` custom properties) and offer its name through `setTheme`/`ThemeSelect` — see
[Theming the default look](#theming-the-default-look).

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

### Select

A single-select dropdown — built as a custom ARIA combobox (not a native `<select>`), so
it supports typeahead, keyboard navigation, a clear affordance, and skin-styled options.
Options are built with `Opt`; the chosen value follows the same controlled/uncontrolled
model as the form controls above:

```scala
Select(
  options = Seq(
    Opt("us", "United States"),
    Opt("ca", "Canada"),
    Opt("mx", "Mexico", disabled = true),
  ),
  placeholder = "Country",
  clearable = true,
  onChange = setCountry,
)
```

`Opt(value, label, disabled)` defaults `label` to `value`. `Select` takes the usual
`color`/`size`/`disabled`/`invalid`, plus `clearable` (adds a reset button) and `name`
(emits a hidden input so it participates in native form submission). It's fully
keyboard-driven (arrows, Home/End, Enter/Space, Escape, type-to-search) and mirrors its
state to `data-*` (`data-state`, `data-value`, and per-option `data-selected`/`data-active`).

### ImageCard

A single image tile for a media grid. The full image loads lazily — only once the tile
nears the viewport (built on [`useIntersectionObserver`](/guide/hooks/#dom-hooks)) — showing
a skeleton until it arrives and fading in on load. If `src` fails it tries `fallback`, then
shows an error placeholder:

```scala
ImageCard(
  src = thumbUrl,
  alt = "Sunset over the bay",
  ratio = "16/9",                 // reserves space so the grid doesn't reflow
  fit = ImageFit.Cover,           // Cover | Contain | Fill | ScaleDown
  badge = Some(span("4K")),       // a corner tag
  overlay = Some(downloadButton), // hover content
  onClick = () => open(fullUrl),
)
```

`ratio` is a CSS `aspect-ratio` (`"16/9"`, `"1/1"`); `fit` controls cropping; `rounded`
(default `true`) rounds the corners; `lazyLoad = false` opts out of deferred loading.
State is mirrored to `data-state` (`loading`/`loaded`/`error`) and `data-inview`.

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

/* A custom theme, selectable by name through setTheme / ThemeSelect. */
[data-theme="ocean"] {
  color-scheme: dark;
  --salle-color-primary: #0c8599;
}
```

salle's own `light` and `dark` palettes are exactly such blocks, so the
[theming hook and components](#light-dark-theming) above pick up a custom one with no
extra wiring.

Classes follow a BEM-ish convention: a base (`.salle-btn`) plus independent modifiers for
color (`--primary`), variant (`--outline`), and size (`--sm`). Each color modifier sets a
single custom property that the variant rules reinterpret, so color × variant combine
without a rule per pair.
