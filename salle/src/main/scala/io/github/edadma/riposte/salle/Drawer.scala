package io.github.edadma.riposte.salle

import io.github.edadma.riposte.*
import org.scalajs.dom
import scala.scalajs.js

// A panel that slides in from an edge of the viewport over a dimming scrim — the filter
// sidebar, a mobile nav, a detail/edit pane. It is the same modal-overlay machinery as
// [[Modal]] (portalled to `document.body`, mounted through [[usePresence]] so the slide-out
// animation is seen, focus trapped inside via the core [[useFocusTrap]] hook, Escape to close)
// but docked to one edge and sized along that axis rather than centred.
//
// It is *controlled*: the caller owns `open` and is told to close via `onClose` (the close
// button, a scrim click when `maskClosable`, and Escape when `closeOnEsc`). `children` are the
// body; `title`, `extra` (header actions), and `footer` are optional slots. Every meaningful
// state is mirrored to `data-*`: the root, mask, and panel each carry `data-state`
// (enter|open|exit) and the panel `data-placement`, so the slide is driven from CSS and specs
// select on stable, skin-independent hooks.

/** The edge a [[Drawer]] slides in from: `Left`/`Right` size by width, `Top`/`Bottom` by
  * height. `Right` is the default (the usual detail/edit pane). */
enum DrawerPlacement:
  case Left, Right, Top, Bottom

  /** The lowercase modifier token (`"right"`). */
  def token: String = toString.toLowerCase

  /** Whether the drawer is sized along the horizontal axis (left/right → width) versus the
    * vertical axis (top/bottom → height). */
  def horizontal: Boolean = this == DrawerPlacement.Left || this == DrawerPlacement.Right

private val DrawerImpl =
  container[
    (
        open: Boolean,
        onClose: () => Unit,
        placement: DrawerPlacement,
        size: String,
        title: Option[VNode],
        extra: Option[VNode],
        footer: Option[VNode],
        closable: Boolean,
        mask: Boolean,
        maskClosable: Boolean,
        closeOnEsc: Boolean,
        ariaLabel: String,
        exitMs: Int,
    ),
  ] { (p, children) =>
    val skin     = useSkin()
    val parts    = skin.drawer(p.placement)
    val presence = usePresence(p.open, p.exitMs)

    // The focus half of "modal": move focus into the panel on open, trap Tab within it, and
    // restore focus to the opener on close. Escape-to-close is handled separately below.
    val panel = useFocusTrap(presence.mounted)

    val base    = useId()
    val titleId = base + "-title"

    // Escape closes via a document-level listener while open — robust regardless of where focus
    // currently sits (the same reasoning as Modal).
    useEffect(
      () =>
        if !(presence.mounted && p.closeOnEsc) then noCleanup
        else
          val listener: js.Function1[dom.KeyboardEvent, Unit] = (e: dom.KeyboardEvent) =>
            if e.key == "Escape" then
              e.preventDefault()
              p.onClose()
          dom.document.addEventListener("keydown", listener)
          () => dom.document.removeEventListener("keydown", listener)
      ,
      Array(presence.mounted, p.closeOnEsc),
    )

    if !presence.mounted then VEmpty
    else
      val state = presence.phase.token

      val labelMod: Mod =
        if p.title.isDefined then aria("labelledby") := titleId
        else if p.ariaLabel.nonEmpty then aria("label") := p.ariaLabel
        else NoMod

      val closeButton: Mod =
        if p.closable then
          button(
            cls           := parts.close,
            typ           := "button",
            data("part")  := "close",
            aria("label") := "Close",
            onClick       := (_ => p.onClose()),
            unsafeHtml(DrawerCloseIcon),
          )
        else NoMod

      val titleNode: Mod = p.title match
        case Some(t) => h2(cls := parts.title, id := titleId, data("part") := "title", t)
        case None    => NoMod

      val extraNode: Mod = p.extra match
        case Some(x) => span(cls := parts.extra, data("part") := "extra", x)
        case None    => NoMod

      val headerNode: Mod =
        if p.title.isDefined || p.closable || p.extra.isDefined then
          div(cls := parts.header, data("part") := "header", titleNode, extraNode, closeButton)
        else NoMod

      val footerNode: Mod = p.footer match
        case Some(f) => div(cls := parts.footer, data("part") := "footer", f)
        case None    => NoMod

      // Size along the docking axis: width for left/right, height for top/bottom.
      val sizeStyle: Mod =
        if p.size.nonEmpty then
          style := Map((if p.placement.horizontal then "width" else "height") -> p.size)
        else NoMod

      val maskNode: Mod =
        if p.mask then
          div(
            cls           := parts.mask,
            data("part")  := "mask",
            data("state") := state,
            aria("hidden") := true,
            onClick       := (_ => if p.maskClosable then p.onClose()),
          )
        else NoMod

      portal(
        dom.document.body,
        div(
          cls               := parts.root,
          data("part")      := "drawer-root",
          data("state")     := state,
          data("placement") := p.placement.token,
          maskNode,
          div(
            cls               := parts.panel,
            ref               := panel,
            role              := "dialog",
            aria("modal")     := "true",
            tabIndex          := "-1",
            data("part")      := "panel",
            data("state")     := state,
            data("placement") := p.placement.token,
            labelMod,
            sizeStyle,
            headerNode,
            div(cls := parts.body, data("part") := "body", children),
            footerNode,
          ),
        ),
      )
  }

/** A panel that slides in from an edge over a dimming scrim, rendered above the page through a
  * portal. It is *controlled*: the caller owns `open` and is told to close via `onClose` (the
  * close button, a scrim click when `maskClosable`, and Escape when `closeOnEsc`). `children`
  * are the body; `title`, `extra` (header-right actions), and `footer` are optional slots.
  * `placement` ([[DrawerPlacement]]) picks the edge; `size` sets the panel's width (left/right)
  * or height (top/bottom) as a CSS length. `closable` shows the corner close button; `mask`
  * shows the scrim; `ariaLabel` names the dialog when there is no `title`; `exitMs` is how long
  * the slide-out runs before the drawer unmounts — keep it in step with the skin's transition.
  * Opening moves focus into the panel and traps Tab inside it; closing restores focus to the
  * opener. Classes come from the active [[Skin]]; lifecycle state is mirrored to `data-*`.
  */
def Drawer(
    open:         Boolean,
    onClose:      () => Unit      = () => (),
    placement:    DrawerPlacement = DrawerPlacement.Right,
    size:         String          = "320px",
    title:        Option[VNode]   = None,
    extra:        Option[VNode]   = None,
    footer:       Option[VNode]   = None,
    closable:     Boolean         = true,
    mask:         Boolean         = true,
    maskClosable: Boolean         = true,
    closeOnEsc:   Boolean         = true,
    ariaLabel:    String          = "",
    exitMs:       Int             = 250,
)(children: VNode*): VNode =
  DrawerImpl(
    (
      open = open,
      onClose = onClose,
      placement = placement,
      size = size,
      title = title,
      extra = extra,
      footer = footer,
      closable = closable,
      mask = mask,
      maskClosable = maskClosable,
      closeOnEsc = closeOnEsc,
      ariaLabel = ariaLabel,
      exitMs = exitMs,
    ),
  )(children*)

// Feather "x", drawn with currentColor; injected as trusted static innerHTML so it parses
// into correctly-namespaced SVG nodes.
private val DrawerCloseIcon =
  """<svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><line x1="18" y1="6" x2="6" y2="18"></line><line x1="6" y1="6" x2="18" y2="18"></line></svg>"""
