---
title: "Dropdown"
weight: 2
---

A menu button: a trigger that opens a `role=menu` of actions. Data-driven — you pass the
`items` and salle renders the popup and handles open/close, keyboard, and dismissal. Unlike
[`Select`](/salle/forms/select/) it has no chosen value; picking an item runs it and closes:

```scala
Dropdown(
  items = Seq(
    MenuItem("edit", "Edit", onSelect = () => edit()),
    MenuItem("dup", "Duplicate", onSelect = () => duplicate()),
    MenuDivider,
    MenuItem("del", "Delete", danger = true, onSelect = () => remove()),
  ),
  onSelect = key => log(key),
)("Actions")
```

Items are built with `MenuItem(key, label, icon, disabled, danger, onSelect)` and
`MenuDivider`. Choosing an item runs its own `onSelect` and reports its `key` to the
dropdown-level `onSelect`. It's fully keyboard-driven (arrows skip dividers and disabled
items with wrap, Home/End, Enter/Space, Escape refocuses the trigger, Tab closes), dismisses
on outside click (via core [`useClickOutside`](/guide/hooks/#dom-hooks)), and carries the
full `aria-haspopup`/`expanded`/`activedescendant` wiring. Submenus and hover-to-open are out
of scope for now.
