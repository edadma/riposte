package io.github.edadma.riposte.salle

import io.github.edadma.riposte.*

// Placeholder shapes shown while real content loads — the grey, gently shimmering blocks
// that stand in for an image, a line of text, or a tile. They reserve the content's space
// so the layout doesn't jump when the real thing arrives, and the motion signals "loading"
// without a spinner. Three pieces:
//
//   • Skeleton       — one primitive block (any width/height, optionally a circle).
//   • SkeletonText   — a stack of lines approximating a paragraph (last line shorter).
//   • SkeletonImage  — an aspect-ratio tile with a faint picture glyph, for grid cells.
//
// The shimmering block is the one skin-dependent piece (SalleSkin sweeps a sheen, DaisyUI
// pulses), so it goes through `Skin.skeleton`. The structural arrangement of the composites
// (line stacking, glyph centring, aspect ratio) is plain inline geometry, so it renders the
// same under any skin without depending on salle.css being loaded.
//
// State is mirrored to `data-*`: every block carries `data-part=skeleton` + `data-animated`
// + `data-shape` (rect|circle); composites add `data-part` (skeleton-text|skeleton-image)
// and their pieces carry `data-part` (line|icon) with `data-index` on lines. All are
// decorative, so each root is `aria-hidden` — assistive tech announces the eventual content,
// not the placeholder.

private val SkeletonImpl =
  component[(width: String, height: String, circle: Boolean, rounded: Boolean, animated: Boolean, className: String)] {
    p =>
      val skin = useSkin()

      // Geometry is inline so a block can be any size; the radius is only overridden when it
      // must differ from the skin's default — a circle, or an explicitly square block.
      var sty = Map.empty[String, String]
      if p.width.nonEmpty then sty += "width"   -> p.width
      if p.height.nonEmpty then sty += "height" -> p.height
      if p.circle then sty += "border-radius" -> "50%"
      else if !p.rounded then sty += "border-radius" -> "0"

      div(
        cls              := skin.skeleton(p.animated) + (if p.className.nonEmpty then " " + p.className else ""),
        data("part")     := "skeleton",
        data("animated") := p.animated,
        data("shape")    := (if p.circle then "circle" else "rect"),
        aria("hidden")   := true,
        style            := sty,
      )
  }

/** One placeholder block. Give it any `width`/`height` (CSS lengths like `"100%"`,
  * `"1.5rem"`, `"200px"`); `circle` makes it round (pair with equal width/height for an
  * avatar), `rounded` (default) uses the skin's corner radius and `rounded = false` squares
  * it off, and `animated` (default) shows the loading shimmer. The look comes from the active
  * [[Skin]]; it is decorative (`aria-hidden`) with a `data-*` state mirror. */
def Skeleton(
    width:     String  = "100%",
    height:    String  = "1rem",
    circle:    Boolean = false,
    rounded:   Boolean = true,
    animated:  Boolean = true,
    className: String   = "",
): VNode =
  SkeletonImpl(
    (width = width, height = height, circle = circle, rounded = rounded, animated = animated, className = className),
  )

private val SkeletonTextImpl =
  component[(lines: Int, lastWidth: String, animated: Boolean, className: String)] { p =>
    val skin = useSkin()
    val n    = if p.lines < 1 then 1 else p.lines
    div(
      cls            := "salle-skeleton-text" + (if p.className.nonEmpty then " " + p.className else ""),
      data("part")   := "skeleton-text",
      aria("hidden") := true,
      style := Map("display" -> "flex", "flex-direction" -> "column", "gap" -> "0.6rem"),
      (0 until n).map { i =>
        // The last line of a multi-line block is shortened so it reads as a paragraph
        // rather than a filled rectangle.
        val w = if i == n - 1 && n > 1 then p.lastWidth else "100%"
        div(
          cls              := skin.skeleton(p.animated),
          data("part")     := "line",
          data("index")    := i,
          data("animated") := p.animated,
          style            := Map("width" -> w, "height" -> "0.85rem"),
        )
      },
    )
  }

/** A paragraph placeholder: `lines` stacked [[Skeleton]] bars. The last line is shortened to
  * `lastWidth` so the block reads as text rather than a filled rectangle (set `lastWidth =
  * "100%"` for a flush block). `animated` toggles the shimmer on every line. Decorative
  * (`aria-hidden`); root `data-part=skeleton-text`, each bar `data-part=line` + `data-index`. */
def SkeletonText(
    lines:     Int     = 3,
    lastWidth: String  = "60%",
    animated:  Boolean = true,
    className: String   = "",
): VNode =
  SkeletonTextImpl((lines = lines, lastWidth = lastWidth, animated = animated, className = className))

private val SkeletonImageImpl =
  component[(ratio: String, rounded: Boolean, animated: Boolean, className: String)] { p =>
    val skin = useSkin()
    var sty = Map(
      "display"         -> "flex",
      "align-items"     -> "center",
      "justify-content" -> "center",
      "width"           -> "100%",
    )
    if p.ratio.nonEmpty then sty += "aspect-ratio" -> p.ratio
    if !p.rounded then sty += "border-radius" -> "0"
    div(
      cls              := skin.skeleton(p.animated) + (if p.className.nonEmpty then " " + p.className else ""),
      data("part")     := "skeleton-image",
      data("animated") := p.animated,
      aria("hidden")   := true,
      style            := sty,
      span(cls := "salle-skeleton-image__icon", data("part") := "icon", unsafeHtml(ImageGlyph)),
    )
  }

/** An image placeholder sized by `ratio` (a CSS `aspect-ratio` like `"16/10"` or `"1/1"`),
  * with a faint picture glyph centred in it — the right stand-in for a gallery tile before
  * its thumbnail loads, since it reserves exactly the cell's space. `rounded` (default) uses
  * the skin radius; `animated` toggles the shimmer. Decorative (`aria-hidden`); root
  * `data-part=skeleton-image`, glyph `data-part=icon`. */
def SkeletonImage(
    ratio:     String  = "16/10",
    rounded:   Boolean = true,
    animated:  Boolean = true,
    className: String   = "",
): VNode =
  SkeletonImageImpl((ratio = ratio, rounded = rounded, animated = animated, className = className))

// A simple "image" glyph (Feather image), drawn with currentColor at low opacity by the CSS;
// injected as trusted static innerHTML so it parses into correctly-namespaced SVG nodes.
private val ImageGlyph =
  """<svg xmlns="http://www.w3.org/2000/svg" width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="3" y="3" width="18" height="18" rx="2" ry="2"></rect><circle cx="8.5" cy="8.5" r="1.5"></circle><polyline points="21 15 16 10 5 21"></polyline></svg>"""
