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

### Badge, Tag & CheckableTag

Three label pills built on one look. **`Badge`** is a non-interactive label — a category, a
resolution, "New". **`Tag`** adds a leading `icon` slot, an `onClick`, and an optional close
button (`closable`/`onClose`). **`CheckableTag`** is a controlled filter chip that toggles
on click:

```scala
Badge(color = Color.Success)("Available")
Badge(color = Color.Error, pill = true)("4K")
Badge(color = Color.Primary, dot = true)()        // a bare status dot, no text

Tag(closable = true, onClose = () => remove(id))("Draft")
Tag(icon = Some(span("★")), onClick = () => open())("Featured")

// A controlled filter chip:
val (on, setOn, _) = useState(false)
CheckableTag(checked = on, onChange = setOn)("Nature")
```

`Color`, `BadgeVariant` (`Solid`/`Outline`/`Soft`/`Dash`), and `Size` set the look; `pill`
rounds to a full capsule and `dot` collapses a Badge to a tiny indicator circle. `Tag`'s
close click is kept from bubbling to its `onClick`. `CheckableTag` reads as a filled primary
pill when checked and a quiet neutral one when not, and is fully keyboard-operable
(`role=button`, Enter/Space, `aria-pressed`).

### Skeleton

Loading placeholders — the grey, gently shimmering blocks that hold content's space until it
arrives, so the layout doesn't jump. Three pieces: **`Skeleton`** (one block),
**`SkeletonText`** (a stack of lines approximating a paragraph), and **`SkeletonImage`** (an
aspect-ratio tile with a faint picture glyph, for grid cells):

```scala
Skeleton(width = "12rem", height = "1.25rem")
Skeleton(width = "3rem", height = "3rem", circle = true)   // an avatar

SkeletonText(lines = 3)                 // last line shortened, reads as a paragraph
SkeletonImage(ratio = "16/10")          // reserves a gallery cell

// The canonical "loading tile":
if loaded then ImageCard(src = url, ratio = "16/10")
else SkeletonImage(ratio = "16/10")
```

Every block takes `animated` (default `true`, toggles the shimmer) and `rounded`; they're
purely decorative (`aria-hidden`), so assistive tech announces the eventual content, not the
placeholder. The shimmer is the one skin-dependent piece (`SalleSkin` sweeps a sheen,
`DaisySkin` pulses).

### Spinner, Progress & RadialProgress

Busy indicators, split by whether you know the fraction. **`Spinner`** is *indeterminate* — a
small animated mark for work of unknown duration; **`Progress`** is a linear bar (determinate
when you pass a `value`, indeterminate when you don't); **`RadialProgress`** is a ring with the
percentage in its centre:

```scala
Spinner()                                  // a bare mark + off-screen "Loading"
Spinner(kind = SpinnerType.Dots, tip = "Fetching…")

Progress(value = Some(70))                 // 70 %
Progress()                                 // indeterminate animation

RadialProgress(value = 33)                 // a ring showing "33%"
```

`Spinner` also has an **overlay mode**: give it `children` and it floats the mark over them,
dimming and disabling the content while `spinning`, then reveals it when `spinning = false`:

```scala
Spinner(spinning = loading)(
  div(/* the panel being loaded */),
)
```

`SpinnerType` picks the animation (`Spinner`/`Dots`/`Ring`/`Ball`/`Bars`/`Infinity`), and
`Color`/`Size` tint and scale it. `Progress` renders the native `<progress>` element, so
assistive tech reads the value for free; `RadialProgress` exposes `role="progressbar"` with
`aria-valuenow`, takes `size`/`thickness` overrides, and shows custom centre `children` (or
`showValue = false` for a bare ring). All three take their look from the active skin.

### Pagination

A controlled page navigator: prev / numbered buttons with collapsing `…` gaps / next. You
own `current` (1-indexed) and react to `onChange(page)`; the strip is computed from `total`
items at `pageSize` per page:

```scala
val (page, setPage, _) = useState(1)
Pagination(current = page, total = 240, pageSize = 20, onChange = setPage)

// A compact "‹ 3 / 12 ›" bar:
Pagination(current = page, total = 240, pageSize = 20, simple = true, onChange = setPage)
```

`siblingCount` sets how many page buttons flank the current one before the rest collapse to
`…`; `simple` swaps the full strip for a compact prev / "n / m" / next bar; `disabled` greys
the control; `Size` scales it. The "which buttons to show" decision is a pure, exported
function — `paginationRange(current, totalPages, siblingCount)` returning a
`Vector[PageItem]` (`Page(n)` / `Dots`) — alongside `pageCount(total, pageSize)`, so you can
unit-test or reuse the logic without a DOM.

### Dropdown

A menu button: a trigger that opens a `role=menu` of actions. Data-driven — you pass the
`items` and salle renders the popup and handles open/close, keyboard, and dismissal. Unlike
[`Select`](#select) it has no chosen value; picking an item runs it and closes:

```scala
Dropdown(
  items = Seq(
    MenuItem("edit", "Edit", onSelect = () => edit()),
    MenuItem("dup", "Duplicate", onSelect = () => duplicate()),
    MenuDivider,
    MenuItem("del", "Delete", danger = true, onSelect = () => remove()),
  ),
  onSelect = key => log(key),
)("Actions")
```

Items are built with `MenuItem(key, label, icon, disabled, danger, onSelect)` and
`MenuDivider`. Choosing an item runs its own `onSelect` and reports its `key` to the
dropdown-level `onSelect`. It's fully keyboard-driven (arrows skip dividers and disabled
items with wrap, Home/End, Enter/Space, Escape refocuses the trigger, Tab closes), dismisses
on outside click (via core [`useClickOutside`](/guide/hooks/#dom-hooks)), and carries the
full `aria-haspopup`/`expanded`/`activedescendant` wiring. Submenus and hover-to-open are out
of scope for now.

## Layout

Two layout systems sit alongside the components — one for *precise* column layouts, one for
*packed* ones.

### Grid (Row / Col)

A 24-column grid in the Ant Design tradition: a `Row` lays out equal column tracks, and each
`Col` spans some of them, with per-breakpoint spans, offsets, and ordering. Reach for it
when you want columns to *line up* on a stated grid:

```scala
Row(gutterX = 24)(
  Col(span = 16)(main),
  Col(span = 8)(sidebar),
)

// Responsive: full width on phones, half on small, a third from medium up.
Row()(
  Col(xs = 24, sm = 12, md = 8)(card1),
  Col(xs = 24, sm = 12, md = 8)(card2),
  Col(xs = 24, sm = 12, md = 8)(card3),
)
```

`Row(cols, gutterX, gutterY, justify, align)` — `cols` defaults to 24; `gutterX`/`gutterY`
are the pixel gutters; `justify` aligns columns along the row
(`start`/`end`/`center`/`between`/`around`/`evenly`) and `align` across it
(`start`/`end`/`center`/`stretch`/`baseline`). `Col(span, offset, order, xs…xxl)` — `span`
is the base width (full row by default), `offset` pushes it right, `order` overrides visual
position, and `xs`/`sm`/`md`/`lg`/`xl`/`xxl` set per-breakpoint spans (each inheriting the
next smaller when unset). Both take their children curried: `Row(…)(cols*)`, `Col(…)(content*)`.

### Masonry

A Pinterest-style packed layout: tiles of differing heights flow into a fixed number of
equal-width columns, each tile placed into the currently shortest column (balanced columns,
left-to-right reading order — unlike a CSS multi-column flow). It measures real tile heights
(via [`useResizeObserver`](/guide/hooks/#dom-hooks)) and re-packs on resize or when children
change:

```scala
Masonry(columns = 3, gap = 16)(tiles*)

// Column count follows the viewport:
Masonry(columns = MasonryColumns.Responsive(base = 1, md = 2, lg = 3))(tiles*)
// or the shorthand:
MasonryResponsive(base = 1, sm = 2, lg = 3)(tiles*)
```

A bare `Int` for `columns` is a fixed count (`Masonry(columns = 3)`); `MasonryColumns.Responsive`
picks a count by viewport against the standard breakpoints. Tiles should size naturally — an
[`ImageCard`](#imagecard) with a `ratio` is the canonical cell. The pure packing function,
`layoutMasonry(heights, columns, gap, containerWidth)`, is exposed too if you need the
geometry without the component.

## Overlays

Two components float above the page, layered through a core
[`portal`](/guide/components/) into `document.body` and mounted/unmounted with a visible
enter and exit via the core [`usePresence`](/guide/hooks/#usepresence) hook.

### Modal

A controlled dialog over a dimming scrim. You own `open` and react to `onClose`, which fires
from the close button, a scrim click (when `maskClosable`), and Escape (when `closeOnEsc`).
`children` are the body; `title` and `footer` are optional slots:

```scala
val (open, setOpen, _) = useState(false)

Button("Open", onClick = () => setOpen(true))

Modal(
  open = open,
  onClose = () => setOpen(false),
  title = Some(span("Delete file?")),
  footer = Some(div(
    Button("Cancel", onClick = () => setOpen(false)),
    Button("Delete", color = Color.Error, onClick = () => { remove(); setOpen(false) }),
  )),
)(
  p("This can't be undone."),
)
```

It implements the full dialog ARIA pattern: `role="dialog"` (or `alertdialog` with `alert =
true`) and `aria-modal`, labelled by the title (or an explicit `ariaLabel`), focus moved
into the dialog on open and **restored to the opener on close**, Tab trapped inside, and
Escape to close. `centered` vertically centres the box, `width` overrides its width (capped
at `90vw`), and `closable` toggles the corner close button. `exitMs` (default `200`) is how
long the close animation runs before unmount — keep it in step with the skin's transition.

### Toast & Toaster

Transient notifications, created **imperatively** — exactly what a "Downloaded" or "Added to
favourites" handler wants, rather than threading open-state through the view tree. Mount a
single `Toaster()` once anywhere in the tree, then call the `toast` API from anywhere:

```scala
// Once, near the root:
div(App(), Toaster())

// Anywhere, imperatively:
toast.success("Downloaded")
toast.error("Upload failed", description = Some(span("Check your connection.")))
val id = toast.loading("Processing…")     // sticky spinner; dismiss when done
toast.dismiss(id)
```

`toast.info`/`success`/`warning`/`error` each take a `message`, optional `description`, and
`duration` (ms; defaults to 4000). `toast.loading` is a sticky spinner with no auto-dismiss
until you `dismiss(id)` or `clear()`. The general form `toast.show(message, kind, …)` also
takes a `placement` (`ToastPlacement` — six corners/edges; each toast carries its own, so one
`Toaster` can show several stacks), `closable`, a custom `icon`, and an `onClick`. Each toast
auto-dismisses after its `duration`, **pauses on hover**, and animates out before leaving;
urgent kinds (`Error`/`Warning`) announce assertively (`role="alert"`), the rest politely.
The store behind it (`ToastStore`) is a plain external store bridged in through
[`useSyncExternalStore`](/guide/hooks/#usesyncexternalstore).

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
