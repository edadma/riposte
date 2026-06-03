---
title: "Tabs"
weight: 3
---

A row of tabs that switches between panels of content — the facets of a detail view,
settings sections, categories. Data-driven: you pass the `items` (each a key, a label, and
the panel content) and salle renders the `role=tablist` strip and the active `role=tabpanel`,
handling selection, keyboard, and the ARIA wiring:

```scala
Tabs(
  items = Seq(
    Tab("overview", "Overview", div(p("The big picture."))),
    Tab("specs", "Specs", div(p("The details."))),
    Tab("legacy", "Legacy", div(p("Old stuff.")), disabled = true),
  ),
)
```

Items are built with `Tab(key, label, content, icon, disabled)`. Selection is
**controllable** — pass `activeKey` to own it and react to `onChange` — or self-managed from
`defaultActiveKey` (defaulting to the first item):

```scala
val (active, setActive, _) = useState("overview")

Tabs(
  items = …,
  activeKey = Some(active),
  onChange = setActive,
)
```

`variant` picks the look — `TabsVariant.Border` (an underline under the active tab, the
default), `Box` (each tab a filled box), or `Lift` (the active tab lifts to meet the panel).
`size` scales the strip, and `position` (`TabsPosition.Top` / `Bottom`) places it above or
below its panel.

It follows the WAI-ARIA tabs pattern with **automatic activation**: only the active tab is in
the tab order (roving `tabindex`), Left/Right move focus to the adjacent enabled tab and
activate it (wrapping, skipping disabled), and Home/End jump to the first/last enabled tab.
State is mirrored to `data-*`. The compound (children-as-panels) pattern is out of scope for
now.
