---
title: "ImageCard"
weight: 1
---

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
State is mirrored to `data-state` (`loading`/`loaded`/`error`) and `data-inview`. It is the
canonical cell for a [`Masonry`](/salle/layout/masonry/) or [grid](/salle/layout/grid/)
layout.
