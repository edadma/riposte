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

  test("a typed PropValue is delivered to setProperty, updated, and reset on removal"):
    val c    = container()
    val root = createRoot(c)
    final case class Rgba(r: Int, g: Int, b: Int)
    root.render(el("box", "bg" -> PropValue(Rgba(255, 0, 0)))())
    val e = c.children.head.asInstanceOf[TestElement]
    assert(e.properties("bg") == Rgba(255, 0, 0)) // the host gets the value itself, not a string
    assert(!e.attributes.contains("bg"))          // and not as an attribute
    root.render(el("box", "bg" -> PropValue(Rgba(0, 0, 255)))())
    assert(e.properties("bg") == Rgba(0, 0, 255)) // a changed value re-sets the property
    root.render(el("box")())
    assert(e.properties("bg") == null)            // removal hands the host null to reset the field

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

  // --- event listeners -------------------------------------------------------

  test("an unchanged handler keeps its listener registration across a patch"):
    val c      = container()
    val root   = createRoot(c)
    val stable = (_: Any) => ()
    root.render(el("button", onClick(stable))("a"))
    val e     = c.children.head.asInstanceOf[TestElement]
    val first = e.listeners.head
    root.render(el("button", onClick(stable))("b"))
    assert(e.listeners.length == 1)
    assert(e.listeners.head eq first) // not torn down and re-added — the point of useCallback
    root.render(el("button", onClick(_ => ()))("c"))
    assert(e.listeners.length == 1)
    assert(!(e.listeners.head eq first)) // a different function does re-register

  test("changed listener options re-register the listener"):
    val c      = container()
    val root   = createRoot(c)
    val stable = (_: Any) => ()
    root.render(el("button", "on:click" -> Handler(stable))())
    val e     = c.children.head.asInstanceOf[TestElement]
    val first = e.listeners.head
    assert(!first.once && !first.passive)
    root.render(el("button", "on:click" -> Handler(stable, EventOptions(once = true, passive = true)))())
    assert(e.listeners.length == 1)
    assert(!(e.listeners.head eq first))
    assert(e.listeners.head.once && e.listeners.head.passive)

  test("a capture listener and a bubble listener for one event coexist and are told apart"):
    val c    = container()
    val seen = scala.collection.mutable.ArrayBuffer.empty[String]
    createRoot(c).render(
      el("button", "on:click" -> Handler(_ => seen += "bubble"), "on:click:capture" -> Handler(_ => seen += "capture"))(),
    )
    val e = c.children.head.asInstanceOf[TestElement]
    assert(e.listeners.length == 2)
    host.fire(e, "click")
    assert(seen.toList == List("bubble"))
    host.fireCapture(e, "click")
    assert(seen.toList == List("bubble", "capture"))

  test("a handler dropped from the props is removed from the element"):
    val c    = container()
    val root = createRoot(c)
    root.render(el("button", onClick(_ => ()))())
    val e = c.children.head.asInstanceOf[TestElement]
    assert(e.listeners.length == 1)
    root.render(el("button")())
    assert(e.listeners.isEmpty)

  // --- error boundaries ------------------------------------------------------

  test("an error boundary retries the real child on a later patch and recovers"):
    val c    = container()
    val root = createRoot(c)
    var boom = true
    val Child = view {
      if boom then throw new RuntimeException("boom")
      el("span")("ok")
    }
    def tree = VErrorBoundary(e => el("p")(s"caught: ${e.getMessage}"), Child())
    root.render(tree)
    assert(host.textOf(c) == "caught: boom")
    boom = false
    root.render(tree) // the retry succeeds, so the real child replaces the fallback
    assert(host.textOf(c) == "ok")
    boom = true
    root.render(tree) // and it falls back again when the cause returns
    assert(host.textOf(c) == "caught: boom")

  test("an error boundary catches a throw from a state-driven re-render, not just from mount"):
    val c = container()
    val Child = view {
      val (n, _, update) = useState(0)
      if n > 0 then throw new RuntimeException("late")
      el("button", onClick(_ => update(_ + 1)))("fine")
    }
    createRoot(c).render(VErrorBoundary(e => el("p")(s"caught: ${e.getMessage}"), Child()))
    assert(host.textOf(c) == "fine")
    // The throw happens inside the scheduler's render pass, so it takes the
    // walk-up-to-the-nearest-boundary path rather than the patch path.
    host.fire(c.children.head, "click")
    Scheduler.flushSync()
    assert(host.textOf(c) == "caught: late")

  test("a render error with no enclosing boundary propagates to the caller"):
    val c    = container()
    val Boom = view { throw new RuntimeException("unguarded") }
    val ex   = intercept[RuntimeException](createRoot(c).render(el("div")(Boom())))
    assert(ex.getMessage == "unguarded")

  // --- memo ------------------------------------------------------------------

  test("a memoized component skips a parent-driven re-render when its props are equal"):
    val c       = container()
    val root    = createRoot(c)
    var renders = 0
    val Row = memo(component[String] { label =>
      renders += 1
      el("span")(label)
    })
    root.render(el("div")(Row("a")))
    assert(renders == 1)
    root.render(el("div")(Row("a"))) // equal props — bail out
    assert(renders == 1)
    root.render(el("div")(Row("b"))) // changed props — render
    assert(renders == 2)
    assert(host.textOf(c) == "b")

  test("a memoized component still re-renders on its own state change"):
    val c       = container()
    var renders = 0
    val Counter = memo(view {
      renders += 1
      val (n, _, update) = useState(0)
      el("button", onClick(_ => update(_ + 1)))(s"$n")
    })
    createRoot(c).render(Counter())
    assert(renders == 1)
    host.fire(c.children.head, "click")
    Scheduler.flushSync()
    assert(renders == 2)
    assert(host.textOf(c) == "1")

  // --- refs ------------------------------------------------------------------

  test("a box ref receives the node on mount and is cleared on unmount"):
    val c    = container()
    val box  = new Ref[AnyRef | Null](null)
    val root = createRoot(c)
    root.render(VElement("div", Map.empty, Vector.empty, None, BoxRef(box)))
    assert(box.current eq c.children.head)
    root.unmount()
    assert(box.current == null)

  test("a callback ref is invoked with the node, then with null when it is replaced"):
    val c    = container()
    val seen = scala.collection.mutable.ArrayBuffer.empty[String]
    val root = createRoot(c)
    def withRef(r: ElementRef) = VElement("div", Map.empty, Vector.empty, None, r)
    val first  = FnRef(n => seen += (if n == null then "detach-1" else "attach-1"))
    val second = FnRef(n => seen += (if n == null then "detach-2" else "attach-2"))
    root.render(withRef(first))
    assert(seen.toList == List("attach-1"))
    root.render(withRef(first)) // same ref identity — no churn
    assert(seen.toList == List("attach-1"))
    root.render(withRef(second)) // changed identity — old detaches, new attaches to the same node
    assert(seen.toList == List("attach-1", "detach-1", "attach-2"))

  test("a stable box ref survives a patch without rebinding"):
    val c    = container()
    val box  = new Ref[AnyRef | Null](null)
    val root = createRoot(c)
    def tree(text: String) = VElement("div", Map.empty, Vector(VText(text)), None, BoxRef(box))
    root.render(tree("a"))
    val node = box.current
    root.render(tree("b")) // a fresh BoxRef wrapping the SAME box compares equal
    assert(box.current eq node)

  // --- children diffing ------------------------------------------------------

  test("unkeyed children mixed among keyed ones are matched in order"):
    val c    = container()
    val root = createRoot(c)
    def tree(order: Seq[String]) =
      el("ul")((el("li")("head") +: order.map(k => keyed("li", k)(k))) *)
    root.render(tree(Seq("a", "b")))
    val ul   = c.children.head.asInstanceOf[TestElement]
    val head = ul.children(0)
    val liA  = ul.children(1)
    val liB  = ul.children(2)
    root.render(tree(Seq("b", "a")))
    assert(ul.children.map(host.textOf).mkString == "headba")
    assert(ul.children(0) eq head) // the unkeyed child matched positionally
    assert((ul.children(1) eq liB) && (ul.children(2) eq liA))

  test("a dropped key unmounts its instance and runs its cleanups"):
    val c    = container()
    val root = createRoot(c)
    val gone = scala.collection.mutable.ArrayBuffer.empty[String]
    val Item = component[String] { k =>
      useEffect(() => () => gone += k, Array())
      el("li")(k)
    }
    def tree(keys: Seq[String]) = el("ul")(keys.map(k => VComponent(Item, k, Some(k))) *)
    root.render(tree(Seq("a", "b", "c")))
    Scheduler.flushSync()
    root.render(tree(Seq("a", "c")))
    assert(gone.toList == List("b"))
    assert(host.textOf(c) == "ac")

  test("a portal whose target changes remounts the child under the new target"):
    val c  = container()
    val t1 = container()
    val t2 = container()
    val root = createRoot(c)
    root.render(el("div")(VPortal(t1, el("span")("p"))))
    assert(host.textOf(t1) == "p" && host.textOf(t2) == "")
    root.render(el("div")(VPortal(t2, el("span")("p"))))
    assert(host.textOf(t1) == "")
    assert(host.textOf(t2) == "p")

  // --- raw html --------------------------------------------------------------

  test("RawHtml is set, updated, and cleared through the host"):
    val c    = container()
    val root = createRoot(c)
    root.render(el("div", "innerHTML" -> RawHtml("<b>one</b>"))())
    val e = c.children.head.asInstanceOf[TestElement]
    assert(e.innerHtml == "<b>one</b>")
    root.render(el("div", "innerHTML" -> RawHtml("<i>two</i>"))())
    assert(e.innerHtml == "<i>two</i>")
    root.render(el("div")())
    assert(e.innerHtml == "")

  test("a style map is applied, replaced wholesale, and cleared on removal"):
    val c    = container()
    val root = createRoot(c)
    root.render(el("div", "style" -> StyleProp(Map("color" -> "red", "top" -> "0")))())
    val e = c.children.head.asInstanceOf[TestElement]
    assert(e.style == Map("color" -> "red", "top" -> "0"))
    root.render(el("div", "style" -> StyleProp(Map("color" -> "blue")))())
    assert(e.style == Map("color" -> "blue")) // replaced, not merged
    root.render(el("div")())
    assert(e.style.isEmpty)

  test("an svg subtree is created in the svg namespace, including children mounted later"):
    val c    = container()
    val root = createRoot(c)
    root.render(el("svg")(el("circle")()))
    val svg = c.children.head.asInstanceOf[TestElement]
    assert(svg.namespace == "http://www.w3.org/2000/svg")
    assert(svg.children.head.asInstanceOf[TestElement].namespace == "http://www.w3.org/2000/svg")
    root.render(el("svg")(el("circle")(), el("rect")()))
    assert(svg.children(1).asInstanceOf[TestElement].namespace == "http://www.w3.org/2000/svg")
