package io.github.edadma.riposte.salle

import io.github.edadma.riposte.*
import org.scalajs.dom
import scala.scalajs.js

// A modal dialog: content that floats above the page over a dimming scrim, taking focus
// until it is dismissed. It is the general-purpose overlay the gallery's lightbox is built
// from, but nothing here is image-specific — `children` are the body, with optional `title`
// and `footer` slots.
//
// The dialog is rendered through a core `portal` into `document.body`, so it escapes any
// `overflow`/`transform`/`z-index` trap of the element that opened it and always layers
// above the page. Mounting is driven by [[usePresence]]: opening mounts and animates in,
// closing animates out and *then* unmounts, so the close transition is actually seen.
//
// Accessibility matches the dialog pattern: `role="dialog"` (or `alertdialog`) with
// `aria-modal`, the box is labelled by its title (or an explicit `ariaLabel`), focus moves
// into the dialog on open and is restored to the opener on close, Tab is trapped within the
// box, and Escape closes. Every meaningful state is mirrored to `data-*`: the overlay
// carries `data-state` (enter|open|exit) and `data-part=overlay`; the box and each slot
// carry `data-part` (box|header|title|close|body|footer) so tests and consumers select on
// stable, skin-independent hooks.

private val ModalImpl =
  container[
    (
        open: Boolean,
        onClose: () => Unit,
        title: Option[VNode],
        footer: Option[VNode],
        closable: Boolean,
        maskClosable: Boolean,
        closeOnEsc: Boolean,
        centered: Boolean,
        alert: Boolean,
        width: String,
        ariaLabel: String,
        exitMs: Int,
    ),
  ] { (p, children) =>
    val skin     = useSkin()
    val parts    = skin.modal(p.centered)
    val presence = usePresence(p.open, p.exitMs)

    // The focus half of "modal": move focus into the box on open, trap Tab within it, and
    // restore focus to the opener on close. Escape-to-close is handled separately below.
    val box = useFocusTrap(presence.mounted)

    val base    = useId()
    val titleId = base + "-title"

    // Escape closes, via a document-level listener while open — robust regardless of where
    // focus currently sits (the same reasoning as Select's click-outside on the document).
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
      val dialogRole = if p.alert then "alertdialog" else "dialog"

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
            unsafeHtml(CloseIcon),
          )
        else NoMod

      val titleNode: Mod = p.title match
        case Some(t) => h2(cls := parts.title, id := titleId, data("part") := "title", t)
        case None    => NoMod

      val headerNode: Mod =
        if p.title.isDefined || p.closable then
          header(cls := parts.header, data("part") := "header", titleNode, closeButton)
        else NoMod

      val footerNode: Mod = p.footer match
        case Some(f) => footer(cls := parts.footer, data("part") := "footer", f)
        case None    => NoMod

      val widthMod: Mod =
        if p.width.nonEmpty then style := Map("width" -> p.width, "max-width" -> "90vw") else NoMod

      portal(
        dom.document.body,
        div(
          cls           := parts.overlay,
          data("part")  := "overlay",
          data("state") := presence.phase.token,
          // Click on the scrim itself (not a child) dismisses, when permitted.
          onClick := (e => if p.maskClosable && (e.target eq e.currentTarget) then p.onClose()),
          div(
            cls               := parts.box,
            ref               := box,
            role              := dialogRole,
            aria("modal")     := "true",
            tabIndex          := "-1",
            data("part")      := "box",
            labelMod,
            widthMod,
            headerNode,
            div(cls := parts.body, data("part") := "body", children),
            footerNode,
          ),
        ),
      )
  }

/** A modal dialog rendered above the page through a portal. It is *controlled*: the caller
  * owns `open` and is told to close via `onClose` (fired by the close button, a click on
  * the scrim when `maskClosable`, and Escape when `closeOnEsc`). `children` are the body;
  * `title` and `footer` are optional slots. `closable` shows the corner close button;
  * `centered` vertically centres the box; `alert` switches the role to `alertdialog` for
  * urgent messages; `width` overrides the box width (capped at `90vw`); `ariaLabel` names
  * the dialog when there is no `title`. `exitMs` is how long the close animation runs
  * before the dialog unmounts — keep it in step with the skin's transition. Opening moves
  * focus into the dialog and traps Tab inside it; closing restores focus to the opener.
  * Classes come from the active [[Skin]]; lifecycle state is mirrored to `data-*`.
  */
def Modal(
    open:         Boolean,
    onClose:      () => Unit       = () => (),
    title:        Option[VNode]    = None,
    footer:       Option[VNode]    = None,
    closable:     Boolean          = true,
    maskClosable: Boolean          = true,
    closeOnEsc:   Boolean          = true,
    centered:     Boolean          = false,
    alert:        Boolean          = false,
    width:        String           = "",
    ariaLabel:    String           = "",
    exitMs:       Int              = 200,
)(children: VNode*): VNode =
  ModalImpl(
    (
      open = open,
      onClose = onClose,
      title = title,
      footer = footer,
      closable = closable,
      maskClosable = maskClosable,
      closeOnEsc = closeOnEsc,
      centered = centered,
      alert = alert,
      width = width,
      ariaLabel = ariaLabel,
      exitMs = exitMs,
    ),
  )(children*)

// Feather "x", drawn with currentColor; injected as trusted static innerHTML so it parses
// into correctly-namespaced SVG nodes.
private val CloseIcon =
  """<svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><line x1="18" y1="6" x2="6" y2="18"></line><line x1="6" y1="6" x2="18" y2="18"></line></svg>"""
