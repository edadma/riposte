---
title: "Button"
weight: 1
---

```scala
Button("Save")                                             // default
Button("Delete", color = Color.Error)
Button("Cancel", color = Color.Primary, variant = ButtonVariant.Outline)
Button("Go", size = Size.Sm, onClick = () => navigate("/next"))
Button("Disabled", disabled = true)
```

The three style axes — `color`, `variant`, `size` — are independent. `color` and `size`
come from the [shared vocabulary](/salle/concepts/skins/#the-style-vocabulary); `variant` is
`ButtonVariant` (`Solid`/`Outline`/`Dash`/`Soft`/`Ghost`/`Link`). The disabled state is
reflected both as the HTML attribute and as `data-state`, for styling hooks.
