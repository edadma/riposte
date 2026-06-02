---
title: "Checkbox & Toggle"
weight: 3
---

Both share the [Input](/salle/forms/input/)'s controlled/uncontrolled model
(`checked: Option[Boolean]` + `defaultChecked`), and both render a `<label>` around the box
when given a non-empty `label` so clicking the text toggles it. `Toggle` is the same control
styled as a switch, with `role="switch"` and `aria-checked` for assistive tech:

```scala
Checkbox(label = "Accept terms", onChange = setAccepted)
Checkbox(label = "Subscribed", defaultChecked = true)

Toggle(label = "Wi-Fi", defaultChecked = true)
Toggle(label = "Dark mode", checked = Some(dark), onChange = setDark)
```
