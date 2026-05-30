package io.github.edadma.riposte

// An error boundary contains a render failure in its subtree: instead of the
// exception tearing down the tree, the boundary shows a fallback. It catches
// throws on the synchronous mount path, on a parent-driven patch, and on a
// scheduler-driven re-render from a state update inside the subtree — and a
// later successful render recovers from the fallback.
class ErrorBoundarySpec extends DomSuite:

  test("a throw while mounting the child shows the fallback"):
    val c    = host()
    val Boom = view { throw new RuntimeException("boom") }
    render(errorBoundary(e => p(cls := "fb", e.getMessage))(Boom()), c)
    Scheduler.flushSync()
    assert(c.querySelector("p.fb").textContent == "boom")

  test("a throw on a parent-driven re-render is caught"):
    val c     = host()
    val Risky = component[Int] { n =>
      if n >= 3 then throw new RuntimeException("too big")
      span(cls := "v", n)
    }
    val App = view {
      val (n, _, update) = useState(0)
      div(
        button(onClick := (_ => update(_ + 1)), "inc"),
        errorBoundary(_ => span(cls := "fb", "caught"))(Risky(n)),
      )
    }
    render(App(), c)
    Scheduler.flushSync()
    assert(c.querySelector("span.v").textContent == "0")
    val inc = c.querySelector("button")
    fireClick(inc) // 1
    fireClick(inc) // 2
    assert(c.querySelector("span.v").textContent == "2")
    fireClick(inc) // 3 → Risky throws as the parent patches it
    assert(c.querySelector("span.fb") != null)
    assert(c.querySelector("span.v") == null)

  test("a throw on a state-driven re-render inside the subtree is caught"):
    val c     = host()
    val Risky = view {
      val (n, _, update) = useState(0)
      if n >= 1 then throw new RuntimeException("self")
      button(onClick := (_ => update(_ + 1)), n)
    }
    render(errorBoundary(_ => span(cls := "fb", "caught"))(Risky()), c)
    Scheduler.flushSync()
    assert(c.querySelector("button").textContent == "0")
    fireClick(c.querySelector("button")) // Risky's own re-render throws
    assert(c.querySelector("span.fb") != null)
    assert(c.querySelector("button") == null)

  test("the boundary recovers when a later render no longer throws"):
    val c     = host()
    val Risky = component[Int] { n =>
      if n >= 3 then throw new RuntimeException("x")
      span(cls := "v", n)
    }
    val App = view {
      val (n, _, update) = useState(3)
      div(
        button(onClick := (_ => update(_ - 1)), "dec"),
        errorBoundary(_ => span(cls := "fb", "caught"))(Risky(n)),
      )
    }
    render(App(), c)
    Scheduler.flushSync()
    assert(c.querySelector("span.fb") != null) // mounted broken
    assert(c.querySelector("span.v") == null)
    fireClick(c.querySelector("button")) // n = 2 → child no longer throws
    assert(c.querySelector("span.v").textContent == "2")
    assert(c.querySelector("span.fb") == null)
