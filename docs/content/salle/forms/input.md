---
title: "Input"
weight: 2
---

A single-line text field that's *controlled* when you pass `value = Some(...)` (your app
owns the text and is notified through `onChange`) or *uncontrolled* when you don't (salle
holds it internally, seeded from `defaultValue`):

```scala
// Controlled — the app drives the value:
val (email, setEmail, _) = useState("")
Input(value = Some(email), onChange = setEmail, inputType = "email")

// Uncontrolled — salle holds the text:
Input(defaultValue = "draft", placeholder = "Notes")

// Validation state:
Input(invalid = true, color = Color.Error)
```

`invalid` switches to the error treatment and sets `aria-invalid`. The controlled/uncontrolled
behaviour is the [`useControllable`](/salle/concepts/use-controllable/) pattern shared by
every form control.
