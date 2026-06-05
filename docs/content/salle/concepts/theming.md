---
title: "Light & dark theming"
weight: 2
---

[Skins](/salle/concepts/skins/) decide *which design system* renders a component; **themes**
decide its *light/dark palette*. They're orthogonal: a single global theme name drives a
`data-theme` attribute on the document root, and both skins respond to it — `SalleSkin`
through the `[data-theme="…"]` token blocks in `salle.css`, `DaisySkin` through DaisyUI's
own themes.

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
[Theming the default look](#theming-the-default-look) below.

## Theming the default look

`SalleSkin`'s styles ship inside the `riposte-salle` artifact and self-install the first
time a component renders — there is no `salle.css` to link. To retheme, just add your own
CSS: every salle rule sits in a low-priority `@layer salle` cascade layer, so your own
unlayered CSS always wins — no `!important`, no specificity fights. Two ways to retheme,
both plain CSS:

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

salle's own `light` and `dark` palettes are exactly such blocks, so the theming hook and
components above pick up a custom one with no extra wiring.

Classes follow a BEM-ish convention: a base (`.salle-btn`) plus independent modifiers for
color (`--primary`), variant (`--outline`), and size (`--sm`). Each color modifier sets a
single custom property that the variant rules reinterpret, so color × variant combine
without a rule per pair.
