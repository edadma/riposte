---
title: "Select"
weight: 4
---

A single-select dropdown — built as a custom ARIA combobox (not a native `<select>`), so
it supports typeahead, keyboard navigation, a clear affordance, and skin-styled options.
Options are built with `Opt`; the chosen value follows the same controlled/uncontrolled
model as the other form controls:

```scala
Select(
  options = Seq(
    Opt("us", "United States"),
    Opt("ca", "Canada"),
    Opt("mx", "Mexico", disabled = true),
  ),
  placeholder = "Country",
  clearable = true,
  onChange = setCountry,
)
```

`Opt(value, label, disabled)` defaults `label` to `value`. `Select` takes the usual
`color`/`size`/`disabled`/`invalid`, plus `clearable` (adds a reset button) and `name`
(emits a hidden input so it participates in native form submission). It's fully
keyboard-driven (arrows, Home/End, Enter/Space, Escape, type-to-search) and mirrors its
state to `data-*` (`data-state`, `data-value`, and per-option `data-selected`/`data-active`).

For a menu of *actions* rather than a chosen value, see [Dropdown](/salle/navigation/dropdown/).
