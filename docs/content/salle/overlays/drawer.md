---
title: "Drawer"
weight: 2
---

A panel that slides in from an edge of the viewport over a dimming scrim — a filter
sidebar, a mobile nav, a detail/edit pane. It's the same modal-overlay machinery as
[`Modal`](/salle/overlays/modal/) (portalled to `document.body`, animated in and out via
[`usePresence`](/guide/hooks/#usepresence), focus trapped inside via the core
[`useFocusTrap`](/guide/hooks/) hook, Escape to close), but docked to one edge and sized
along that axis. You own `open` and react to `onClose`:

```scala
val (open, setOpen, _) = useState(false)

Button("Filters", onClick = () => setOpen(true))

Drawer(
  open = open,
  onClose = () => setOpen(false),
  placement = DrawerPlacement.Right,   // Left | Right | Top | Bottom
  size = "360px",                      // width (left/right) or height (top/bottom)
  title = Some(span("Filters")),
  footer = Some(Button("Apply", onClick = () => setOpen(false))),
)(
  filterForm,
)
```

`placement` (`DrawerPlacement.{Left,Right,Top,Bottom}`, default `Right`) picks the edge;
`size` is a CSS length applied to the docking axis (width for left/right, height for
top/bottom). `children` are the body; `title`, `extra` (header-right actions), and `footer`
are optional slots. `closable` toggles the corner close button; `mask` shows the scrim and
`maskClosable` lets a scrim click dismiss; `closeOnEsc` enables Escape; `ariaLabel` names the
dialog when there is no `title`. `exitMs` (default `250`) is how long the slide-out runs
before unmount — keep it in step with the skin's transition.

It implements the dialog ARIA pattern: `role="dialog"` and `aria-modal`, labelled by the
title (or `ariaLabel`), focus moved into the panel on open and **restored to the opener on
close**, Tab trapped inside. Lifecycle state is mirrored to `data-state` (`enter`/`open`/
`exit`) and `data-placement` on the root, mask, and panel, so the slide is driven from CSS.
