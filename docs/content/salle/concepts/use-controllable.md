---
title: "useControllable"
weight: 3
---

The hook every salle form control is built on, and one you can reuse for your own
controlled/uncontrolled components. Given the optional controlled value, a default, and an
`onChange`, it returns a `(current, set)` pair that does the right thing in either mode:

```scala
val (current, set) = useControllable(value, default, onChange)
```

In **controlled** mode (`value` is `Some`) `set` only calls `onChange` — the next render's
`Some` carries the new value back. In **uncontrolled** mode (`value` is `None`) `set`
updates internal state *and* calls `onChange` as a notification. Writing a component
against this pair means its body is identical in both modes.
