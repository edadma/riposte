---
title: "Tooltip"
weight: 3
---

A small floating hint anchored to its trigger — the label on an icon button, the full text
of a truncated cell, a hint on a disabled control. Unlike DaisyUI's pure-CSS tooltip (a
string shown on hover only), salle's is JS-driven: the `tip` is rich content (any `VNode`),
it can be controlled, it opens on hover, focus, or click, and it honours show/hide delays.
The trigger is the `children`:

```scala
Tooltip(tip = span("Delete this file"))(
  Button("🗑", color = Color.Error),
)
```

`placement` (`TooltipPlacement.Top` / `Bottom` / `Left` / `Right`) chooses the side; `color`
tints the bubble. `trigger` selects how it appears:

- `TooltipTrigger.Hover` (default) — pointer hover **and** keyboard focus, the accessible
  default.
- `TooltipTrigger.Focus` — focus only.
- `TooltipTrigger.Click` — toggles on click and dismisses on an outside click.

Escape dismisses any open tooltip. It can be *uncontrolled* (`defaultOpen` seeds it) or
*controlled* (pass `open = Some(...)` and react to `onOpenChange`). `mouseEnterDelay` /
`mouseLeaveDelay` (ms) debounce show/hide so a quick pass doesn't flash it; `disabled`
suppresses it entirely; `exitMs` is the fade-out duration before it unmounts — keep it in
step with the skin's transition.

Mounting goes through the core [`usePresence`](/guide/hooks/#usepresence) hook, so the
close transition is seen before unmount. The tip is a `role="tooltip"` element referenced by
the wrapper's `aria-describedby` while shown. (riposte has no child-cloning, so the
relationship sits on the wrapper rather than the trigger element itself; a consumer that
needs `aria-describedby` on the control directly can set it themselves.) State is mirrored
to `data-*`.
