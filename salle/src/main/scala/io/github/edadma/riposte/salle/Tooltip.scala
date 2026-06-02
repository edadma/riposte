package io.github.edadma.riposte.salle

import io.github.edadma.riposte.*
import org.scalajs.dom
import scala.scalajs.js

// A small floating hint shown next to a trigger — the label on an icon button, the full text
// of a truncated cell, a hint on a disabled control. Unlike DaisyUI's pure-CSS tooltip (a
// string in `data-tip` shown on hover only), salle's is JS-driven so it reaches AntD-level
// behaviour: the `tip` is rich content (a [[VNode]]), it can be controlled, it opens on
// hover, keyboard focus, or click, and it honours enter/leave delays.
//
// Mounting is driven by [[usePresence]]: showing mounts and fades in, hiding fades out and
// *then* unmounts, so the close transition is seen. The enter/leave delays go through the
// shared [[Timers]] seam, so tests drive them deterministically.
//
// Accessibility: the tip is a `role="tooltip"` element with a stable id, and the wrapper
// carries `aria-describedby` pointing at it while shown. (riposte has no child-cloning, so the
// relationship sits on the wrapper rather than the trigger element itself — a consumer that
// needs the describedby on the control directly can set it themselves; a core clone helper is
// the eventual fix.) Escape always dismisses an open tooltip; a click-triggered one also
// dismisses on an outside click.
//
// State is mirrored to `data-*`: the wrapper carries `data-part=tooltip` + `data-state`
// (enter|open|exit|closed) + `data-placement` + `data-disabled`; the tip carries
// `data-part=tip` (and its own `data-state`/`data-placement`); the arrow `data-part=arrow`.

/** Which side of the trigger a [[Tooltip]] appears on. */
enum TooltipPlacement:
  case Top, Bottom, Left, Right

  /** The lowercase modifier token (`"top"`). */
  def token: String = this match
    case TooltipPlacement.Top    => "top"
    case TooltipPlacement.Bottom => "bottom"
    case TooltipPlacement.Left   => "left"
    case TooltipPlacement.Right  => "right"

/** What makes a [[Tooltip]] appear. `Hover` (the default) shows on pointer hover *and*
  * keyboard focus — the accessible default; `Focus` shows on focus only; `Click` toggles on
  * click and dismisses on an outside click. Escape dismisses any open tooltip. */
enum TooltipTrigger:
  case Hover, Focus, Click

private val TooltipImpl =
  container[
    (
        tip: VNode,
        placement: TooltipPlacement,
        color: Color,
        trigger: TooltipTrigger,
        open: Option[Boolean],
        defaultOpen: Boolean,
        mouseEnterDelay: Int,
        mouseLeaveDelay: Int,
        disabled: Boolean,
        exitMs: Int,
        onOpenChange: Boolean => Unit,
    ),
  ] { (p, children) =>
    val skin  = useSkin()
    val parts = skin.tooltip(p.placement, p.color)

    val (current, setOpen) = useControllable(p.open, p.defaultOpen, p.onOpenChange)

    // Disabled wins over any requested open state, so a disabled trigger never shows a tip.
    val wantOpen = current && !p.disabled
    val presence = usePresence(wantOpen, p.exitMs)

    val wrapper = useRef[dom.Element | Null](null)
    val tipId   = useId() + "-tip"

    // A single pending show/hide timer, cancelled whenever the opposite intent arrives so a
    // quick hover-in-then-out doesn't flash the tip. Held in a ref so it survives re-renders.
    val pending = useRef[(() => Unit) | Null](null)

    def clearPending(): Unit =
      val cancel = pending.current
      if cancel != null then cancel()
      pending.current = null

    def scheduleOpen(): Unit =
      clearPending()
      pending.current = Timers.schedule(
        () => { pending.current = null; setOpen(true) },
        p.mouseEnterDelay,
      )

    def scheduleClose(): Unit =
      clearPending()
      pending.current = Timers.schedule(
        () => { pending.current = null; setOpen(false) },
        p.mouseLeaveDelay,
      )

    val enabled       = !p.disabled
    val hoverTrigger  = p.trigger == TooltipTrigger.Hover
    val focusTrigger  = p.trigger == TooltipTrigger.Hover || p.trigger == TooltipTrigger.Focus
    val clickTrigger  = p.trigger == TooltipTrigger.Click

    // Escape dismisses any open tooltip, via a document listener while mounted — robust
    // regardless of where focus sits (the same reasoning as Modal's Escape handling).
    useEffect(
      () =>
        if !presence.mounted then noCleanup
        else
          val listener: js.Function1[dom.KeyboardEvent, Unit] = (e: dom.KeyboardEvent) =>
            if e.key == "Escape" then { clearPending(); setOpen(false) }
          dom.document.addEventListener("keydown", listener)
          () => dom.document.removeEventListener("keydown", listener)
      ,
      Array(presence.mounted),
    )

    // Cancel a pending timer if the tooltip unmounts mid-delay, so it can't fire into a dead
    // component.
    useEffect(() => () => clearPending(), Array.empty[Any])

    // A click-triggered tooltip dismisses when the pointer goes down outside the wrapper.
    useClickOutside(wrapper, clickTrigger && current, () => { clearPending(); setOpen(false) })

    val describedBy: Mod = if presence.mounted then aria("describedby") := tipId else NoMod

    val tipNode: Mod =
      if presence.mounted then
        span(
          cls               := parts.tip,
          id                := tipId,
          role              := "tooltip",
          data("part")      := "tip",
          data("state")     := presence.phase.token,
          data("placement") := p.placement.token,
          span(cls := parts.arrow, data("part") := "arrow", aria("hidden") := true),
          p.tip,
        )
      else NoMod

    span(
      cls               := parts.root,
      ref               := wrapper,
      data("part")      := "tooltip",
      data("state")     := (if presence.mounted then presence.phase.token else "closed"),
      data("placement") := p.placement.token,
      data("disabled")  := p.disabled,
      describedBy,
      onMouseEnter := (_ => if enabled && hoverTrigger then scheduleOpen()),
      onMouseLeave := (_ => if enabled && hoverTrigger then scheduleClose()),
      onFocusIn    := (_ => if enabled && focusTrigger then scheduleOpen()),
      onFocusOut   := (_ => if enabled && focusTrigger then scheduleClose()),
      onClick      := (_ => if enabled && clickTrigger then { clearPending(); setOpen(!current) }),
      children,
      tipNode,
    )
  }

/** A floating hint anchored to its `children` (the trigger). `tip` is the content shown — a
  * string or any [[VNode]]. `placement` chooses the side; `color` tints the bubble. `trigger`
  * selects how it appears ([[TooltipTrigger.Hover]] = hover + focus, the default;
  * [[TooltipTrigger.Focus]]; [[TooltipTrigger.Click]]). It can be *uncontrolled*
  * (`defaultOpen` seeds it) or *controlled* (pass `open = Some(...)` and react to
  * `onOpenChange`). `mouseEnterDelay`/`mouseLeaveDelay` (ms) debounce show/hide so a quick
  * pass doesn't flash it; `disabled` suppresses it entirely; `exitMs` is the fade-out duration
  * before it unmounts (keep it in step with the skin's transition). Classes come from the
  * active [[Skin]]; state is mirrored to `data-*`, and the tip is a `role="tooltip"` referenced
  * by the wrapper's `aria-describedby` while shown. */
def Tooltip(
    tip:             VNode,
    placement:       TooltipPlacement   = TooltipPlacement.Top,
    color:           Color              = Color.Default,
    trigger:         TooltipTrigger     = TooltipTrigger.Hover,
    open:            Option[Boolean]    = None,
    defaultOpen:     Boolean            = false,
    mouseEnterDelay: Int                = 100,
    mouseLeaveDelay: Int                = 100,
    disabled:        Boolean            = false,
    exitMs:          Int                = 150,
    onOpenChange:    Boolean => Unit    = _ => (),
)(children: VNode*): VNode =
  TooltipImpl(
    (
      tip = tip,
      placement = placement,
      color = color,
      trigger = trigger,
      open = open,
      defaultOpen = defaultOpen,
      mouseEnterDelay = mouseEnterDelay,
      mouseLeaveDelay = mouseLeaveDelay,
      disabled = disabled,
      exitMs = exitMs,
      onOpenChange = onOpenChange,
    ),
  )(children*)
