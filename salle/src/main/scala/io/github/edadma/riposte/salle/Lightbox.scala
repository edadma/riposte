package io.github.edadma.riposte.salle

import io.github.edadma.riposte.*
import org.scalajs.dom
import scala.scalajs.js

// The full-size image experience: a [[Lightbox]] that floats one image (of a set) above the
// page over a dimming scrim, with previous/next navigation, a counter, optional click-to-zoom,
// and keyboard control; a standalone previewable [[Image]] that opens its own one-image
// lightbox on click; and an [[ImagePreviewGroup]] that wires a set of `Image`s into one
// navigable lightbox so a gallery opens into a single browsable viewer.
//
// The lightbox is rendered through a core `portal` into `document.body`, so it escapes any
// `overflow`/`transform`/`z-index` trap of the gallery that opened it. Mounting is driven by
// [[usePresence]]: opening mounts and fades in, closing fades out and *then* unmounts, so the
// close transition is seen. Focus moves into the overlay on open and is restored to the opener
// on close. Escape closes; ArrowLeft/ArrowRight step through a multi-image set.
//
// Every meaningful state is mirrored to `data-*` for skin-independent tests and consumers: the
// overlay carries `data-state` (enter|open|exit) and `data-part=overlay`; the image carries
// `data-part=image` and `data-zoom` (in|out); the controls carry `data-part` (close|prev|next|
// counter) so specs select on stable hooks regardless of skin.

/** One previewable image: its source URL and alternative text. A plain named tuple, so a set
  * is just a `Vector[PreviewItem]`. */
type PreviewItem = (src: String, alt: String)

/** Build a [[PreviewItem]]. `alt` defaults to empty. */
def Preview(src: String, alt: String = ""): PreviewItem = (src = src, alt = alt)

private val LightboxImpl =
  component[
    (
        open: Boolean,
        items: Vector[PreviewItem],
        index: Option[Int],
        defaultIndex: Int,
        onClose: () => Unit,
        onIndexChange: Int => Unit,
        closeOnEsc: Boolean,
        maskClosable: Boolean,
        zoomable: Boolean,
        loop: Boolean,
        exitMs: Int,
    ),
  ] { p =>
    val skin     = useSkin()
    val parts    = skin.lightbox
    val presence = usePresence(p.open, p.exitMs)

    val (index, setIndex)      = useControllable(p.index, p.defaultIndex, p.onIndexChange)
    val (zoomed, setZoomed, _) = useState(false)

    val overlay = useRef[dom.Element | Null](null)

    val n       = p.items.length
    val current = if n == 0 then 0 else math.max(0, math.min(n - 1, index))
    val multi   = n > 1

    // Step the shown image by `delta`, wrapping when `loop` and clamping at the ends otherwise.
    // Each new image starts un-zoomed (reset here so navigation is felt immediately).
    def go(delta: Int): Unit =
      if n > 0 then
        val raw = current + delta
        val next =
          if p.loop then ((raw % n) + n) % n
          else math.max(0, math.min(n - 1, raw))
        if next != current then
          setZoomed(false)
          setIndex(next)

    // Every fresh open (and any externally-driven image change) starts un-zoomed.
    useEffect(
      () =>
        setZoomed(false)
        noCleanup
      ,
      Array(current, presence.mounted),
    )

    // Keyboard while open, on the document so it works regardless of where focus sits: Escape
    // closes, the arrows navigate a multi-image set. The listener is subscribed once per open
    // and reads the latest handler through a ref refreshed every render — so it always steps
    // from the current image without depending on a passive effect re-subscribing in time.
    val keyHandler = useRef[dom.KeyboardEvent => Unit](_ => ())
    keyHandler.current = (e: dom.KeyboardEvent) =>
      e.key match
        case "Escape" if p.closeOnEsc =>
          e.preventDefault()
          p.onClose()
        case "ArrowLeft" if multi =>
          e.preventDefault()
          go(-1)
        case "ArrowRight" if multi =>
          e.preventDefault()
          go(1)
        case _ => ()

    useEffect(
      () =>
        if !presence.mounted then noCleanup
        else
          val listener: js.Function1[dom.KeyboardEvent, Unit] = (e: dom.KeyboardEvent) => keyHandler.current(e)
          dom.document.addEventListener("keydown", listener)
          () => dom.document.removeEventListener("keydown", listener)
      ,
      Array(presence.mounted),
    )

    // Move focus into the overlay when it appears and restore it to the opener on close.
    useLayoutEffect(
      () =>
        if !presence.mounted then noCleanup
        else
          val previous = dom.document.activeElement
          val o        = overlay.current
          if o != null then o.asInstanceOf[dom.html.Element].focus()
          () =>
            if previous != null then
              try previous.asInstanceOf[dom.html.Element].focus()
              catch case _: Throwable => ()
      ,
      Array(presence.mounted),
    )

    if !presence.mounted || n == 0 then VEmpty
    else
      val item = p.items(current)

      val labelText = if item.alt.nonEmpty then s"Image preview: ${item.alt}" else "Image preview"

      val closeNode =
        button(
          cls           := parts.close,
          typ           := "button",
          data("part")  := "close",
          aria("label") := "Close",
          onClick       := (_ => p.onClose()),
          unsafeHtml(LightboxCloseIcon),
        )

      val prevNode: Mod =
        if multi then
          val atEnd = !p.loop && current == 0
          button(
            cls              := parts.prev,
            typ              := "button",
            data("part")     := "prev",
            data("disabled") := (if atEnd then "true" else "false"),
            disabled         := atEnd,
            aria("label")    := "Previous image",
            onClick := (e =>
              e.stopPropagation()
              go(-1)
            ),
            unsafeHtml(LightboxPrevIcon),
          )
        else NoMod

      val nextNode: Mod =
        if multi then
          val atEnd = !p.loop && current == n - 1
          button(
            cls              := parts.next,
            typ              := "button",
            data("part")     := "next",
            data("disabled") := (if atEnd then "true" else "false"),
            disabled         := atEnd,
            aria("label")    := "Next image",
            onClick := (e =>
              e.stopPropagation()
              go(1)
            ),
            unsafeHtml(LightboxNextIcon),
          )
        else NoMod

      val counterNode: Mod =
        if multi then
          span(cls := parts.counter, data("part") := "counter", aria("live") := "polite", s"${current + 1} / $n")
        else NoMod

      val imageNode =
        img(
          cls          := parts.img,
          data("part") := "image",
          data("zoom") := (if zoomed then "in" else "out"),
          src          := item.src,
          alt          := item.alt,
          if p.zoomable then
            onClick := (e =>
              e.stopPropagation()
              setZoomed(!zoomed)
            )
          else NoMod,
        )

      portal(
        dom.document.body,
        div(
          cls           := parts.overlay,
          ref           := overlay,
          role          := "dialog",
          aria("modal") := "true",
          aria("label") := labelText,
          tabIndex      := "-1",
          data("part")  := "overlay",
          data("state") := presence.phase.token,
          // Click on the scrim itself (never a child control or the image) dismisses.
          onClick := (e => if p.maskClosable && (e.target eq e.currentTarget) then p.onClose()),
          closeNode,
          prevNode,
          div(cls := parts.content, data("part") := "content", imageNode),
          nextNode,
          counterNode,
        ),
      )
  }

/** A full-size image viewer rendered above the page through a portal. It is *controlled*: the
  * caller owns `open` and is told to close via `onClose` (the close button, a scrim click when
  * `maskClosable`, and Escape when `closeOnEsc`). `items` is the set on show; the current one
  * is `index` (controlled) or `defaultIndex` (uncontrolled), with changes reported through
  * `onIndexChange`. With more than one item it shows previous/next controls and an `n / total`
  * counter, and ArrowLeft/ArrowRight navigate — wrapping past the ends when `loop`, clamping
  * otherwise. `zoomable` lets a click on the image toggle a zoomed view (reset whenever the
  * shown image changes). `exitMs` is how long the close fade runs before unmounting — keep it
  * in step with the skin's transition. Opening moves focus into the overlay; closing restores
  * it to the opener. Classes come from the active [[Skin]]; lifecycle state is mirrored to
  * `data-*`.
  */
def Lightbox(
    open:          Boolean,
    items:         Vector[PreviewItem],
    index:         Option[Int]        = None,
    defaultIndex:  Int                = 0,
    onClose:       () => Unit         = () => (),
    onIndexChange: Int => Unit        = _ => (),
    closeOnEsc:    Boolean            = true,
    maskClosable:  Boolean            = true,
    zoomable:      Boolean            = true,
    loop:          Boolean            = true,
    exitMs:        Int                = 200,
): VNode =
  LightboxImpl(
    (
      open = open,
      items = items,
      index = index,
      defaultIndex = defaultIndex,
      onClose = onClose,
      onIndexChange = onIndexChange,
      closeOnEsc = closeOnEsc,
      maskClosable = maskClosable,
      zoomable = zoomable,
      loop = loop,
      exitMs = exitMs,
    ),
  )

// --- Image: a standalone previewable image ---------------------------------------------

private val ImageImpl =
  component[
    (
        src: String,
        alt: String,
        fallback: String,
        preview: Boolean,
        fit: ImageFit,
        width: String,
        height: String,
        rounded: Boolean,
        onLoad: () => Unit,
        onError: () => Unit,
    ),
  ] { p =>
    val skin  = useSkin()
    val parts = skin.image(p.rounded)
    val group = useImagePreviewGroup()

    val (loaded, setLoaded, _)                 = useState(false)
    val (failed, setFailed, _)                 = useState(false)
    val (fallbackFailed, setFallbackFailed, _) = useState(false)
    val (open, setOpen, _)                     = useState(false)

    val slotId = useRef[Int](-1)

    // Register with an enclosing preview group exactly once; registration order is navigation
    // order. The cleanup unregisters on unmount. (A group of static gallery images is the
    // intended use; changing a grouped image's `src` after mount is not re-registered.)
    useEffect(
      () =>
        group match
          case Some(g) =>
            val id = g.register(p.src, p.alt)
            slotId.current = id
            () => g.unregister(id)
          case None => noCleanup
      ,
      Array(),
    )

    // Reset the load lifecycle if the source changes.
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
    val previewable     = p.preview && loaded && !hardError

    val state =
      if hardError then "error"
      else if loaded then "loaded"
      else "loading"

    def openPreview(): Unit =
      if previewable then
        group match
          case Some(g) => g.openAt(slotId.current)
          case None    => setOpen(true)

    val sizeStyle: Mod =
      val m = scala.collection.mutable.Map.empty[String, String]
      if p.width.nonEmpty then m += ("width"   -> p.width)
      if p.height.nonEmpty then m += ("height" -> p.height)
      if m.nonEmpty then style := m.toMap else NoMod

    val imgOrError: Mod =
      if hardError then
        div(
          cls           := parts.error,
          data("part")  := "error",
          role          := "img",
          aria("label") := (if p.alt.nonEmpty then p.alt else "Image failed to load"),
          unsafeHtml(LightboxBrokenIcon),
        )
      else
        img(
          cls          := parts.img,
          data("part") := "img",
          src          := effectiveSrc,
          alt          := p.alt,
          style := Map(
            "object-fit" -> p.fit.css,
            "opacity"    -> (if loaded then "1" else "0"),
            "cursor"     -> (if previewable then "zoom-in" else "default"),
          ),
          if previewable then role := "button" else NoMod,
          if previewable then tabIndex := "0" else NoMod,
          if previewable then
            aria("label") := (if p.alt.nonEmpty then s"${p.alt} (click to preview)" else "Image (click to preview)")
          else NoMod,
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
          onClick := (_ => openPreview()),
          onKeyDown := (e =>
            if e.key == "Enter" || e.key == " " then
              e.preventDefault()
              openPreview()
          ),
        )

    // A standalone image carries its own one-image lightbox; a grouped one defers to the
    // group's shared lightbox instead.
    val ownLightbox: Mod =
      if group.isEmpty then
        Lightbox(open = open, items = Vector((src = p.src, alt = p.alt)), onClose = () => setOpen(false))
      else NoMod

    div(
      cls           := parts.root,
      data("part")  := "root",
      data("state") := state,
      sizeStyle,
      imgOrError,
      ownLightbox,
    )
  }

/** A previewable image. It loads `src` (fading in on load); if that fails it tries `fallback`
  * when given, then shows an error placeholder. When `preview` is on, a loaded image is
  * clickable (and keyboard-activatable with Enter/Space) and opens a [[Lightbox]] — its own
  * one-image one, or, inside an [[ImagePreviewGroup]], the group's shared navigable one. `fit`
  * controls cropping ([[ImageFit.Cover]] by default); `width`/`height` are optional CSS sizes;
  * `rounded` requests rounded corners. Classes come from the active [[Skin]]; state is mirrored
  * to `data-*`.
  */
def Image(
    src:      String,
    alt:      String     = "",
    fallback: String     = "",
    preview:  Boolean    = true,
    fit:      ImageFit   = ImageFit.Cover,
    width:    String     = "",
    height:   String     = "",
    rounded:  Boolean    = false,
    onLoad:   () => Unit = () => (),
    onError:  () => Unit = () => (),
): VNode =
  ImageImpl(
    (
      src = src,
      alt = alt,
      fallback = fallback,
      preview = preview,
      fit = fit,
      width = width,
      height = height,
      rounded = rounded,
      onLoad = onLoad,
      onError = onError,
    ),
  )

// --- ImagePreviewGroup: a shared lightbox across a set of Images ------------------------

// The seam an [[Image]] uses to join a [[ImagePreviewGroup]]: register itself (returning a
// stable slot id), unregister on unmount, and ask the group to open its shared lightbox at the
// image's slot. `None` when there is no enclosing group, so a standalone Image falls back to
// its own one-image lightbox.
private type PreviewGroupApi = (
    register: (String, String) => Int,
    unregister: Int => Unit,
    openAt: Int => Unit,
)

private val PreviewGroupContext: Context[Option[PreviewGroupApi]] = createContext(None)

/** The enclosing [[ImagePreviewGroup]]'s seam, or `None` when there is none. */
private[salle] def useImagePreviewGroup()(using Hooks): Option[PreviewGroupApi] =
  useContext(PreviewGroupContext)

private val ImagePreviewGroupImpl =
  container[(zoomable: Boolean, loop: Boolean, exitMs: Int)] { (p, children) =>
    val slots  = useRef[scala.collection.mutable.ArrayBuffer[(Int, String, String)]](
      scala.collection.mutable.ArrayBuffer.empty,
    )
    val nextId = useRef[Int](0)

    val (_, _, bump)         = useState(0)
    val (open, setOpen, _)   = useState(false)
    val (index, setIndex, _) = useState(0)

    // Identity of `api` changes each render, but it closes only over stable refs and state
    // updaters, so an Image that captured an earlier render's handle still drives the live
    // registry. `bump` forces a group re-render whenever the set changes so the lightbox sees it.
    val api: PreviewGroupApi =
      (
        register = (s, a) =>
          val id = nextId.current
          nextId.current = id + 1
          slots.current += ((id, s, a))
          bump(_ + 1)
          id
        ,
        unregister = id =>
          val buf = slots.current
          val i   = buf.indexWhere(_._1 == id)
          if i >= 0 then buf.remove(i)
          bump(_ + 1)
        ,
        openAt = id =>
          val i = slots.current.indexWhere(_._1 == id)
          if i >= 0 then
            setIndex(i)
            setOpen(true)
        ,
      )

    val items: Vector[PreviewItem] = slots.current.toVector.map(s => (src = s._2, alt = s._3))

    PreviewGroupContext.provide(
      Some(api),
      fragment(
        (children :+
          Lightbox(
            open = open,
            items = items,
            index = Some(index),
            onClose = () => setOpen(false),
            onIndexChange = i => setIndex(i),
            zoomable = p.zoomable,
            loop = p.loop,
            exitMs = p.exitMs,
          ))*,
      ),
    )
  }

/** Wire a set of [[Image]]s into one navigable [[Lightbox]]: clicking any previewable image
  * inside opens a single shared viewer positioned at that image, with previous/next stepping
  * through them all (in mount order). `zoomable`, `loop`, and `exitMs` are forwarded to the
  * shared lightbox. The group renders its `children` unchanged plus the shared lightbox.
  */
def ImagePreviewGroup(
    zoomable: Boolean = true,
    loop:     Boolean = true,
    exitMs:   Int     = 200,
)(children: VNode*): VNode =
  ImagePreviewGroupImpl((zoomable = zoomable, loop = loop, exitMs = exitMs))(children*)

// Feather "x", drawn with currentColor; injected as trusted static innerHTML so it parses into
// correctly-namespaced SVG nodes.
private val LightboxCloseIcon =
  """<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><line x1="18" y1="6" x2="6" y2="18"></line><line x1="6" y1="6" x2="18" y2="18"></line></svg>"""

// Feather "chevron-left" / "chevron-right" for the navigation controls.
private val LightboxPrevIcon =
  """<svg xmlns="http://www.w3.org/2000/svg" width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><polyline points="15 18 9 12 15 6"></polyline></svg>"""

private val LightboxNextIcon =
  """<svg xmlns="http://www.w3.org/2000/svg" width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><polyline points="9 18 15 12 9 6"></polyline></svg>"""

// A "broken image" glyph (Feather image-off) for the error placeholder.
private val LightboxBrokenIcon =
  """<svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><line x1="2" y1="2" x2="22" y2="22"></line><path d="M10.41 10.41a2 2 0 1 1-2.83-2.83"></path><line x1="13.5" y1="13.5" x2="6" y2="21"></line><line x1="18" y1="12" x2="21" y2="15"></line><path d="M3.59 3.59A1.99 1.99 0 0 0 3 5v14a2 2 0 0 0 2 2h14c.55 0 1.052-.22 1.41-.59"></path><path d="M21 15V5a2 2 0 0 0-2-2H9"></path></svg>"""
