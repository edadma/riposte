---
title: "Spinner, Progress & RadialProgress"
weight: 5
---

Busy indicators, split by whether you know the fraction. **`Spinner`** is *indeterminate* — a
small animated mark for work of unknown duration; **`Progress`** is a linear bar (determinate
when you pass a `value`, indeterminate when you don't); **`RadialProgress`** is a ring with the
percentage in its centre:

```scala
Spinner()                                  // a bare mark + off-screen "Loading"
Spinner(kind = SpinnerType.Dots, tip = "Fetching…")

Progress(value = Some(70))                 // 70 %
Progress()                                 // indeterminate animation

RadialProgress(value = 33)                 // a ring showing "33%"
```

`Spinner` also has an **overlay mode**: give it `children` and it floats the mark over them,
dimming and disabling the content while `spinning`, then reveals it when `spinning = false`:

```scala
Spinner(spinning = loading)(
  div(/* the panel being loaded */),
)
```

`SpinnerType` picks the animation (`Spinner`/`Dots`/`Ring`/`Ball`/`Bars`/`Infinity`), and
`Color`/`Size` tint and scale it. `Progress` renders the native `<progress>` element, so
assistive tech reads the value for free; `RadialProgress` exposes `role="progressbar"` with
`aria-valuenow`, takes `size`/`thickness` overrides, and shows custom centre `children` (or
`showValue = false` for a bare ring). All three take their look from the active skin. For a
placeholder while content loads (rather than a busy indicator), see
[`Skeleton`](/salle/display/skeleton/).
