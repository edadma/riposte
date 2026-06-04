---
title: "Breadcrumb"
weight: 4
---

The trail that tells you where a page sits in the hierarchy — Home › Nature › Forests — and
lets you jump back up it. Data-driven like [`Tabs`](/salle/navigation/tabs/) and
[`Segmented`](/salle/forms/segmented/): pass the `items` and salle renders the `nav`
landmark, the ordered list, the links, and the separators:

```scala
Breadcrumb(items = Seq(
  Crumb("Home", href = Some("/")),
  Crumb("Nature", href = Some("/nature")),
  Crumb("Forests"),               // the current page — plain text, aria-current=page
))
```

`Crumb(label, href, onClick, icon)` builds a crumb: `href` makes it a link, `onClick` a
click handler (a focusable link that doesn't navigate), `icon` an optional leading slot. The
**last** item is the current page — rendered as plain text (not a link) with
`aria-current=page`.

By default the active [skin](/salle/concepts/skins/) draws the separators (SalleSkin a `/`,
DaisySkin a chevron). Pass a `separator` node to render your own between every pair.
`ariaLabel` names the landmark (default `"Breadcrumb"`). Structure is mirrored to `data-*`
(`data-part` on the root, each crumb, and each separator; `data-current` on the last crumb).
