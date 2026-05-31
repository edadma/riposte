package io.github.edadma.riposte.salle

import io.github.edadma.riposte.*
import org.scalajs.dom

/** The kind of a toast, which selects its accent colour, default icon, and ARIA urgency.
  * `Loading` is the spinner variant used for in-progress work; it is sticky by default
  * (no auto-dismiss) until replaced or dismissed. */
enum ToastType:
  case Info, Success, Warning, Error, Loading

  /** The `data-type` token and BEM modifier for this kind. */
  def token: String = this match
    case Info    => "info"
    case Success => "success"
    case Warning => "warning"
    case Error   => "error"
    case Loading => "loading"

/** Where a toast region is anchored on screen. Each toast carries its own placement, so a
  * single [[Toaster]] can show several stacks at once (one per occupied corner/edge). */
enum ToastPlacement:
  case TopLeft, TopCenter, TopRight, BottomLeft, BottomCenter, BottomRight

  /** The `data-placement` token and BEM modifier for this placement. */
  def token: String = this match
    case TopLeft      => "top-left"
    case TopCenter    => "top-center"
    case TopRight     => "top-right"
    case BottomLeft   => "bottom-left"
    case BottomCenter => "bottom-center"
    case BottomRight  => "bottom-right"

/** One queued toast. A plain named tuple (never a case class) holding everything the
  * [[Toaster]] needs to render and manage a single notification. `id` is assigned by the
  * store and is the React-style key that keeps each toast's component instance — and so its
  * auto-dismiss timer and enter/exit animation — stable as siblings are added and removed. */
type ToastEntry = (
    id:          Int,
    message:     VNode,
    description: Option[VNode],
    kind:        ToastType,
    duration:    Int,
    placement:   ToastPlacement,
    closable:    Boolean,
    icon:        Option[VNode],
    onClick:     Option[() => Unit],
    onClose:     () => Unit,
)

/** The reactive backing store for toasts: an ordered list plus a set of listeners. It is a
  * plain external store (subscribe + snapshot), bridged into riposte's render cycle by the
  * [[Toaster]] via `useSyncExternalStore`. Toasts are created imperatively through the
  * [[toast]] API — which is exactly what a "Downloaded" or "Added to favourites" handler
  * wants — rather than by threading open-state through the view tree. */
object ToastStore:
  private var entries:   Vector[ToastEntry] = Vector.empty
  private var listeners: Vector[() => Unit] = Vector.empty
  private var seq:       Int                = 0

  /** The current list of toasts, oldest first. Stable until the next mutation. */
  def snapshot: Vector[ToastEntry] = entries

  /** Register `listener` to be called after every mutation; returns an unsubscribe thunk. */
  def subscribe(listener: () => Unit): () => Unit =
    listeners = listeners :+ listener
    () => listeners = listeners.filterNot(_ eq listener)

  private def emit(): Unit = listeners.foreach(_())

  /** Append a new toast and return its assigned id (usable later with [[remove]]). */
  def add(
      message:     VNode,
      description: Option[VNode],
      kind:        ToastType,
      duration:    Int,
      placement:   ToastPlacement,
      closable:    Boolean,
      icon:        Option[VNode],
      onClick:     Option[() => Unit],
      onClose:     () => Unit,
  ): Int =
    seq += 1
    val id = seq
    val entry: ToastEntry = (
      id = id,
      message = message,
      description = description,
      kind = kind,
      duration = duration,
      placement = placement,
      closable = closable,
      icon = icon,
      onClick = onClick,
      onClose = onClose,
    )
    entries = entries :+ entry
    emit()
    id

  /** Remove the toast with this id immediately (no exit animation). The animated path is the
    * close button / auto-dismiss, which lets the item play its exit before calling this. */
  def remove(id: Int): Unit =
    entries = entries.filterNot(_.id == id)
    emit()

  /** Drop every toast at once. */
  def clear(): Unit =
    entries = Vector.empty
    emit()

  /** Test seam: reset the id counter so ids are predictable across cases. */
  private[salle] def resetForTest(): Unit =
    entries = Vector.empty
    seq = 0
    emit()

/** The imperative entry point — `toast.success("Downloaded")`, `toast.error(...)`, etc. Each
  * call enqueues a toast and returns its id; `dismiss(id)` and `clear()` remove them. A
  * [[Toaster]] must be mounted once (anywhere) for the toasts to appear. */
object toast:
  /** The default visible lifetime, in milliseconds, for non-loading toasts. */
  val DefaultDuration: Int = 4000

  /** Show a toast of an explicit kind. `duration = 0` makes it sticky; `icon = None` uses the
    * kind's default icon (a spinner for `Loading`). Returns the toast's id. */
  def show(
      message:     VNode,
      kind:        ToastType         = ToastType.Info,
      description: Option[VNode]     = None,
      duration:    Int               = DefaultDuration,
      placement:   ToastPlacement    = ToastPlacement.TopRight,
      closable:    Boolean           = true,
      icon:        Option[VNode]     = None,
      onClick:     Option[() => Unit] = None,
      onClose:     () => Unit        = () => (),
  ): Int =
    ToastStore.add(message, description, kind, duration, placement, closable, icon, onClick, onClose)

  def info(message: VNode, description: Option[VNode] = None, duration: Int = DefaultDuration): Int =
    show(message, ToastType.Info, description, duration)

  def success(message: VNode, description: Option[VNode] = None, duration: Int = DefaultDuration): Int =
    show(message, ToastType.Success, description, duration)

  def warning(message: VNode, description: Option[VNode] = None, duration: Int = DefaultDuration): Int =
    show(message, ToastType.Warning, description, duration)

  def error(message: VNode, description: Option[VNode] = None, duration: Int = DefaultDuration): Int =
    show(message, ToastType.Error, description, duration)

  /** A sticky spinner toast for in-progress work — no auto-dismiss until removed. */
  def loading(message: VNode, description: Option[VNode] = None): Int =
    show(message, ToastType.Loading, description, duration = 0)

  /** Remove a toast by id (immediate). */
  def dismiss(id: Int): Unit = ToastStore.remove(id)

  /** Remove all toasts. */
  def clear(): Unit = ToastStore.clear()

/** A single rendered toast. It owns its own open-state, auto-dismiss timer, and enter/exit
  * animation (via `usePresence`), which is why it is a component keyed by toast id — so its
  * timer is not inherited by a different toast when siblings are added or removed. When its
  * exit finishes it removes itself from the store. */
private val ToastItemImpl = component[ToastEntry] { e =>
  val skin  = useSkin()
  val parts = skin.toast(e.kind)

  val (open, setOpen, _)     = useState(true)
  val (paused, setPaused, _) = useState(false)
  val presence               = usePresence(open, exitMs = 200)

  // Once the exit animation has played out and the element is no longer mounted, take this
  // toast out of the store so the Toaster stops rendering it.
  useEffect(
    () =>
      if !presence.mounted then ToastStore.remove(e.id)
      noCleanup
    ,
    Array(presence.mounted),
  )

  def beginExit(): Unit =
    if open then
      setOpen(false)
      e.onClose()

  // Auto-dismiss after `duration`, unless the toast is sticky (duration <= 0) or the pointer
  // is hovering it. The returned cancel thunk is the effect cleanup, so a hover (which flips
  // `paused`) or an early close cancels the pending timer; leaving re-arms a fresh one.
  useEffect(
    () =>
      if e.duration > 0 && !paused && open then Timers.schedule(() => beginExit(), e.duration)
      else noCleanup
    ,
    Array(paused, open),
  )

  val iconNode: Mod = (e.kind, e.icon) match
    case (_, Some(custom)) =>
      span(cls := parts.icon, data("part") := "icon", aria("hidden") := "true", custom)
    case (ToastType.Loading, None) =>
      span(cls := parts.spinner, data("part") := "spinner", role := "status", aria("label") := "loading")
    case (k, None) =>
      span(cls := parts.icon, data("part") := "icon", aria("hidden") := "true", unsafeHtml(defaultIconSvg(k)))

  val descNode: Mod = e.description match
    case Some(d) => div(cls := parts.description, data("part") := "description", d)
    case None    => NoMod

  val closeNode: Mod =
    if e.closable then
      button(
        cls           := parts.close,
        typ           := "button",
        data("part")  := "close",
        aria("label") := "Close",
        onClick       := (ev => { ev.stopPropagation(); beginExit() }),
        unsafeHtml(ToastCloseSvg),
      )
    else NoMod

  val onClickMod: Mod = e.onClick match
    case Some(cb) => onClick := (_ => cb())
    case None     => NoMod

  val onKeyMod: Mod = e.onClick match
    case Some(cb) =>
      onKeyDown := { ev =>
        if ev.key == "Enter" || ev.key == " " then
          ev.preventDefault()
          cb()
      }
    case None => NoMod

  val tabMod:       Mod = if e.onClick.isDefined then tabIndex := "0" else NoMod
  val clickableMod: Mod = if e.onClick.isDefined then data("clickable") := "true" else NoMod

  // While the kind is urgent (error/warning) announce assertively; otherwise politely.
  val (ariaRole, ariaLive) = e.kind match
    case ToastType.Error | ToastType.Warning => ("alert", "assertive")
    case _                                   => ("status", "polite")

  if presence.mounted then
    div(
      cls           := parts.item,
      role          := ariaRole,
      aria("live")  := ariaLive,
      data("part")  := "toast",
      data("type")  := e.kind.token,
      data("state") := presence.phase.token,
      data("id")    := e.id.toString,
      onMouseEnter  := (_ => setPaused(true)),
      onMouseLeave  := (_ => setPaused(false)),
      onClickMod,
      onKeyMod,
      tabMod,
      clickableMod,
      iconNode,
      div(
        cls          := parts.body,
        data("part") := "body",
        div(cls := parts.message, data("part") := "message", e.message),
        descNode,
      ),
      closeNode,
    )
  else VEmpty
}

/** Mount this once (anywhere in the tree) to display toasts. It subscribes to [[ToastStore]],
  * groups the live toasts by placement, and portals each placement's stack to `document.body`
  * so they overlay the whole page regardless of where the Toaster sits. */
private val ToasterImpl = component[Unit] { _ =>
  val skin    = useSkin()
  val entries = useSyncExternalStore[Vector[ToastEntry]](ToastStore.subscribe, () => ToastStore.snapshot)

  val regions: Seq[VNode] =
    ToastPlacement.values.toSeq.flatMap { placement =>
      val group = entries.filter(_.placement == placement)
      if group.isEmpty then None
      else
        Some(
          div(
            cls               := skin.toastRegion(placement),
            data("part")      := "toast-region",
            data("placement") := placement.token,
            role              := "region",
            aria("label")     := "Notifications",
            group.map(en => ToastItemImpl(en, en.id.toString)),
          )
        )
    }

  portal(dom.document.body, fragment(regions*))
}

/** Render the toast layer. See [[ToasterImpl]]. */
def Toaster(): VNode = ToasterImpl(())

// Feather-style icons, inlined so the component has no external icon dependency.
private def defaultIconSvg(kind: ToastType): String = kind match
  case ToastType.Success => ToastSuccessSvg
  case ToastType.Error   => ToastErrorSvg
  case ToastType.Warning => ToastWarningSvg
  case _                 => ToastInfoSvg

private val ToastSuccessSvg =
  """<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path><polyline points="22 4 12 14.01 9 11.01"></polyline></svg>"""

private val ToastErrorSvg =
  """<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="8" x2="12" y2="12"></line><line x1="12" y1="16" x2="12.01" y2="16"></line></svg>"""

private val ToastWarningSvg =
  """<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"></path><line x1="12" y1="9" x2="12" y2="13"></line><line x1="12" y1="17" x2="12.01" y2="17"></line></svg>"""

private val ToastInfoSvg =
  """<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="16" x2="12" y2="12"></line><line x1="12" y1="8" x2="12.01" y2="8"></line></svg>"""

private val ToastCloseSvg =
  """<svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><line x1="18" y1="6" x2="6" y2="18"></line><line x1="6" y1="6" x2="18" y2="18"></line></svg>"""
