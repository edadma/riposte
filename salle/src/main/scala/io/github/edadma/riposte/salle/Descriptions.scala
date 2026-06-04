package io.github.edadma.riposte.salle

import io.github.edadma.riposte.*

// A description list — the label/value table you reach for on a detail page: a wallpaper's
// resolution, license, palette, and download count, or a user's profile fields. Data-driven like
// [[Tabs]] and [[Breadcrumb]]: pass the `items` (each a `label`, a `value`, and an optional column
// `span`) and salle lays them into rows that fill the column count, renders the `<table>`, and —
// when `bordered` — draws the cell borders.
//
// Two layouts. `Horizontal` (the default) puts each label beside its value (`th` then `td` on one
// row); `Vertical` stacks the row of labels above the row of values. A `title`/`extra` header sits
// above the table, and `colon` appends a `:` after each label.
//
// Responsiveness rides the active [[Skin]]'s media-query support: pass a `responsive` map of
// breakpoint→column-count and the largest matching breakpoint wins, falling back to `column` (which
// is also the behaviour where `matchMedia` is unavailable, e.g. jsdom and SSR).
//
// Accessibility: each label cell is a `th` (`scope=row` horizontal, `scope=col` vertical) carrying
// an id, and each value cell points back to it with `aria-labelledby`, so a value is announced with
// its label. State is mirrored to `data-*`: `data-part=descriptions` on the root, with `data-part`
// on the table and every cell, plus `data-bordered`/`data-layout`/`data-columns` on the root.

/** Which way an [[Descriptions]] row arranges its label and value. `Horizontal` puts the label
  * beside the value (label cell then value cell on one row); `Vertical` stacks a row of labels
  * above a row of values. */
enum DescriptionsLayout:
  case Horizontal
  case Vertical

/** Per-breakpoint column counts for a responsive [[Descriptions]]. Each is the number of columns to
  * use once the viewport reaches that Tailwind breakpoint (`sm` 640px … `xxl` 1536px); the largest
  * matching one wins. Any left `None` is skipped, falling through to the next smaller — and finally
  * to the base `column`. Build with [[descriptionsResponsive]]. */
type DescriptionsResponsive = (
    sm:  Option[Int],
    md:  Option[Int],
    lg:  Option[Int],
    xl:  Option[Int],
    xxl: Option[Int],
)

/** Build a [[DescriptionsResponsive]] map: give the column count at any of the Tailwind breakpoints
  * and leave the rest `None`. */
def descriptionsResponsive(
    sm:  Option[Int] = None,
    md:  Option[Int] = None,
    lg:  Option[Int] = None,
    xl:  Option[Int] = None,
    xxl: Option[Int] = None,
): DescriptionsResponsive =
  (sm = sm, md = md, lg = lg, xl = xl, xxl = xxl)

/** One row in a [[Descriptions]] list: a `label`, its `value`, and how many columns it occupies.
  * `span` is a fixed column count; `filled` makes the item take the rest of its row (the remaining
  * columns), overriding `span`. Build with [[DescItem]]. */
type DescriptionsItem = (
    label:  VNode,
    value:  VNode,
    span:   Int,
    filled: Boolean,
)

/** Build a [[Descriptions]] item: a `label`, a `value`, an optional column `span` (default 1), and
  * `filled` to make it consume the rest of its row instead. */
def DescItem(
    label:  VNode,
    value:  VNode,
    span:   Int     = 1,
    filled: Boolean = false,
): DescriptionsItem =
  (label = label, value = value, span = span, filled = filled)

/** Pack `items` into rows that each fill `columns` columns, returning each item paired with its
  * effective span (clamped to the column count). A `filled` item takes whatever columns remain in
  * its row (or a whole row of its own when it starts one); a fixed-span item that would overflow the
  * current row starts a new one. Pure and total — the rendering is a thin walk over this. */
def descriptionsRows(items: Vector[DescriptionsItem], columns: Int): Vector[Vector[(DescriptionsItem, Int)]] =
  val cols = math.max(1, columns)
  val rows = scala.collection.mutable.ArrayBuffer.empty[Vector[(DescriptionsItem, Int)]]
  var cur  = Vector.empty[(DescriptionsItem, Int)]
  var span = 0

  items.foreach { it =>
    if it.filled then
      if cur.nonEmpty then
        cur = cur :+ (it, cols - span)
        rows += cur
        cur  = Vector.empty
        span = 0
      else rows += Vector((it, cols))
    else
      val eff = math.min(math.max(1, it.span), cols)
      if span + eff > cols then
        if cur.nonEmpty then rows += cur
        cur  = Vector((it, eff))
        span = eff
      else
        cur   = cur :+ (it, eff)
        span += eff
  }

  if cur.nonEmpty then rows += cur
  rows.toVector

private val DescriptionsImpl =
  component[(
      items:      Vector[DescriptionsItem],
      title:      Option[VNode],
      extra:      Option[VNode],
      bordered:   Boolean,
      column:     Int,
      responsive: Option[DescriptionsResponsive],
      size:       Size,
      layout:     DescriptionsLayout,
      colon:      Boolean,
  )] { p =>
    val skin  = useSkin()
    val baseId = useId()

    // Subscribe to every breakpoint unconditionally (hook order must be stable), then resolve the
    // active column count: largest matching breakpoint that the map defines, else the base column.
    val bpSm  = useMediaQuery("(min-width: 640px)")
    val bpMd  = useMediaQuery("(min-width: 768px)")
    val bpLg  = useMediaQuery("(min-width: 1024px)")
    val bpXl  = useMediaQuery("(min-width: 1280px)")
    val bpXxl = useMediaQuery("(min-width: 1536px)")

    val columnCount = p.responsive match
      case None => p.column
      case Some(r) =>
        if bpXxl && r.xxl.isDefined then r.xxl.get
        else if bpXl && r.xl.isDefined then r.xl.get
        else if bpLg && r.lg.isDefined then r.lg.get
        else if bpMd && r.md.isDefined then r.md.get
        else if bpSm && r.sm.isDefined then r.sm.get
        else p.column

    val parts = skin.descriptions(p.bordered, p.size, p.layout)
    val rows  = descriptionsRows(p.items, columnCount)

    def labelId(row: Int, col: Int): String = s"$baseId-r${row}c$col"

    val colonMod: Mod = if p.colon then span(aria("hidden") := true, ":") else NoMod

    // One label cell: a `th` scoped to its row (horizontal) or column (vertical), carrying the id
    // its value cell points back to.
    def labelCell(it: DescriptionsItem, eff: Int, row: Int, col: Int, scope: String): VNode =
      th(
        cls            := parts.label,
        id             := labelId(row, col),
        attr("scope")  := scope,
        attr("colspan") := eff.toString,
        data("part")   := "label",
        it.label,
        colonMod,
      )

    def valueCell(it: DescriptionsItem, colspan: Int, row: Int, col: Int): VNode =
      td(
        cls                := parts.content,
        attr("colspan")    := colspan.toString,
        aria("labelledby") := labelId(row, col),
        data("part")       := "content",
        it.value,
      )

    // Horizontal: label and value sit side by side. A value cell that spans S columns covers
    // S label+value pairs minus its own label cell, i.e. 2·S − 1 table cells.
    val horizontalRows: Seq[VNode] =
      rows.zipWithIndex.map { (row, ri) =>
        val cells: Seq[VNode] = row.zipWithIndex.flatMap { case ((it, eff), ci) =>
          val span = if eff > 1 then eff * 2 - 1 else 1
          Seq(labelCell(it, 1, ri, ci, "row"), valueCell(it, span, ri, ci))
        }
        tr(data("part") := "row", cells)
      }

    // Vertical: a row of labels above the matching row of values, each cell spanning its columns.
    val verticalRows: Seq[VNode] =
      rows.zipWithIndex.flatMap { (row, ri) =>
        val labels: Seq[VNode] = row.zipWithIndex.map { case ((it, eff), ci) => labelCell(it, eff, ri, ci, "col") }
        val values: Seq[VNode] = row.zipWithIndex.map { case ((it, eff), ci) => valueCell(it, eff, ri, ci) }
        Seq(
          tr(data("part") := "label-row", labels),
          tr(data("part") := "value-row", values),
        )
      }

    val bodyRows = p.layout match
      case DescriptionsLayout.Vertical   => verticalRows
      case DescriptionsLayout.Horizontal => horizontalRows

    val headerMod: Mod =
      if p.title.isEmpty && p.extra.isEmpty then NoMod
      else
        div(
          cls          := parts.header,
          data("part") := "header",
          p.title.map(t => (div(cls := parts.title, data("part") := "title", t): Mod)).getOrElse(NoMod),
          p.extra.map(e => (div(cls := parts.extra, data("part") := "extra", e): Mod)).getOrElse(NoMod),
        )

    div(
      cls               := parts.root,
      role              := "group",
      data("part")      := "descriptions",
      data("bordered")  := p.bordered,
      data("layout")    := (p.layout match { case DescriptionsLayout.Vertical => "vertical"; case _ => "horizontal" }),
      data("columns")   := columnCount.toString,
      headerMod,
      table(
        cls          := parts.table,
        data("part") := "table",
        tbody(bodyRows),
      ),
    )
  }

/** A description list: pass the `items` ([[DescItem]] — a label, a value, an optional column span)
  * and salle packs them into rows of `column` columns and renders the `<table>`. `title`/`extra`
  * add a header above it; `bordered` draws cell borders; `layout` ([[DescriptionsLayout]]) puts
  * labels beside (`Horizontal`) or above (`Vertical`) their values; `size` scales the text; `colon`
  * appends a `:` after each label; and `responsive` ([[descriptionsResponsive]]) varies the column
  * count by breakpoint. Classes come from the active [[Skin]]; structure is mirrored to `data-*`.
  *
  * {{{ Descriptions(items = Seq(
  *   DescItem(label = "Resolution", value = "3840×2160"),
  *   DescItem(label = "License",    value = "CC0"),
  *   DescItem(label = "Palette",    value = "warm", span = 2),
  * ), bordered = true) }}} */
def Descriptions(
    items:      Seq[DescriptionsItem],
    title:      Option[VNode]                 = None,
    extra:      Option[VNode]                 = None,
    bordered:   Boolean                       = false,
    column:     Int                           = 3,
    responsive: Option[DescriptionsResponsive] = None,
    size:       Size                          = Size.Md,
    layout:     DescriptionsLayout            = DescriptionsLayout.Horizontal,
    colon:      Boolean                       = true,
): VNode =
  DescriptionsImpl(
    (
      items      = items.toVector,
      title      = title,
      extra      = extra,
      bordered   = bordered,
      column     = column,
      responsive = responsive,
      size       = size,
      layout     = layout,
      colon      = colon,
    ),
  )
