package io.github.edadma.riposte

import scala.collection.mutable

// useSyncExternalStore: the bridge between a store living outside the component
// tree and the re-render model. Covers reading the current value, re-rendering
// on change, selector bail-out (a notification whose selected slice is unchanged
// does not re-render), unsubscribing on unmount, and several subscribers sharing
// one store.
class ExternalStoreSpec extends DomSuite:

  // A minimal external store: a current value, a set of listeners, and a STABLE
  // `subscribe` (a val, so its identity doesn't change between renders).
  private class Store[S](initial: S):
    private var state          = initial
    private val listeners      = mutable.Set.empty[() => Unit]
    def get: S                 = state
    def listenerCount: Int     = listeners.size
    def set(s: S): Unit =
      state = s
      listeners.foreach(_())
    val subscribe: (() => Unit) => (() => Unit) = cb =>
      listeners += cb
      () => listeners -= cb

  test("reflects the store's value and re-renders when it changes"):
    val c     = host()
    val store = new Store(0)
    val Comp = view {
      val n = useSyncExternalStore(store.subscribe, () => store.get)
      span(cls := "n", n)
    }
    render(Comp(), c)
    Scheduler.flushSync()
    assert(c.querySelector("span.n").textContent == "0")
    store.set(5)
    Scheduler.flushSync()
    assert(c.querySelector("span.n").textContent == "5")

  test("a selector bails out when its slice is unchanged"):
    val c     = host()
    val store = new Store((a = 0, b = 0))
    var renders = 0
    val Comp = view {
      val a = useSyncExternalStore(store.subscribe, () => store.get.a)
      renders += 1
      span(cls := "a", a)
    }
    render(Comp(), c)
    Scheduler.flushSync()
    assert(renders == 1)
    // Change only `b`: the store fires, but the selected slice `a` is unchanged.
    store.set((a = 0, b = 9))
    Scheduler.flushSync()
    assert(renders == 1) // bailed — no re-render
    assert(c.querySelector("span.a").textContent == "0")
    // Change `a`: now the selected slice differs and it re-renders.
    store.set((a = 7, b = 9))
    Scheduler.flushSync()
    assert(renders == 2)
    assert(c.querySelector("span.a").textContent == "7")

  test("a change landing before the subscription is established is not missed"):
    val c     = host()
    val store = new Store(0)
    val Comp = view {
      val n = useSyncExternalStore(store.subscribe, () => store.get)
      span(cls := "n", n)
    }
    render(Comp(), c)
    // Mutate before flushing — the layout effect hasn't subscribed yet, so the
    // notification has no listener. The re-check at subscribe time catches it.
    store.set(3)
    Scheduler.flushSync()
    assert(c.querySelector("span.n").textContent == "3")

  test("unsubscribes from the store on unmount"):
    val c     = host()
    val store = new Store(0)
    val Comp = view {
      val n = useSyncExternalStore(store.subscribe, () => store.get)
      span(n)
    }
    val root = createRoot(c)
    root.render(Comp())
    Scheduler.flushSync()
    assert(store.listenerCount == 1)
    root.unmount()
    assert(store.listenerCount == 0)

  test("multiple subscribers all see the change"):
    val c     = host()
    val store = new Store("hi")
    val Label = view {
      val s = useSyncExternalStore(store.subscribe, () => store.get)
      span(cls := "x", s)
    }
    render(div(Label(), Label()), c)
    Scheduler.flushSync()
    assert(c.querySelectorAll("span.x").length == 2)
    store.set("bye")
    Scheduler.flushSync()
    val spans = c.querySelectorAll("span.x")
    assert(spans(0).textContent == "bye")
    assert(spans(1).textContent == "bye")
    assert(store.listenerCount == 2)
