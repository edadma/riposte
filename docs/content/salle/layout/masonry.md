---
title: "Masonry"
weight: 3
---

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
[`ImageCard`](/salle/display/image-card/) with a `ratio` is the canonical cell. The pure
packing function, `layoutMasonry(heights, columns, gap, containerWidth)`, is exposed too if
you need the geometry without the component.

For aligned columns on a fixed grid rather than packed tiles, see
[Grid](/salle/layout/grid/).
