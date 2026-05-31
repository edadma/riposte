package io.github.edadma.riposte.salle

import io.github.edadma.riposte.*
import org.scalajs.dom

// The core unit of a media gallery: a single image tile that loads lazily, shows a
// skeleton while it loads, fades in when ready, and falls back (or shows an error
// placeholder) when the source fails. Optional slots overlay a badge (a resolution or
// "4K" tag) and hover content (download / favourite actions). It is built to be the
// repeated cell of a grid, so the expensive part — the full image — only loads once the
// tile nears the viewport ([[useIntersectionObserver]]).
//
// Every meaningful state is mirrored to `data-*` for skin-independent tests and
// consumers: the root carries `data-state` (loading|loaded|error) and `data-inview`;
// sub-elements carry `data-part` (frame|img|skeleton|error|badge|overlay).

/** How the image fills its frame, mapping to CSS `object-fit`. `Cover` (the default)
  * fills and crops — the right choice for a uniform wallpaper grid; `Contain` letterboxes
  * to show the whole image; `Fill` stretches; `ScaleDown` is `Contain` but never upscales. */
enum ImageFit:
  case Cover, Contain, Fill, ScaleDown

  /** The CSS `object-fit` keyword. */
  def css: String = this match
    case Cover     => "cover"
    case Contain   => "contain"
    case Fill      => "fill"
    case ScaleDown => "scale-down"

private val ImageCardImpl =
  component[
    (
        src: String,
        alt: String,
        fallback: String,
        fit: ImageFit,
        ratio: String,
        rounded: Boolean,
        lazyLoad: Boolean,
        badge: Option[VNode],
        overlay: Option[VNode],
        onClick: () => Unit,
        onLoad: () => Unit,
        onError: () => Unit,
    ),
  ] { p =>
    val skin  = useSkin()
    val parts = skin.imageCard(p.rounded)

    val (loaded, setLoaded, _)                 = useState(false)
    val (failed, setFailed, _)                 = useState(false)
    val (fallbackFailed, setFallbackFailed, _) = useState(false)

    val container = useRef[dom.Element | Null](null)
    val inView    = useIntersectionObserver(container, once = true)

    // Reset the load lifecycle if the source changes (a recycled tile in a virtualized
    // grid). On mount this is a no-op since the cells already start false.
    useEffect(
      () =>
        setLoaded(false)
        setFailed(false)
        setFallbackFailed(false)
        noCleanup
      ,
      Array(p.src),
    )

    val showingFallback = failed && p.fallback.nonEmpty && !fallbackFailed
    val effectiveSrc    = if showingFallback then p.fallback else p.src
    val hardError       = (failed && p.fallback.isEmpty) || fallbackFailed
    val shouldLoad      = !p.lazyLoad || inView

    val state =
      if hardError then "error"
      else if loaded then "loaded"
      else "loading"

    val imgNode: Mod =
      if hardError then
        div(
          cls           := parts.error,
          data("part")  := "error",
          role          := "img",
          aria("label") := (if p.alt.nonEmpty then p.alt else "Image failed to load"),
          unsafeHtml(BrokenImageIcon),
        )
      else if shouldLoad then
        img(
          cls          := parts.img,
          data("part") := "img",
          src          := effectiveSrc,
          alt          := p.alt,
          style := Map(
            "object-fit" -> p.fit.css,
            "opacity"    -> (if loaded then "1" else "0"),
          ),
          onLoad := (_ =>
            setLoaded(true)
            p.onLoad()
          ),
          onError := (_ =>
            if showingFallback then setFallbackFailed(true)
            else if !failed then
              setFailed(true)
              p.onError()
          ),
        )
      else NoMod

    // The skeleton covers both "not yet scrolled into view" and "loading" — any time
    // there is no painted image and we're not in the error state.
    val skeletonNode: Mod =
      if !loaded && !hardError then div(cls := parts.skeleton, data("part") := "skeleton", aria("hidden") := true)
      else NoMod

    val badgeNode: Mod = p.badge match
      case Some(bd) => div(cls := parts.badge, data("part") := "badge", bd)
      case None     => NoMod

    val overlayNode: Mod = p.overlay match
      case Some(ov) => div(cls := parts.overlay, data("part") := "overlay", ov)
      case None     => NoMod

    // The aspect-ratio box keeps the grid from reflowing as images load: the frame
    // reserves its space up front via `aspect-ratio` (when a ratio is given).
    val frameStyle: Mod =
      if p.ratio.nonEmpty then style := Map("aspect-ratio" -> p.ratio) else NoMod

    div(
      cls            := parts.root,
      ref            := container,
      data("state")  := state,
      data("inview") := inView,
      onClick        := (_ => p.onClick()),
      div(
        cls          := parts.frame,
        data("part") := "frame",
        frameStyle,
        skeletonNode,
        imgNode,
        badgeNode,
        overlayNode,
      ),
    )
  }

/** A single image tile for a media grid. The full image loads lazily — only once the
  * tile nears the viewport (disable with `lazyLoad = false`) — showing a skeleton until it
  * arrives and fading in on load. If `src` fails it tries `fallback` (when given), then
  * shows an error placeholder. `fit` controls cropping ([[ImageFit.Cover]] by default,
  * ideal for a uniform grid); `ratio` (a CSS `aspect-ratio` like `"16/9"` or `"1/1"`)
  * reserves the tile's space so the grid doesn't reflow as images load. `badge` overlays
  * a corner tag (resolution, "4K"); `overlay` holds hover content (download / favourite
  * actions). Classes come from the active [[Skin]]; state is mirrored to `data-*`.
  */
def ImageCard(
    src:      String,
    alt:      String          = "",
    fallback: String          = "",
    fit:      ImageFit        = ImageFit.Cover,
    ratio:    String          = "",
    rounded:  Boolean         = true,
    lazyLoad: Boolean         = true,
    badge:    Option[VNode]   = None,
    overlay:  Option[VNode]   = None,
    onClick:  () => Unit      = () => (),
    onLoad:   () => Unit      = () => (),
    onError:  () => Unit      = () => (),
): VNode =
  ImageCardImpl(
    (
      src = src,
      alt = alt,
      fallback = fallback,
      fit = fit,
      ratio = ratio,
      rounded = rounded,
      lazyLoad = lazyLoad,
      badge = badge,
      overlay = overlay,
      onClick = onClick,
      onLoad = onLoad,
      onError = onError,
    ),
  )

// A simple "broken image" glyph (Feather image-off), drawn with currentColor; injected
// as trusted static innerHTML so it parses into correctly-namespaced SVG nodes.
private val BrokenImageIcon =
  """<svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><line x1="2" y1="2" x2="22" y2="22"></line><path d="M10.41 10.41a2 2 0 1 1-2.83-2.83"></path><line x1="13.5" y1="13.5" x2="6" y2="21"></line><line x1="18" y1="12" x2="21" y2="15"></line><path d="M3.59 3.59A1.99 1.99 0 0 0 3 5v14a2 2 0 0 0 2 2h14c.55 0 1.052-.22 1.41-.59"></path><path d="M21 15V5a2 2 0 0 0-2-2H9"></path></svg>"""
