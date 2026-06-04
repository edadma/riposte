---
title: "Page shell — Layout, Navbar & Footer"
weight: 1
---

The structural frame a site lays its content into: a `Layout` flex container with `Header`,
`Content`, `Footer`, and `Sider` regions, a `Navbar` for the top bar, and a content
`Footer` for the foot. Each is a thin styled wrapper over a semantic landmark element, so
the look comes from the active [skin](/salle/concepts/skins/) and the structure stays
accessible.

```scala
Layout() (
  Layout.Header(Navbar(start = brand, end = actions)),
  Layout(hasSider = true) (
    Layout.Sider(collapsible = true)(sideNav),
    Layout.Content(pageBody),
  ),
  Layout.Footer(siteFooter),
)
```

## Layout and its regions

`Layout()` is the flex container. It stacks its regions vertically (header / content /
footer) by default; pass `hasSider = true` to lay them in a **row** so a `Sider` sits beside
the `Content`. Nest a horizontal inner layout inside a vertical outer one to get both axes.

- `Layout.Header(children*)` — the top band, a `<header>` (`role="banner"`). Put a `Navbar`
  (or your own brand + nav) here.
- `Layout.Content(children*)` — the main region, a `<main>` that grows to fill the space the
  header/footer/sider leave and scrolls its overflow.
- `Layout.Footer(children*)` — the bottom band, a `<footer>` (`role="contentinfo"`). For a
  multi-column footer, put a content `Footer` inside it.
- `Layout.Sider(...)(children*)` — a collapsible sidebar, placed as a direct child of a
  `Layout(hasSider = true)` beside the `Content`.

`Sider` collapse is controllable — pass `collapsed` to own it (told via `onCollapse`) — or
self-managed from `defaultCollapsed`. `collapsible` shows a trigger button; `width` /
`collapsedWidth` are CSS lengths; `breakpoint` (a media query) auto-folds it on small
screens while uncontrolled; `reverseArrow` flips the trigger glyph for a right-docked sider;
`theme` (`SiderTheme.Dark` default, or `Light`) picks the surface.

## Navbar

A `<nav>` landmark with three zones — `start` (brand/logo), `center`, and `end` (actions):

```scala
Navbar(
  start = strong("Acme"),
  center = navLinks,
  end = ThemeToggle(),
  sticky = true,
  shadow = NavbarShadow.Sm,
  collapsible = true,           // hamburger below the breakpoint
  breakpoint = "(max-width: 768px)",
)
```

`color` tints the bar ([`Color`](/salle/concepts/skins/)); `sticky` pins it to the top of
the viewport; `shadow` (`NavbarShadow.{None,Sm,Md,Lg,Xl}`) and `rounded`
(`NavbarRounded.{None,Sm,Md,Lg,Xl,Full}`) shape it. When `collapsible`, a hamburger toggle
appears below `breakpoint` and folds the `center`/`end` zones behind it until opened.

## Footer

The page-foot content block — link columns, copyright — with a `Footer.Title` for each
column heading. Distinct from `Layout.Footer` (the bottom *band*); a content `Footer` is
what you place inside that band.

```scala
Layout.Footer(
  Footer(horizontal = true) (
    div(Footer.Title("Product"), a(href := "/docs", "Docs")),
    div(Footer.Title("Company"), a(href := "/about", "About")),
  ),
)
```

`center` centres the columns; `horizontal` lays them in a row (versus stacked).

Every region mirrors its state to `data-*` (`data-part`, `data-has-sider`, `data-collapsed`,
`data-narrow`, `data-open`) for skin-independent selection.
