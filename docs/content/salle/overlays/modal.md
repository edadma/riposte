---
title: "Modal"
weight: 1
---

A controlled dialog over a dimming scrim, layered through a core
[`portal`](/guide/components/) and animated in and out via
[`usePresence`](/guide/hooks/#usepresence). You own `open` and react to `onClose`, which
fires from the close button, a scrim click (when `maskClosable`), and Escape (when
`closeOnEsc`). `children` are the body; `title` and `footer` are optional slots:

```scala
val (open, setOpen, _) = useState(false)

Button("Open", onClick = () => setOpen(true))

Modal(
  open = open,
  onClose = () => setOpen(false),
  title = Some(span("Delete file?")),
  footer = Some(div(
    Button("Cancel", onClick = () => setOpen(false)),
    Button("Delete", color = Color.Error, onClick = () => { remove(); setOpen(false) }),
  )),
)(
  p("This can't be undone."),
)
```

It implements the full dialog ARIA pattern: `role="dialog"` (or `alertdialog` with `alert =
true`) and `aria-modal`, labelled by the title (or an explicit `ariaLabel`), focus moved
into the dialog on open and **restored to the opener on close**, Tab trapped inside, and
Escape to close. `centered` vertically centres the box, `width` overrides its width (capped
at `90vw`), and `closable` toggles the corner close button. `exitMs` (default `200`) is how
long the close animation runs before unmount — keep it in step with the skin's transition.
