---
title: "Pagination"
weight: 1
---

A controlled page navigator: prev / numbered buttons with collapsing `…` gaps / next. You
own `current` (1-indexed) and react to `onChange(page)`; the strip is computed from `total`
items at `pageSize` per page:

```scala
val (page, setPage, _) = useState(1)
Pagination(current = page, total = 240, pageSize = 20, onChange = setPage)

// A compact "‹ 3 / 12 ›" bar:
Pagination(current = page, total = 240, pageSize = 20, simple = true, onChange = setPage)
```

`siblingCount` sets how many page buttons flank the current one before the rest collapse to
`…`; `simple` swaps the full strip for a compact prev / "n / m" / next bar; `disabled` greys
the control; `Size` scales it. The "which buttons to show" decision is a pure, exported
function — `paginationRange(current, totalPages, siblingCount)` returning a
`Vector[PageItem]` (`Page(n)` / `Dots`) — alongside `pageCount(total, pageSize)`, so you can
unit-test or reuse the logic without a DOM.
