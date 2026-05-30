package io.github.edadma.riposte.router

import io.github.edadma.riposte.*
import org.scalajs.dom
import scala.collection.mutable

// Where the router reads and writes the current location. History mode uses clean
// paths (`/users/7`) through the History API; hash mode keeps everything after a
// `#` (`/#/users/7`), which needs no server fallback. The store notifies its own
// subscribers the moment it navigates — `pushState` never fires `popstate`, in a
// real browser or in jsdom — and additionally listens for `popstate` / `hashchange`
// to pick up the back/forward buttons and external URL edits.

enum RouterMode:
  case History, Hash

object Location:
  private var mode      = RouterMode.History
  private val listeners = mutable.LinkedHashSet.empty[() => Unit]
  private var wired     = false

  // Choose history vs hash mode. Call once at startup, before rendering routes.
  def setMode(m: RouterMode): Unit = mode = m

  // The current matchable path, normalised to begin with "/" and stripped of any
  // query string. History mode reads `pathname` (the query lives separately in
  // `search`); hash mode reads everything after the `#`, up to a `?`.
  def current(): String = mode match
    case RouterMode.History =>
      val p = dom.window.location.pathname
      if p.isEmpty then "/" else p
    case RouterMode.Hash =>
      splitQuery(hashBody())._1

  // The current query string, without the leading "?". History mode reads
  // `location.search`; hash mode reads what follows a `?` inside the hash.
  def search(): String = mode match
    case RouterMode.History =>
      val s = dom.window.location.search
      if s.startsWith("?") then s.substring(1) else s
    case RouterMode.Hash =>
      splitQuery(hashBody())._2

  // The hash with its leading "#" removed, defaulting to "/" when empty.
  private def hashBody(): String =
    val h = dom.window.location.hash
    if h.length > 1 then h.substring(1) else "/"

  // Split "path?query" into its path and query halves (query without the "?").
  private def splitQuery(s: String): (String, String) =
    val i = s.indexOf('?')
    if i < 0 then (s, "") else (s.substring(0, i), s.substring(i + 1))

  // The value to put in a link's href for `to`, so the anchor is real and a
  // modifier-click (open in new tab) still works: the bare path in history mode,
  // the `#`-prefixed path in hash mode.
  def hrefFor(to: String): String = mode match
    case RouterMode.History => to
    case RouterMode.Hash    => "#" + to

  // Navigate to `to`, pushing a new history entry (or replacing the current one).
  // Always via `pushState` / `replaceState`, which fire no event, then notify
  // subscribers directly — exactly once.
  def navigate(to: String, replace: Boolean): Unit =
    val url = hrefFor(to)
    if replace then dom.window.history.replaceState(null, "", url)
    else dom.window.history.pushState(null, "", url)
    notifyListeners()

  // Replace the query string while staying on the current path, then navigate.
  // `pushState` by default; `replace = true` edits the current entry instead.
  def setSearch(params: Params, replace: Boolean): Unit =
    val qs = encodeQuery(params)
    val to = if qs.isEmpty then current() else current() + "?" + qs
    navigate(to, replace)

  def subscribe(callback: () => Unit): () => Unit =
    ensureWired()
    listeners += callback
    () =>
      listeners -= callback
      ()

  private def notifyListeners(): Unit = listeners.toVector.foreach(_())

  // Attach the window listeners once, lazily on first subscribe. They only re-read
  // the location and notify, so they cover back/forward and any external change.
  private def ensureWired(): Unit =
    if !wired then
      wired = true
      dom.window.addEventListener("popstate", (_: dom.Event) => notifyListeners())
      dom.window.addEventListener("hashchange", (_: dom.Event) => notifyListeners())

  // Test seam: drop all subscribers, set the mode, and reset the URL, so each test
  // starts from a clean location. Leaves `wired` alone so the single pair of window
  // listeners is not duplicated across tests.
  private[router] def reset(m: RouterMode): Unit =
    listeners.clear()
    mode = m
    val url = m match
      case RouterMode.History => "/"
      case RouterMode.Hash    => "#/"
    dom.window.history.replaceState(null, "", url)

// Read the current path; the calling component re-renders whenever it changes.
def useLocation()(using Hooks): String =
  useSyncExternalStore(Location.subscribe, () => Location.current())

// Imperatively navigate. `navigate("/users/7")` pushes a new entry; `replace = true`
// replaces the current one (e.g. after a redirect).
def navigate(to: String, replace: Boolean = false): Unit = Location.navigate(to, replace)

// The navigator, for parity with React Router's `useNavigate`. It needs no hook
// state — the location store is global — so the returned function is stable.
def useNavigate(): String => Unit = to => navigate(to)

// The current query string parsed into a map, plus a setter that navigates to the
// same path with a new query — the router's `useSearchParams`. The component
// re-renders whenever the query changes. The setter pushes a new history entry;
// `setParams(map, replace = true)` edits the current one instead.
def useSearchParams()(using Hooks): (Params, (Params, Boolean) => Unit) =
  val raw    = useSyncExternalStore(Location.subscribe, () => Location.search())
  val params = parseQuery(raw)
  (params, (next, replace) => Location.setSearch(next, replace))
