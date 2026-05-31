package io.github.edadma.riposte

import org.scalajs.dom

// Reaching into a child from its parent: forwarding a ref to the child's DOM node
// (the plain ref-as-prop pattern), and exposing a custom imperative handle with
// useImperativeHandle.
class ImperativeHandleSpec extends DomSuite:

  test("a ref passed as a prop forwards to the child's DOM node"):
    var node: dom.Element | Null = null
    val Field = component[Ref[dom.html.Input | Null]] { r =>
      input(ref := r)
    }
    val Parent = view {
      val r = useRef[dom.html.Input | Null](null)
      useEffect(() => { node = r.current; noCleanup }, Array())
      Field(r)
    }
    render(Parent(), host())
    Scheduler.flushSync()
    assert(node != null)
    assert(node.tagName.toLowerCase == "input")

  test("useImperativeHandle lets the parent drive the child through the ref"):
    trait Api:
      def bump(): Unit
    val Child = component[Ref[Api | Null]] { ref =>
      val (n, _, update) = useState(0)
      useImperativeHandle(ref, () => new Api { def bump() = update(_ + 1) }, Array())
      span(cls := "n", n)
    }
    val Parent = view {
      val h = useRef[Api | Null](null)
      div(
        button(onClick := (_ => { val a = h.current; if a != null then a.bump() })),
        Child(h),
      )
    }
    val c = host()
    render(Parent(), c)
    Scheduler.flushSync()
    assert(c.querySelector("span.n").textContent == "0")
    fireClick(c.querySelector("button")) // parent calls the child's exposed bump()
    assert(c.querySelector("span.n").textContent == "1")
