package io.github.edadma.vdom

/** The lifecycle phase of a presence-managed element, mirrored to `data-state` so a skin
  * styles each step. `Enter` is the just-mounted initial state (the element exists but its
  * open styles have not been applied); a frame later it advances to `Open`, so a CSS
  * `transition` from the enter state to the open state runs. `Exit` is the leaving state —
  * the element is still mounted so its exit animation can play before it is removed. */
enum PresencePhase:
  case Enter, Open, Exit

  /** The `data-state` token for this phase. */
  def token: String = this match
    case Enter => "enter"
    case Open  => "open"
    case Exit  => "exit"

/** What [[usePresence]] reports each render: whether the element should be in the host tree
  * at all (`mounted`), and which lifecycle [[PresencePhase]] it is in. A plain named tuple,
  * never a case class. */
type Presence = (mounted: Boolean, phase: PresencePhase)

/** Keep an element mounted through its exit animation. The reconciler unmounts a subtree the
  * instant its `open` condition turns false, which gives no chance to animate a close;
  * `usePresence` bridges that gap. Drive it with the caller's `open` flag and the exit
  * animation's duration (`exitMs`), then render the element only while `mounted` is true
  * and mirror `phase` to `data-state`:
  *
  * {{{
  * val p = usePresence(open, exitMs = 200)
  * if p.mounted then
  *   portal(body, div(data("state") := p.phase.token, ...))
  * else VEmpty
  * }}}
  *
  * The phase sequence on open is `Enter` → (one frame later) `Open`, so a CSS `transition`
  * keyed on `[data-state]` animates the element in; on close it becomes `Exit` and stays
  * mounted for `exitMs` before `mounted` flips to false. A re-open mid-exit cancels the
  * pending unmount and re-enters. The frame and the delay are taken from the same seams as
  * [[useTransition]] / the value hooks, so tests drive both deterministically. This is the
  * shared foundation for Modal, Drawer, Toast, Tooltip and any other dismissible overlay.
  */
def usePresence(open: Boolean, exitMs: Int)(using Hooks): Presence =
  val (mounted, setMounted, _) = useState(open)
  val (phase, setPhase, _)     = useState(if open then PresencePhase.Enter else PresencePhase.Exit)

  useLayoutEffect(
    () =>
      if open then
        // Appearing (or re-appearing mid-exit): ensure it is mounted, start at Enter, then
        // advance to Open on the next frame so the enter→open transition has two distinct
        // committed states to animate between. The pending frame is cancelled if `open`
        // flips again before it fires.
        setMounted(true)
        setPhase(PresencePhase.Enter)
        val frame = Transition.requestFrame(() => setPhase(PresencePhase.Open))
        () => Transition.cancelFrame(frame)
      else if mounted then
        // Leaving: stay mounted and switch to Exit so the close animation can play, then
        // unmount after `exitMs`. The timer is cancelled if `open` turns true again first.
        setPhase(PresencePhase.Exit)
        Timers.schedule(() => setMounted(false), exitMs)
      else
        // Already absent and still closed (e.g. first mount with open = false): nothing to do.
        noCleanup
    ,
    Array(open),
  )

  (mounted = mounted, phase = phase)
