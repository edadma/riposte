package io.github.edadma.riposte

import org.scalajs.dom

// SVG elements must be created in the SVG namespace, or the browser treats them
// as unknown HTML and nothing draws. The reconciler enters the namespace at an
// `<svg>` tag and keeps it for every descendant — including children mounted
// later during a patch.
class SvgSpec extends DomSuite:

  private val SvgNs  = "http://www.w3.org/2000/svg"
  private val HtmlNs = "http://www.w3.org/1999/xhtml"

  test("an svg element and its descendants are in the SVG namespace"):
    val c = host()
    render(svg(viewBox := "0 0 10 10", path(d := "M0 0L10 10")), c)
    Scheduler.flushSync()
    val s = c.querySelector("svg")
    val p = c.querySelector("path")
    assert(s.namespaceURI == SvgNs)
    assert(p.namespaceURI == SvgNs)
    assert(s.getAttribute("viewBox") == "0 0 10 10")
    assert(p.getAttribute("d") == "M0 0L10 10")

  test("html siblings of an svg stay in the HTML namespace"):
    val c = host()
    render(div(span("label"), svg(circle(cx := 5, cy := 5, r := 4))), c)
    Scheduler.flushSync()
    assert(c.querySelector("span").namespaceURI == HtmlNs)
    assert(c.querySelector("circle").namespaceURI == SvgNs)

  test("a child added to an svg during a patch is namespaced correctly"):
    val c = host()
    val Chart = view {
      val (n, _, update) = useState(1)
      div(
        button(onClick := (_ => update(_ + 1)), "add"),
        svg(
          (0 until n).map(i => circle(key := i, cx := i, cy := 0, r := 1)): Seq[VNode],
        ),
      )
    }
    render(Chart(), c)
    Scheduler.flushSync()
    assert(c.querySelectorAll("circle").length == 1)
    fireClick(c.querySelector("button"))
    val circles = c.querySelectorAll("circle")
    assert(circles.length == 2)
    // The freshly-mounted second circle must also be in the SVG namespace.
    assert(circles(1).asInstanceOf[dom.Element].namespaceURI == SvgNs)
