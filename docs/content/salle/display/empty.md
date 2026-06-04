---
title: "Empty"
weight: 6
---

The "nothing here" placeholder — what a gallery shows when a search or filter returns no
results, a list is empty, or a panel has no content yet. Three stacked pieces: an
illustration, a description line, and an optional action slot (the `children`, typically a
button that clears the filter or adds the first item):

```scala
Empty(description = Some(span("No wallpapers match your filters")))(
  Button("Clear filters", onClick = () => reset()),
)
```

`image` chooses the illustration via `EmptyImage`: `Default` (the full line-art drawing),
`Simple` (a smaller minimal one), `Hidden` (no illustration), or `Custom(node)` (your own
icon or photo). Both built-in drawings are in `currentColor`, so they take the surrounding
text colour under any skin. `description` overrides the default "No data" text.

The root is a `role=status` region labelled by the description (so assistive tech announces
the empty state), and the structure is mirrored to `data-*` (`data-part` on the root and
each piece).
