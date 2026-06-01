---
title: "Skeleton"
weight: 3
---

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
`DaisySkin` pulses). When the fraction of work *is* known, reach for
[`Progress`](/salle/display/spinner/) instead.
