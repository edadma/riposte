---
title: "Descriptions"
weight: 7
---

A description list — the label/value table you reach for on a detail page: a wallpaper's
resolution, license, palette, and download count, or a user's profile fields. Data-driven
like [`Tabs`](/salle/navigation/tabs/): pass the `items` and salle packs them into rows that
fill the column count and renders the `<table>`:

```scala
Descriptions(
  title = Some(span("Wallpaper")),
  bordered = true,
  column = 2,
  items = Seq(
    DescItem("Resolution", "3840×2160"),
    DescItem("License", "CC0"),
    DescItem("Palette", "warm tones", span = 2),   // spans both columns
  ),
)
```

`DescItem(label, value, span, filled)` builds a row: `span` is a fixed column count, or
`filled = true` makes the item take the rest of its row. `title`/`extra` add a header above
the table; `bordered` draws cell borders; `column` (default 3) sets the column count; `size`
scales the text; `colon` (default `true`) appends a `:` after each label.

`layout` (`DescriptionsLayout.{Horizontal,Vertical}`, default `Horizontal`) puts each label
beside its value or stacks a row of labels above a row of values. For a responsive column
count, pass `responsive = Some(descriptionsResponsive(sm = …, md = …, …))` — the largest
matching Tailwind breakpoint wins, falling back to `column` (also the behaviour where
`matchMedia` is unavailable, e.g. jsdom/SSR).

Each label is a `<th>` (`scope=row`/`scope=col`) and each value cell points back to it with
`aria-labelledby`, so a value is announced with its label. Structure is mirrored to `data-*`
(`data-bordered`/`data-layout`/`data-columns` on the root; `data-part` on every cell). The
row-packing logic is the pure, testable `descriptionsRows(items, columns)`.
