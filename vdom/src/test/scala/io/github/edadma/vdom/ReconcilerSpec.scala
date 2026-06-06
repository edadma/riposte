package io.github.edadma.vdom

// The reconciler, exercised headlessly through `TestHost` — no DOM, no browser.
// This is the payoff of the host-agnostic core: the diff engine's structural
// behaviour (mount, patch, replace, keyed moves, fragments, portals, boundaries)
// is verified on the JVM, where it is just data-structure manipulation.
class ReconcilerSpec extends VdomSuite:

  test("mounts an element tree with attributes, child elements, and text"):
    val c = container()
    createRoot(c).render(el("div", attrib("class", "x"))(el("span")("hi"), "world"))
    assert(host.serialize(c) == """<root><div class="x"><span>hi</span>world</div></root>""")

  test("patches text in place without recreating the node"):
    val c    = container()
    val root = createRoot(c)
    root.render(el("p")("a"))
    val textNode = c.children.head.asInstanceOf[TestElement].children.head
    root.render(el("p")("b"))
    assert(host.textOf(c) == "b")
    assert(c.children.head.asInstanceOf[TestElement].children.head eq textNode)

  test("a changed tag replaces the node"):
    val c    = container()
    val root = createRoot(c)
    root.render(el("div")("x"))
    val first = c.children.head
    root.render(el("section")("x"))
    assert(c.children.head.asInstanceOf[TestElement].tag == "section")
    assert(!(c.children.head eq first))

  test("sets, updates, and removes attributes across patches"):
    val c    = container()
    val root = createRoot(c)
    root.render(el("div", attrib("id", "a"), attrib("title", "t"))())
    val e = c.children.head.asInstanceOf[TestElement]
    assert(e.attributes("id") == "a" && e.attributes("title") == "t")
    root.render(el("div", attrib("id", "b"))())
    assert(e.attributes("id") == "b")
    assert(!e.attributes.contains("title"))

  test("an event drives a state update and a re-render"):
    val c = container()
    val Counter = view {
      val (n, _, update) = useState(0)
      el("button", onClick(_ => update(_ + 1)))(s"count $n")
    }
    createRoot(c).render(Counter())
    assert(host.textOf(c) == "count 0")
    host.fire(c.children.head, "click")
    Scheduler.flushSync()
    assert(host.textOf(c) == "count 1")

  test("keyed children reorder without recreating their nodes"):
    val c    = container()
    val root = createRoot(c)
    def list(keys: Seq[String]) = el("ul")(keys.map(k => keyed("li", k)(k))*)
    root.render(list(Seq("a", "b", "c")))
    val ul  = c.children.head.asInstanceOf[TestElement]
    val liA = ul.children(0); val liB = ul.children(1); val liC = ul.children(2)
    root.render(list(Seq("c", "a", "b")))
    assert(ul.children.map(host.textOf).mkString == "cab")
    assert((ul.children(0) eq liC) && (ul.children(1) eq liA) && (ul.children(2) eq liB))

  test("a VEmpty placeholder holds its slot so following siblings are stable"):
    val c    = container()
    val root = createRoot(c)
    root.render(el("div")(VEmpty, el("span")("x")))
    val span = c.children.head.asInstanceOf[TestElement].children(1)
    root.render(el("div")(el("b")("shown"), el("span")("x")))
    val div = c.children.head.asInstanceOf[TestElement]
    assert(div.children(0).asInstanceOf[TestElement].tag == "b")
    assert(div.children(1) eq span)

  test("a fragment splices its children without a wrapper element"):
    val c = container()
    createRoot(c).render(el("div")(VFragment(Vector(VText("a"), VText("b"))), VText("c")))
    assert(host.textOf(c) == "abc")

  test("a portal renders its child into the target, not the main tree"):
    val c      = container()
    val target = container()
    createRoot(c).render(el("div")(VPortal(target, el("span")("p")), VText("main")))
    assert(host.textOf(c) == "main")
    assert(host.textOf(target) == "p")

  test("an error boundary shows the fallback when a child throws on mount"):
    val c    = container()
    val Boom = view { throw new RuntimeException("boom") }
    createRoot(c).render(VErrorBoundary(e => el("p")(s"caught: ${e.getMessage}"), Boom()))
    assert(host.textOf(c) == "caught: boom")

  test("unmount tears the tree out of the host"):
    val c    = container()
    val root = createRoot(c)
    root.render(el("div")("x"))
    assert(c.children.nonEmpty)
    root.unmount()
    assert(c.children.isEmpty)
