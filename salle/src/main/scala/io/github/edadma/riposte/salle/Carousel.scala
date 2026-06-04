package io.github.edadma.riposte.salle

import io.github.edadma.riposte.*
import org.scalajs.dom

// A rotating strip of full-width slides — the featured-wallpaper banner on a landing page, a
// product gallery, an onboarding sequence. Data-driven like [[Tabs]] and [[Segmented]]: you pass
// the `slides` (each a [[VNode]]) and salle renders the `role=region` carousel, advances between
// them, and handles the arrows, the indicator dots, the keyboard, autoplay, and pointer-swipe.
// The current slide is controllable (pass `activeIndex` to own it) or self-managed (seed it with
// `defaultActiveIndex`).
//
// Two effects. `Scrollx` (the default) lays the slides in a flex track and translates it so they
// slide horizontally (or vertically when `vertical`); `Fade` stacks them and cross-fades the
// active one. `infinite` wraps past the ends (and is what autoplay rides); without it the arrows
// disable at the first/last slide.
//
// Keyboard follows the slide pattern: with the region focused, Left/Right (or Up/Down when
// `vertical`) step to the previous/next slide. Autoplay advances every `autoplaySpeed` ms and, when
// `pauseOnHover`, halts while the pointer is over the carousel. A horizontal pointer drag past a
// small threshold also flips the slide, so it works under touch.
//
// Accessibility: the region is `role=region` `aria-roledescription=carousel`; the track is an
// `aria-live=polite` region so a slide change is announced; each slide is `role=group`
// `aria-roledescription=slide` carrying `aria-label="Slide n of N"` and `aria-hidden` while off;
// the dots are a `role=tablist` of `role=tab` buttons with `aria-selected`. State is mirrored to
// `data-*`: the root carries `data-part=carousel` + `data-active-index` + `data-effect` +
// `data-vertical`; the track `data-part=track`; each slide `data-part=slide` + `data-active`; the
// dots `data-part=dots` and each `data-part=dot` + `data-active`.

/** How a [[Carousel]] transitions between slides. `Scrollx` slides the whole track (horizontally,
  * or vertically when the carousel is `vertical`); `Fade` stacks the slides and cross-fades the
  * active one. */
enum CarouselEffect:
  case Scrollx, Fade

  /** The lowercase modifier token (`"scrollx"`). */
  def token: String = this match
    case Scrollx => "scrollx"
    case Fade    => "fade"

private val CarouselImpl =
  component[(
      slides:             Vector[VNode],
      activeIndex:        Option[Int],
      defaultActiveIndex: Int,
      autoplay:           Boolean,
      autoplaySpeed:      Int,
      speed:              Int,
      arrows:             Boolean,
      dots:               Boolean,
      effect:             CarouselEffect,
      infinite:           Boolean,
      pauseOnHover:       Boolean,
      vertical:           Boolean,
      ariaLabel:          String,
      onChange:           Int => Unit,
  )] { p =>
    val skin   = useSkin()
    val parts  = skin.carousel(p.vertical)
    val slides = p.slides
    val count  = slides.length

    val (current, setIndex) = useControllable(p.activeIndex, p.defaultActiveIndex, p.onChange)
    val (paused, setPaused, _) = useState(false)

    // Resolve a requested index to a real slide: wrap around when infinite, else clamp to the
    // ends. A no-op when it lands on the current slide or there are no slides.
    def goTo(index: Int): Unit =
      if count > 0 then
        val target =
          if p.infinite then ((index % count) + count) % count
          else math.max(0, math.min(index, count - 1))
        if target != current then setIndex(target)

    def next(): Unit = goTo(current + 1)
    def prev(): Unit = goTo(current - 1)

    // Autoplay re-arms after each advance: the effect re-runs when `current` changes (or when
    // autoplay/pause/count do), scheduling the next step a full interval out. Routing through the
    // Timers seam keeps it deterministic in tests, and the cleanup cancels a pending step so a
    // user interaction or unmount resets the countdown.
    useEffect(
      () =>
        if p.autoplay && !paused && count > 1 then Timers.schedule(() => next(), p.autoplaySpeed)
        else noCleanup
      ,
      Array(p.autoplay, paused, count, current),
    )

    val onRegionKey: dom.KeyboardEvent => Unit = e =>
      val (prevKey, nextKey) = if p.vertical then ("ArrowUp", "ArrowDown") else ("ArrowLeft", "ArrowRight")
      e.key match
        case k if k == prevKey => e.preventDefault(); prev()
        case k if k == nextKey => e.preventDefault(); next()
        case _                 => ()

    // Pointer-swipe: remember where a drag began and flip the slide if it ends far enough along
    // the carousel's axis. A short drag falls through (treated as a click on a dot/arrow).
    val dragStart  = useRef[Double | Null](null)
    val swipeMin   = 40.0
    def axisPos(e: dom.PointerEvent): Double = if p.vertical then e.clientY else e.clientX

    val onDown: dom.PointerEvent => Unit = e => dragStart.current = axisPos(e)
    val onUp: dom.PointerEvent => Unit = e =>
      val s = dragStart.current
      dragStart.current = null
      if s != null then
        val delta = axisPos(e) - s.asInstanceOf[Double]
        if delta <= -swipeMin then next()
        else if delta >= swipeMin then prev()

    def onEnter(): Unit = if p.pauseOnHover && p.autoplay then setPaused(true)
    def onLeave(): Unit = if p.pauseOnHover && p.autoplay then setPaused(false)

    // The track's transform/opacity is index-dependent layout, not a skin concern, so it rides
    // inline style. Scrollx flexes the slides into a row/column and shifts the whole track; Fade
    // overlays them and the per-slide opacity does the work.
    val trackStyle: Map[String, String] =
      if p.effect == CarouselEffect.Scrollx then
        val base = Map(
          "display"        -> "flex",
          "flex-direction" -> (if p.vertical then "column" else "row"),
          "transform" -> (
            if p.vertical then s"translateY(-${current * 100}%)" else s"translateX(-${current * 100}%)"
          ),
          "transition" -> s"transform ${p.speed}ms ease-in-out",
        )
        if p.vertical then base + ("height" -> "100%") else base
      else Map("position" -> "relative", "width" -> "100%", "height" -> "100%")

    def slideStyle(i: Int): Map[String, String] =
      if p.effect == CarouselEffect.Fade then
        Map(
          "position"   -> (if i == current then "relative" else "absolute"),
          "top"        -> "0",
          "left"       -> "0",
          "width"      -> "100%",
          "opacity"    -> (if i == current then "1" else "0"),
          "transition" -> s"opacity ${p.speed}ms ease-in-out",
          "z-index"    -> (if i == current then "1" else "0"),
        )
      else if p.vertical then Map("flex-shrink" -> "0", "height" -> "100%")
      else Map("flex-shrink" -> "0", "width" -> "100%")

    val slideNodes: Seq[VNode] = slides.zipWithIndex.map { (slide, i) =>
      val isActive = i == current
      div(
        cls                       := parts.slide,
        role                      := "group",
        aria("roledescription")   := "slide",
        aria("label")             := s"Slide ${i + 1} of $count",
        aria("hidden")            := (!isActive),
        data("part")              := "slide",
        data("active")            := isActive,
        style                     := slideStyle(i),
        slide,
      )
    }

    val trackNode =
      div(
        cls          := parts.track,
        data("part") := "track",
        aria("live") := "polite",
        style        := trackStyle,
        slideNodes,
      )

    val viewportNode =
      div(
        cls          := parts.viewport,
        data("part") := "viewport",
        trackNode,
      )

    def arrowChar(isPrev: Boolean): String =
      if p.vertical then (if isPrev then "▲" else "▼") else (if isPrev then "❮" else "❯")

    val arrowsMod: Mod =
      if p.arrows && count > 1 then
        Seq[VNode](
          button(
            cls           := (parts.arrow + " " + parts.prev),
            typ           := "button",
            aria("label") := "Previous slide",
            data("part")  := "arrow",
            data("dir")   := "prev",
            disabled      := (!p.infinite && current == 0),
            onClick       := (_ => prev()),
            arrowChar(true),
          ),
          button(
            cls           := (parts.arrow + " " + parts.next),
            typ           := "button",
            aria("label") := "Next slide",
            data("part")  := "arrow",
            data("dir")   := "next",
            disabled      := (!p.infinite && current == count - 1),
            onClick       := (_ => next()),
            arrowChar(false),
          ),
        )
      else NoMod

    val dotsMod: Mod =
      if p.dots && count > 1 then
        div(
          cls           := parts.dots,
          role          := "tablist",
          aria("label") := "Slide indicators",
          data("part")  := "dots",
          slides.indices.map { i =>
            val isActive = i == current
            button(
              cls              := (if isActive then parts.dotActive else parts.dot),
              typ              := "button",
              role             := "tab",
              aria("selected") := (if isActive then "true" else "false"),
              aria("label")    := s"Go to slide ${i + 1}",
              data("part")     := "dot",
              data("active")   := isActive,
              onClick          := (_ => goTo(i)),
            )
          },
        )
      else NoMod

    div(
      cls                     := parts.root,
      role                    := "region",
      aria("roledescription") := "carousel",
      aria("label")           := p.ariaLabel,
      tabIndex                := "0",
      data("part")            := "carousel",
      data("active-index")    := current.toString,
      data("effect")          := p.effect.token,
      data("vertical")        := p.vertical,
      onKeyDown               := onRegionKey,
      onMouseEnter            := (_ => onEnter()),
      onMouseLeave            := (_ => onLeave()),
      onPointerDown           := onDown,
      onPointerUp             := onUp,
      viewportNode,
      arrowsMod,
      dotsMod,
    )
  }

/** A slide carousel: pass the `slides` (each a [[VNode]]) and salle renders a `role=region`
  * carousel that advances between them. The current slide is controllable via `activeIndex` (the
  * caller owns it, told of intent through `onChange`) or self-managed from `defaultActiveIndex`.
  * `effect` ([[CarouselEffect]]) chooses slide-vs-fade; `infinite` wraps past the ends; `vertical`
  * stacks the slides; `arrows`/`dots` toggle the controls; `autoplay` advances every
  * `autoplaySpeed` ms (pausing on hover when `pauseOnHover`); `speed` is the transition duration.
  * Fully keyboard-driven (arrow keys) and swipeable; classes come from the active [[Skin]] and
  * state is mirrored to `data-*`. `ariaLabel` names the region for assistive tech.
  *
  * {{{ Carousel(slides = Seq(
  *   Image(src = "a.jpg", alt = "A"),
  *   Image(src = "b.jpg", alt = "B"),
  * ), autoplay = true) }}} */
def Carousel(
    slides:             Seq[VNode],
    activeIndex:        Option[Int]    = None,
    defaultActiveIndex: Int            = 0,
    autoplay:           Boolean        = false,
    autoplaySpeed:      Int            = 3000,
    speed:              Int            = 500,
    arrows:             Boolean        = true,
    dots:               Boolean        = true,
    effect:             CarouselEffect = CarouselEffect.Scrollx,
    infinite:           Boolean        = true,
    pauseOnHover:       Boolean        = true,
    vertical:           Boolean        = false,
    ariaLabel:          String         = "Carousel",
    onChange:           Int => Unit    = _ => (),
): VNode =
  CarouselImpl(
    (
      slides = slides.toVector,
      activeIndex = activeIndex,
      defaultActiveIndex = defaultActiveIndex,
      autoplay = autoplay,
      autoplaySpeed = autoplaySpeed,
      speed = speed,
      arrows = arrows,
      dots = dots,
      effect = effect,
      infinite = infinite,
      pauseOnHover = pauseOnHover,
      vertical = vertical,
      ariaLabel = ariaLabel,
      onChange = onChange,
    ),
  )
