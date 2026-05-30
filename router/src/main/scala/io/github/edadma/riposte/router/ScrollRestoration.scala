package io.github.edadma.riposte.router

import io.github.edadma.riposte.*
import org.scalajs.dom
import scala.collection.mutable

// Browser-like scroll behaviour for a single-page app: scroll to the top when you
// navigate to a new page, and restore where you were when you go back. Drop one
// `ScrollRestoration()` near the root of the app, below the router. It renders
// nothing — it only watches the location and drives the window scroll.

// The last scroll offset seen on each path, so returning to a page restores it.
private val scrollPositions = mutable.Map.empty[String, (Double, Double)]

// How scroll is read and written — the window by default, overridable in tests
// where jsdom has no real layout.
private var readScroll:  () => (Double, Double)  = () => (dom.window.scrollX, dom.window.scrollY)
private var writeScroll: (Double, Double) => Unit = (x, y) => dom.window.scrollTo(x.toInt, y.toInt)

// Remember the current scroll for `path`; restore `path`'s saved offset, or scroll
// to the top if it has never been visited.
private def rememberScroll(path: String): Unit = scrollPositions(path) = readScroll()
private def restoreScroll(path: String): Unit =
  val (x, y) = scrollPositions.getOrElse(path, (0d, 0d))
  writeScroll(x, y)

// Restore on navigation and keep recording the offset while a page is on screen.
// Watch the location: each time the path changes the layout effect re-runs,
// restoring that path's saved offset (top for a fresh page), then listens for
// scroll so the offset stays current until we leave. The map persists across
// navigations, so going back finds the offset the page had when we left it.
val ScrollRestoration = view {
  val path = useLocation()
  useLayoutEffect(
    () =>
      restoreScroll(path)
      val onScroll = (_: dom.Event) => rememberScroll(path)
      dom.window.addEventListener("scroll", onScroll)
      () => dom.window.removeEventListener("scroll", onScroll),
    Array(path),
  )
  empty
}

// Test seam: drop the remembered positions and swap in fake scroll get/set, since
// jsdom reports no real scroll offset and ignores `scrollTo`.
private[router] def resetScrollRestoration(
    read:  () => (Double, Double),
    write: (Double, Double) => Unit,
): Unit =
  scrollPositions.clear()
  readScroll = read
  writeScroll = write
