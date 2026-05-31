package io.github.edadma.riposte.salle

import io.github.edadma.riposte.*

// A 24- (or 30-) column grid, in the Ant Design tradition: a `Row` lays out a fixed
// number of equal tracks, and each `Col` spans some of them, with per-breakpoint spans,
// offsets, and ordering. It's the precise-layout counterpart to [[Masonry]] (which packs
// by measured height); here the author states spans explicitly and the columns line up.
//
// AsterUI realizes this with hard-coded Tailwind classes (`col-span-7`, `md:col-span-12`).
// salle can't depend on Tailwind under its native skin, so geometry is written as inline
// CSS custom properties (`--sc-base`, `--sc-md`, …) that the stylesheet turns into
// `grid-column` spans — including the responsive ones, via `@media` rules keyed off the
// same properties. Crucially the spans live in CSS, NOT inline `grid-column`: an inline
// value would beat the `@media` rules and defeat responsiveness. Row/Col share their
// column count and gutter through a context, exactly as AsterUI's GridContext does.
//
// State is mirrored to `data-*`: the Row carries `data-cols`, each Col `data-span`.

// The column count + gutter a Row publishes to its Cols. A named tuple (never a case
// class), carried by a riposte context.
private type GridConfig = (cols: Int, gutterX: Double, gutterY: Double)

private val GridContext: Context[GridConfig] =
  createContext((cols = 24, gutterX = 16.0, gutterY = 0.0))

private val RowImpl =
  component[
    (
        cols: Int,
        gutterX: Double,
        gutterY: Double,
        justify: String,
        align: String,
        className: String,
        children: Vector[VNode],
    ),
  ] { p =>
    // justify maps to justify-content, align to align-items. (AsterUI used
    // justify-items, but `between`/`around`/`evenly` are only valid on justify-content —
    // so this is the correct CSS for the documented values.)
    val justifyCss = p.justify match
      case "start"   => "flex-start"
      case "end"     => "flex-end"
      case "center"  => "center"
      case "between" => "space-between"
      case "around"  => "space-around"
      case "evenly"  => "space-evenly"
      case _         => ""
    val alignCss = p.align match
      case "start"    => "flex-start"
      case "end"      => "flex-end"
      case "center"   => "center"
      case "stretch"  => "stretch"
      case "baseline" => "baseline"
      case _          => ""

    // The classic gutter technique: the Row pulls its edges in by half the horizontal
    // gutter (negative margin) and each Col pads by the same, so the outer edges align
    // while inner columns are separated by the full gutter. Vertical gutter is row-gap.
    val rowStyle: Map[String, String] =
      Map(
        "display"               -> "grid",
        "grid-template-columns" -> s"repeat(${p.cols}, minmax(0, 1fr))",
        "width"                 -> "100%",
      )
        ++ (if p.gutterX > 0 then
              Map("margin-left" -> s"-${p.gutterX / 2}px", "margin-right" -> s"-${p.gutterX / 2}px")
            else Map.empty)
        ++ (if p.gutterY > 0 then Map("row-gap" -> s"${p.gutterY}px") else Map.empty)
        ++ (if justifyCss.nonEmpty then Map("justify-content" -> justifyCss) else Map.empty)
        ++ (if alignCss.nonEmpty then Map("align-items" -> alignCss) else Map.empty)

    val cls0 = if p.className.isEmpty then "salle-row" else s"salle-row ${p.className}"

    GridContext.provide(
      (cols = p.cols, gutterX = p.gutterX, gutterY = p.gutterY),
      div(
        cls          := cls0,
        data("cols") := p.cols,
        style        := rowStyle,
        p.children,
      ),
    )
  }

private val ColImpl =
  component[
    (
        span: Int,
        offset: Int,
        order: Int,
        xs: Int,
        sm: Int,
        md: Int,
        lg: Int,
        xl: Int,
        xxl: Int,
        className: String,
        children: Vector[VNode],
    ),
  ] { p =>
    val cfg = useContext(GridContext)

    // The base span: explicit `span`, else `xs`, else the full row width. The responsive
    // levels fill forward — an unset breakpoint inherits the next smaller one — so the
    // emitted custom properties always carry a concrete span for every breakpoint.
    val base = if p.span > 0 then p.span else if p.xs > 0 then p.xs else cfg.cols
    val rsm  = if p.sm > 0 then p.sm else base
    val rmd  = if p.md > 0 then p.md else rsm
    val rlg  = if p.lg > 0 then p.lg else rmd
    val rxl  = if p.xl > 0 then p.xl else rlg
    val rxxl = if p.xxl > 0 then p.xxl else rxl

    val colStyle: Map[String, String] =
      Map(
        "--sc-base" -> base.toString,
        "--sc-sm"   -> rsm.toString,
        "--sc-md"   -> rmd.toString,
        "--sc-lg"   -> rlg.toString,
        "--sc-xl"   -> rxl.toString,
        "--sc-xxl"  -> rxxl.toString,
      )
        ++ (if p.offset > 0 then Map("--sc-start" -> (p.offset + 1).toString) else Map.empty)
        ++ (if p.order > 0 then Map("order" -> p.order.toString) else Map.empty)
        ++ (if cfg.gutterX > 0 then
              Map("padding-left" -> s"${cfg.gutterX / 2}px", "padding-right" -> s"${cfg.gutterX / 2}px")
            else Map.empty)

    val cls0 = if p.className.isEmpty then "salle-col" else s"salle-col ${p.className}"

    div(
      cls          := cls0,
      data("span") := base,
      style        := colStyle,
      p.children,
    )
  }

/** A grid row: lays out `cols` equal columns (24 by default; 30 for finer control) and
  * spaces its [[Col]] children by `gutterX` horizontally and `gutterY` vertically (the
  * `[x, y]` of AntD's gutter, as two params). `justify` aligns columns along the row
  * (`start`/`end`/`center`/`between`/`around`/`evenly`); `align` aligns them across it
  * (`start`/`end`/`center`/`stretch`/`baseline`). Pass `Col`s as children; the row shares
  * its column count and gutter with them through context. `Row(gutterX = 24)(cols*)`.
  */
def Row(
    cols:      Int    = 24,
    gutterX:   Double  = 16.0,
    gutterY:   Double  = 0.0,
    justify:   String  = "",
    align:     String  = "",
    className: String  = "",
)(children: VNode*): VNode =
  RowImpl(
    (
      cols = cols,
      gutterX = gutterX,
      gutterY = gutterY,
      justify = justify,
      align = align,
      className = className,
      children = children.toVector,
    ),
  )

/** A grid column inside a [[Row]]. `span` is how many of the row's columns it occupies
  * (defaulting to the full row); `offset` pushes it that many columns to the right;
  * `order` overrides its visual position. The `xs`/`sm`/`md`/`lg`/`xl`/`xxl` params set
  * the span at each breakpoint (≥640/768/1024/1280/1536px; `xs` is the base), each
  * inheriting the next smaller when left unset — so `Col(xs = 24, sm = 12, md = 8)` is
  * full width on phones, half on small screens, a third from medium up. `Col(span = 8,
  * offset = 8)(content*)`.
  */
def Col(
    span:      Int    = 0,
    offset:    Int    = 0,
    order:     Int    = 0,
    xs:        Int    = 0,
    sm:        Int    = 0,
    md:        Int    = 0,
    lg:        Int    = 0,
    xl:        Int    = 0,
    xxl:       Int    = 0,
    className: String  = "",
)(children: VNode*): VNode =
  ColImpl(
    (
      span = span,
      offset = offset,
      order = order,
      xs = xs,
      sm = sm,
      md = md,
      lg = lg,
      xl = xl,
      xxl = xxl,
      className = className,
      children = children.toVector,
    ),
  )
