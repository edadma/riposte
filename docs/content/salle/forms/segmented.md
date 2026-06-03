---
title: "Segmented"
weight: 5
---

A single-choice control rendered as one connected strip of segments — the "sort by" or
grid-vs-list switch you reach for instead of a [`Select`](/salle/forms/select/) when there
are only a handful of mutually exclusive options worth showing at once. Data-driven like
[`Tabs`](/salle/navigation/tabs/): pass the `options` and salle renders a `role=radiogroup`
and handles selection, keyboard, and ARIA. Selection follows the usual
[controlled/uncontrolled](/salle/concepts/use-controllable/) model:

```scala
Segmented(
  options = Seq(
    SegmentedOpt("list", "List"),
    SegmentedOpt("grid", "Grid"),
    SegmentedOpt("map", "Map", disabled = true),
  ),
  defaultValue = Some("list"),
  ariaLabel = "View mode",
  onChange = setView,
)
```

`SegmentedOpt(value, label, icon, disabled)` builds an option (`label` is a `VNode`, `icon`
an optional leading slot). `value`/`defaultValue` own or seed the selection (defaulting to
the first option); `size` scales the strip; `block` makes it span the full container width
(segments share the space equally); `disabled` disables the whole control; `ariaLabel` names
the group.

Unlike `Tabs` there is no panel — the control just reports the chosen `value` through
`onChange`. It follows the WAI-ARIA radio-group keyboard pattern with roving `tabindex`
(selection follows focus): only the selected segment is tabbable, Left/Up and Right/Down
move to the adjacent enabled segment and select it (wrapping, skipping disabled), and
Home/End jump to the first/last enabled segment. State is mirrored to `data-*` (`data-value`
on the root; `data-checked`/`data-disabled` per segment).
