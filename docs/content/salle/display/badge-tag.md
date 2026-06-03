---
title: "Badge, Tag & CheckableTag"
weight: 3
---

Three label pills built on one look. **`Badge`** is a non-interactive label — a category, a
resolution, "New". **`Tag`** adds a leading `icon` slot, an `onClick`, and an optional close
button (`closable`/`onClose`). **`CheckableTag`** is a controlled filter chip that toggles
on click:

```scala
Badge(color = Color.Success)("Available")
Badge(color = Color.Error, pill = true)("4K")
Badge(color = Color.Primary, dot = true)()        // a bare status dot, no text

Tag(closable = true, onClose = () => remove(id))("Draft")
Tag(icon = Some(span("★")), onClick = () => open())("Featured")

// A controlled filter chip:
val (on, setOn, _) = useState(false)
CheckableTag(checked = on, onChange = setOn)("Nature")
```

`Color`, `BadgeVariant` (`Solid`/`Outline`/`Soft`/`Dash`), and `Size` set the look; `pill`
rounds to a full capsule and `dot` collapses a Badge to a tiny indicator circle. `Tag`'s
close click is kept from bubbling to its `onClick`. `CheckableTag` reads as a filled primary
pill when checked and a quiet neutral one when not, and is fully keyboard-operable
(`role=button`, Enter/Space, `aria-pressed`).
