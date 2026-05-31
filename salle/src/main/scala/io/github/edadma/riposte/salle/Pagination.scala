package io.github.edadma.riposte.salle

import io.github.edadma.riposte.*

// A page navigator: « prev, a run of page-number buttons with … gaps when there are many
// pages, next ». It is *controlled* — the caller owns `current` and is told of a requested
// move via `onChange(page)` — so it composes with the router's `?page=` query or an atom.
//
// The interesting part, the "which page buttons to show" decision, is a pure function
// ([[paginationRange]]) so it is unit-testable without a DOM, exactly like Masonry's
// `layoutMasonry`. The component is a thin shell over it.
//
// State is mirrored to `data-*`: the nav carries `data-part=pagination` + `data-current` +
// `data-pages`; each control carries `data-part` (prev|next|page|dots) with `data-page` on
// number buttons and `data-active` on the current one.

/** One slot in a rendered pagination strip: either a concrete `Page` to jump to, or a
  * `Dots` gap standing in for a collapsed run of pages. A plain enum, never a case class. */
enum PageItem:
  case Page(n: Int)
  case Dots

/** Decide which page buttons to show for `current` of `totalPages`, keeping `siblingCount`
  * neighbours on each side of the current page plus the first and last, collapsing the rest
  * into [[PageItem.Dots]]. Pure and total: returns `Page(1)…Page(totalPages)` verbatim when
  * they all fit (no dots), and never emits dots adjacent to the edge they'd replace a single
  * page for (a lone gap shows the page instead). `current` is clamped into range; a
  * `totalPages <= 0` yields an empty vector.
  *
  * The window is `siblingCount` on each side of current, plus current, plus first and last,
  * plus the two dots slots — `2*siblingCount + 5` items. If that covers every page, all are
  * shown; otherwise dots appear on whichever side has hidden pages.
  */
def paginationRange(current: Int, totalPages: Int, siblingCount: Int = 1): Vector[PageItem] =
  if totalPages <= 0 then Vector.empty
  else if totalPages == 1 then Vector(PageItem.Page(1))
  else
    val cur = if current < 1 then 1 else if current > totalPages then totalPages else current

    // If the full window is as large as the page count, just show every page.
    val windowSize = siblingCount * 2 + 5
    if windowSize >= totalPages then Vector.tabulate(totalPages)(i => PageItem.Page(i + 1))
    else
      val left  = math.max(cur - siblingCount, 1)
      val right = math.min(cur + siblingCount, totalPages)

      // Dots are worth showing only when they hide more than one page; right next to the
      // edge (index 2 / totalPages-1) a single page would be hidden, so show it instead.
      val leftDots  = left > 3
      val rightDots = right < totalPages - 2

      val b = Vector.newBuilder[PageItem]
      if !leftDots && rightDots then
        // Near the start: show a longer left run, dots, last.
        val leftCount = 3 + siblingCount * 2
        for i <- 1 to leftCount do b += PageItem.Page(i)
        b += PageItem.Dots
        b += PageItem.Page(totalPages)
      else if leftDots && !rightDots then
        // Near the end: first, dots, a longer right run.
        val rightCount = 3 + siblingCount * 2
        b += PageItem.Page(1)
        b += PageItem.Dots
        for i <- totalPages - rightCount + 1 to totalPages do b += PageItem.Page(i)
      else
        // In the middle: first, dots, the sibling window, dots, last.
        b += PageItem.Page(1)
        b += PageItem.Dots
        for i <- left to right do b += PageItem.Page(i)
        b += PageItem.Dots
        b += PageItem.Page(totalPages)
      b.result()

/** The number of pages for `total` items at `pageSize` per page (at least 0; `ceil`). */
def pageCount(total: Int, pageSize: Int): Int =
  if pageSize <= 0 || total <= 0 then 0
  else (total + pageSize - 1) / pageSize

private val PaginationImpl =
  component[
    (
        current: Int,
        total: Int,
        pageSize: Int,
        siblingCount: Int,
        size: Size,
        disabled: Boolean,
        simple: Boolean,
        onChange: Int => Unit,
    ),
  ] { p =>
    val skin  = useSkin()
    val parts = skin.pagination(p.size)

    val totalPages = pageCount(p.total, p.pageSize)
    val cur        = if p.current < 1 then 1 else if p.current > totalPages then totalPages else p.current

    // Guard every move: ignore out-of-range, same-page, and disabled requests.
    def go(page: Int): Unit =
      if !p.disabled && page >= 1 && page <= totalPages && page != cur then p.onChange(page)

    def navButton(part: String, label: String, glyph: String, target: Int, off: Boolean): VNode =
      button(
        cls            := parts.item,
        typ            := "button",
        data("part")   := part,
        aria("label")  := label,
        disabled       := off,
        onClick        := (_ => go(target)),
        glyph,
      )

    if totalPages <= 0 then span(cls := parts.root, data("part") := "pagination", data("current") := 0, data("pages") := 0)
    else if p.simple then
      nav(
        cls             := parts.root,
        role            := "navigation",
        aria("label")   := "Pagination",
        data("part")    := "pagination",
        data("current") := cur,
        data("pages")   := totalPages,
        navButton("prev", "Previous page", "«", cur - 1, cur <= 1 || p.disabled),
        span(cls := parts.dots, data("part") := "status", cur.toString + " / " + totalPages.toString),
        navButton("next", "Next page", "»", cur + 1, cur >= totalPages || p.disabled),
      )
    else
      val items: Seq[VNode] = paginationRange(cur, totalPages, p.siblingCount).map { item =>
        item match
          case PageItem.Dots =>
            span(cls := parts.dots, data("part") := "dots", aria("hidden") := true, "…")
          case PageItem.Page(n) =>
            val active = n == cur
            button(
              cls            := (if active then parts.item + " " + parts.active else parts.item),
              typ            := "button",
              data("part")   := "page",
              data("page")   := n,
              data("active")  := active,
              aria("label")  := ("Page " + n.toString),
              aria("current") := (if active then "page" else ""),
              disabled       := p.disabled,
              onClick        := (_ => go(n)),
              n.toString,
            )
      }

      nav(
        cls             := parts.root,
        role            := "navigation",
        aria("label")   := "Pagination",
        data("part")    := "pagination",
        data("current") := cur,
        data("pages")   := totalPages,
        navButton("prev", "Previous page", "«", cur - 1, cur <= 1 || p.disabled),
        items,
        navButton("next", "Next page", "»", cur + 1, cur >= totalPages || p.disabled),
      )
  }

/** A controlled page navigator. The caller owns `current` (1-indexed) and is told of a
  * requested move via `onChange(page)`; the strip is computed from `total` items at
  * `pageSize` per page. `siblingCount` sets how many page buttons flank the current one
  * before the rest collapse into `…`. `simple` renders a compact prev / "n / m" / next bar
  * instead of the full strip; `disabled` greys the whole control; [[Size]] scales it.
  * Classes come from the active [[Skin]]; state is mirrored to `data-*`. */
def Pagination(
    current:      Int          = 1,
    total:        Int          = 0,
    pageSize:     Int          = 10,
    siblingCount: Int          = 1,
    size:         Size         = Size.Md,
    disabled:     Boolean      = false,
    simple:       Boolean      = false,
    onChange:     Int => Unit  = _ => (),
): VNode =
  PaginationImpl(
    (
      current = current,
      total = total,
      pageSize = pageSize,
      siblingCount = siblingCount,
      size = size,
      disabled = disabled,
      simple = simple,
      onChange = onChange,
    ),
  )
