package io.github.edadma.riposte.salle

import io.github.edadma.riposte.*
import org.scalajs.dom
import scala.scalajs.js

// A Pinterest-style masonry: tiles of differing heights pack into a fixed number of
// equal-width columns with no ragged gaps. Unlike a CSS multi-column flow (which fills
// one column top-to-bottom before the next, giving column-major reading order), this
// MEASURES each tile and greedily places it into whichever column is currently shortest
// — balanced columns and left-to-right, top-to-bottom order. Because that needs real
// element heights, the component measures in a layout effect and absolutely positions
// each tile; the placement math itself is the pure, browser-free [[layoutMasonry]].
//
// State is mirrored to `data-*`: the container carries `data-columns` (the resolved
// count) and each tile a `data-part=item` with `data-index` and `data-col`.

/** The geometry of one placed tile: its `left`/`top` offset within the container and the
  * `col` it landed in. */
type MasonryPosition = (left: Double, top: Double, col: Int)

/** The computed layout: a `position` per input tile (in input order), the total
  * `containerHeight` the absolutely-positioned tiles need, and the shared `columnWidth`. */
type MasonryLayout = (positions: Seq[MasonryPosition], containerHeight: Double, columnWidth: Double)

/** Place tiles of the given `heights` into `columns` equal-width columns separated by
  * `gap`, within `containerWidth`, using the greedy shortest-column rule (ties go to the
  * lowest-indexed column). Pure and deterministic — no DOM — so the packing is unit
  * tested directly with synthetic heights. `columnWidth = (containerWidth - gap*(n-1))/n`;
  * each tile goes atop the currently shortest column, which then grows by `height + gap`;
  * `containerHeight` is the tallest column minus the trailing gap (0 when there are no
  * tiles). `columns` below 1 is treated as 1.
  */
def layoutMasonry(heights: Seq[Double], columns: Int, gap: Double, containerWidth: Double): MasonryLayout =
  val n           = math.max(1, columns)
  val columnWidth = (containerWidth - gap * (n - 1)) / n
  val colHeights  = Array.fill(n)(0.0)
  val positions = heights.map { h =>
    var col = 0
    var i   = 1
    while i < n do
      if colHeights(i) < colHeights(col) then col = i
      i += 1
    val left = col * (columnWidth + gap)
    val top  = colHeights(col)
    colHeights(col) += h + gap
    (left = left, top = top, col = col)
  }
  val containerHeight = if heights.isEmpty then 0.0 else colHeights.max - gap
  (positions = positions, containerHeight = containerHeight, columnWidth = columnWidth)

// The Tailwind breakpoint floors (px) used to pick a responsive column count, matching
// AsterUI's Masonry so behaviour is identical across the two libraries.
private val MasonryBreakpoints =
  (sm = 640.0, md = 768.0, lg = 1024.0, xl = 1280.0, xxl = 1536.0)

/** How many columns a [[Masonry]] uses. [[MasonryColumns.Fixed]] is a constant count;
  * [[MasonryColumns.Responsive]] picks a count by viewport width against the standard
  * breakpoints (a `-1` field means "unset", and the value fills forward from the largest
  * matching breakpoint down). A bare `Int` converts to `Fixed`. */
enum MasonryColumns:
  case Fixed(n: Int)
  case Responsive(xs: Int, sm: Int, md: Int, lg: Int, xl: Int, xxl: Int)

  /** Resolve to a concrete column count for the given viewport width. */
  def resolve(viewportWidth: Double): Int = this match
    case Fixed(n) => math.max(1, n)
    case Responsive(xs, sm, md, lg, xl, xxl) =>
      // Walk down from the largest breakpoint, taking the first set value at or below the
      // current width; unset levels fall through to a smaller one, then to xs, then 3.
      val bp = MasonryBreakpoints
      val pick =
        if viewportWidth >= bp.xxl && xxl >= 0 then xxl
        else if viewportWidth >= bp.xl && xl >= 0 then xl
        else if viewportWidth >= bp.lg && lg >= 0 then lg
        else if viewportWidth >= bp.md && md >= 0 then md
        else if viewportWidth >= bp.sm && sm >= 0 then sm
        else if xs >= 0 then xs
        else -1
      // If the exact level was unset, fall forward through the lower set levels.
      val resolved =
        if pick >= 0 then pick
        else Seq(xxl, xl, lg, md, sm, xs).filter(_ >= 0).headOption.getOrElse(3)
      math.max(1, resolved)

object MasonryColumns:
  /** Allow passing a plain `Int` where a column spec is expected — it means a fixed count. */
  given Conversion[Int, MasonryColumns] = Fixed(_)

private val MasonryImpl =
  component[
    (
        columns: MasonryColumns,
        gap: Double,
        className: String,
        children: Vector[VNode],
    ),
  ] { p =>
    val containerRef = useRef[dom.Element | Null](null)
    // One slot per child, filled by each tile's callback ref as it mounts.
    val itemRefs = useRef[js.Array[dom.Element | Null]](js.Array())

    val (positions, setPositions, _)         = useState[Seq[MasonryPosition]](Seq.empty)
    val (containerHeight, setHeight, _)       = useState(0.0)
    val (columnWidth, setColumnWidth, _)      = useState(0.0)
    val (resolvedColumns, setResolvedCols, _) = useState(0)
    val (laidOut, setLaidOut, _)              = useState(false)

    val count = p.children.length

    // Measure the container and every tile, then run the pure packing. jsdom reports
    // offsetWidth/offsetHeight as 0, so when the container has no measurable width we
    // leave the tiles in normal flow (visible, not absolutely positioned) — a graceful,
    // if unbalanced, fallback that a real browser never hits.
    def measure(): Unit =
      val container = containerRef.current
      if container == null then ()
      else
        val cw = container.asInstanceOf[dom.html.Element].offsetWidth.toDouble
        val n  = p.columns.resolve(dom.window.innerWidth.toDouble)
        setResolvedCols(n)
        if cw <= 0 then () // unmeasurable (jsdom) — keep normal-flow fallback
        else
          val heights =
            (0 until count).map { i =>
              val el = itemRefs.current(i)
              if el == null then 0.0 else el.asInstanceOf[dom.html.Element].offsetHeight.toDouble
            }
          val layout = layoutMasonry(heights, n, p.gap, cw)
          setPositions(layout.positions)
          setHeight(layout.containerHeight)
          setColumnWidth(layout.columnWidth)
          setLaidOut(true)

    // Re-measure after layout (refs are populated by then) and whenever the child count
    // or gap changes. A resize of the container (and, via the fallback, the viewport)
    // also triggers a re-measure so the column count and packing stay correct.
    useLayoutEffect(
      () =>
        measure()
        noCleanup
      ,
      Array(count, p.gap),
    )
    useResizeObserver(containerRef, () => measure())
    useEventListener(dom.window, "resize", _ => measure())

    val containerStyle: Map[String, String] =
      Map("position" -> "relative") ++ (if laidOut then Map("height" -> s"${containerHeight}px") else Map.empty)

    val cls0 = if p.className.isEmpty then "salle-masonry" else s"salle-masonry ${p.className}"

    div(
      ref             := containerRef,
      cls             := cls0,
      data("columns") := resolvedColumns,
      style           := containerStyle,
      p.children.zipWithIndex.map { (child, i) =>
        val itemStyle: Map[String, String] =
          if laidOut && i < positions.length then
            val pos = positions(i)
            Map(
              "position"   -> "absolute",
              "left"       -> s"${pos.left}px",
              "top"        -> s"${pos.top}px",
              "width"      -> s"${columnWidth}px",
              "visibility" -> "visible",
            )
          else Map("position" -> "relative", "visibility" -> "visible")
        div(
          ref          := ((el: dom.Element | Null) => itemRefs.current(i) = el),
          data("part") := "item",
          data("index") := i,
          data("col")  := (if laidOut && i < positions.length then positions(i).col else 0),
          style        := itemStyle,
          child,
        )
      },
    )
  }

/** A measured, balanced masonry layout. Pass tiles of differing heights as children and
  * they pack into `columns` equal-width columns (the shortest column always takes the
  * next tile), separated by `gap` pixels. `columns` is a fixed count here; use
  * [[MasonryResponsive]] for a per-breakpoint count. The layout recomputes on container
  * and viewport resize. Reading order is left-to-right, top-to-bottom (unlike CSS
  * columns). State is mirrored to `data-*`. `Masonry(columns = 3, gap = 16)(tiles*)`.
  */
def Masonry(
    columns:   Int    = 3,
    gap:       Double  = 16.0,
    className: String  = "",
)(children: VNode*): VNode =
  MasonryImpl(
    (
      columns = MasonryColumns.Fixed(columns),
      gap = gap,
      className = className,
      children = children.toVector,
    ),
  )

/** A [[Masonry]] whose column count varies by viewport width. Each of `xs`/`sm`/`md`/
  * `lg`/`xl`/`xxl` is a column count at that Tailwind breakpoint; leave one at its `-1`
  * default to inherit the next smaller level (e.g. `MasonryResponsive(xs = 1, sm = 2, md
  * = 3)` uses 3 columns at md and up). `MasonryResponsive(xs = 1, sm = 2, lg = 4)(tiles*)`.
  */
def MasonryResponsive(
    xs:        Int    = -1,
    sm:        Int    = -1,
    md:        Int    = -1,
    lg:        Int    = -1,
    xl:        Int    = -1,
    xxl:       Int    = -1,
    gap:       Double  = 16.0,
    className: String  = "",
)(children: VNode*): VNode =
  MasonryImpl(
    (
      columns = MasonryColumns.Responsive(xs, sm, md, lg, xl, xxl),
      gap = gap,
      className = className,
      children = children.toVector,
    ),
  )
