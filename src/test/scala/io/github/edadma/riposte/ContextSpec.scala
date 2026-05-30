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
