package io.github.edadma.riposte

// useContext + providers: reading the nearest provided value, falling back to
// the default, nested overrides, and propagation when a provided value changes.
class ContextSpec extends DomSuite:

  private val Theme = createContext("light")
  private val Label = view {
    val theme = useContext(Theme)
    span(cls := "t", theme)
  }

  test("reads the nearest provided value"):
    val c = host()
    render(Theme.provide("dark", Label()), c)
    assert(c.querySelector("span.t").textContent == "dark")

  test("falls back to the default outside any provider"):
    val c = host()
    render(Label(), c)
    assert(c.querySelector("span.t").textContent == "light")

  test("the nearest provider wins when nested"):
    val c = host()
    render(Theme.provide("dark", Theme.provide("solarized", Label())), c)
    assert(c.querySelector("span.t").textContent == "solarized")

  test("a changed provided value propagates to consumers"):
    val c = host()
    val root = createRoot(c)
    root.render(Theme.provide("dark", Label()))
    assert(c.querySelector("span.t").textContent == "dark")
    root.render(Theme.provide("light", Label()))
    assert(c.querySelector("span.t").textContent == "light")

  test("a consumer nested below intervening elements still resolves context"):
    val c = host()
    render(Theme.provide("dark", div(cls := "wrap", ul(li(Label())))), c)
    assert(c.querySelector("span.t").textContent == "dark")

  test("an outer provider change does not wake a consumer shielded by an inner provider"):
    val c = host()
    var innerRenders = 0
    // Memoized so the top-down patch bails — the only thing that could re-render
    // it is the context-invalidation walk, which must stop at the inner provider.
    val Inner = memo(view {
      val t = useContext(Theme)
      innerRenders += 1
      span(cls := "inner", t)
    })
    def tree(outer: String): VNode = Theme.provide(outer, div(Theme.provide("fixed", Inner())))
    val root = createRoot(c)
    root.render(tree("dark"))
    Scheduler.flushSync()
    assert(c.querySelector("span.inner").textContent == "fixed")
    assert(innerRenders == 1)
    // Change ONLY the outer provider. Inner reads its own enclosing provider
    // ("fixed", unchanged), so the invalidation walk stops at that boundary and
    // Inner is not woken — a global subscriber set would over-invalidate here.
    root.render(tree("light"))
    Scheduler.flushSync()
    assert(innerRenders == 1)
    assert(c.querySelector("span.inner").textContent == "fixed")
