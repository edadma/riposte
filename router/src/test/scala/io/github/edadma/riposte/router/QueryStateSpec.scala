package io.github.edadma.riposte.router

import io.github.edadma.riposte.*
import org.scalajs.dom

// `useQueryState` in two layers: the codecs are pure and need no DOM, so they get
// plain round-trip assertions; the hook drives the real `Location` store under
// jsdom, exactly like the `useSearchParams` suite — render a view, navigate, click,
// then read back the text and the address bar.
class QueryStateSpec extends RouterSuite:

  // --- the codec layer: pure, no DOM -------------------------------------------

  test("the int codec round-trips and rejects garbage"):
    assert(QueryCodec.int.parse("42") == Some(42))
    assert(QueryCodec.int.parse("-7") == Some(-7))
    assert(QueryCodec.int.parse("abc") == None)
    assert(QueryCodec.int.render(42) == "42")

  test("the boolean codec reads true/false and rejects anything else"):
    assert(QueryCodec.boolean.parse("true") == Some(true))
    assert(QueryCodec.boolean.parse("false") == Some(false))
    assert(QueryCodec.boolean.parse("nope") == None)
    assert(QueryCodec.boolean.render(false) == "false")

  test("the double and string codecs round-trip"):
    assert(QueryCodec.double.parse("1.5") == Some(1.5))
    assert(QueryCodec.double.parse("x") == None)
    assert(QueryCodec.string.parse("hello") == Some("hello"))
    assert(QueryCodec.string.render("hello") == "hello")

  // --- the hook: jsdom, against the live Location store ------------------------

  test("reads a typed parameter and the setter writes it"):
    start()
    val c    = host()
    val View = view {
      val (count, setCount, _) = useQueryState("count", 0)
      div(
        span(cls := "v", count.toString),
        button(onClick := (_ => setCount(5)), "set"),
      )
    }
    render(View(), c)
    navigate("/x?count=3")
    Scheduler.flushSync()
    assert(c.querySelector("span.v").textContent == "3")
    click(c.querySelector("button"))
    assert(c.querySelector("span.v").textContent == "5")
    assert(dom.window.location.search == "?count=5")

  test("an absent or unparseable value reads as the default"):
    start()
    val c    = host()
    val View = view {
      val (count, _, _) = useQueryState("count", 0)
      span(cls := "v", count.toString)
    }
    render(View(), c)
    Scheduler.flushSync()
    assert(c.querySelector("span.v").textContent == "0") // absent
    navigate("/x?count=oops")
    Scheduler.flushSync()
    assert(c.querySelector("span.v").textContent == "0") // unparseable

  test("setting the default value removes the key but leaves the others"):
    start()
    val c    = host()
    val View = view {
      val (count, setCount, _) = useQueryState("count", 0)
      div(
        span(cls := "v", count.toString),
        button(onClick := (_ => setCount(0)), "clear"),
      )
    }
    render(View(), c)
    navigate("/x?count=5&q=hi")
    Scheduler.flushSync()
    assert(c.querySelector("span.v").textContent == "5")
    click(c.querySelector("button"))
    assert(c.querySelector("span.v").textContent == "0")
    assert(dom.window.location.search == "?q=hi") // count gone, q preserved

  test("independent keys do not clobber each other"):
    start()
    val c    = host()
    val View = view {
      val (count, setCount, _) = useQueryState("count", 0)
      val (q, _, _)            = useQueryState("q", "")
      div(
        span(cls := "c", count.toString),
        span(cls := "q", q),
        button(onClick := (_ => setCount(9)), "set"),
      )
    }
    render(View(), c)
    navigate("/x?count=1&q=cats")
    Scheduler.flushSync()
    assert(c.querySelector("span.c").textContent == "1")
    assert(c.querySelector("span.q").textContent == "cats")
    click(c.querySelector("button"))
    assert(c.querySelector("span.c").textContent == "9")
    assert(c.querySelector("span.q").textContent == "cats") // untouched
    assert(dom.window.location.search == "?count=9&q=cats")

  test("the update form reads the live value"):
    start()
    val c    = host()
    val View = view {
      val (count, _, updateCount) = useQueryState("count", 0)
      div(
        span(cls := "v", count.toString),
        button(onClick := (_ => updateCount(_ + 1)), "inc"),
      )
    }
    render(View(), c)
    navigate("/x?count=10")
    Scheduler.flushSync()
    click(c.querySelector("button"))
    assert(c.querySelector("span.v").textContent == "11")
    assert(dom.window.location.search == "?count=11")

  test("a boolean parameter round-trips through the URL"):
    start()
    val c    = host()
    val View = view {
      val (open, setOpen, _) = useQueryState("open", false)
      div(
        span(cls := "v", open.toString),
        button(onClick := (_ => setOpen(true)), "open"),
      )
    }
    render(View(), c)
    Scheduler.flushSync()
    assert(c.querySelector("span.v").textContent == "false") // default, no key
    click(c.querySelector("button"))
    assert(c.querySelector("span.v").textContent == "true")
    assert(dom.window.location.search == "?open=true")

  test("the setter replaces history by default and pushes on request"):
    start()
    val c    = host()
    val View = view {
      val (count, setCount, _) = useQueryState("count", 0)
      div(
        span(cls := "v", count.toString),
        button(cls := "rep", onClick := (_ => setCount(1)), "replace"),
        button(cls := "push", onClick := (_ => setCount(2, push = true)), "push"),
      )
    }
    render(View(), c)
    Scheduler.flushSync()
    val before = dom.window.history.length
    click(c.querySelector("button.rep"))
    assert(dom.window.history.length == before) // replaceState: no new entry
    click(c.querySelector("button.push"))
    assert(dom.window.history.length == before + 1) // pushState: one new entry
