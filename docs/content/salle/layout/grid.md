---
title: "Grid (Row / Col)"
weight: 1
---

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

For *packed* tiles of differing heights rather than aligned columns, see
[Masonry](/salle/layout/masonry/).
