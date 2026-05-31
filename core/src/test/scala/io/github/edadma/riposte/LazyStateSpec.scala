package io.github.edadma.riposte

import org.scalajs.dom
import org.scalatest.funsuite.AnyFunSuite

// useState takes its initial value by-name, so the initializer expression is evaluated
// exactly once (when the cell is first created) and never again — the lazy-initializer
// contract. These pin that: the expression runs once across many renders, a mutable
// object built that way stays stable, and the setter/updater behave as ever.
class LazyStateSpec extends AnyFunSuite:

  private def host(): dom.Element =
    val c = dom.document.createElement("div")
    dom.document.body.appendChild(c)
    c

  test("the by-name initializer runs once, not on every render"):
    var inits             = 0
    var bump: Int => Unit = null
    val Comp = view {
      val (s, _, _)   = useState({ inits += 1; "value" })
      val (n, set, _) = useState(0)
      bump = set
      div(s"$s-$n")
    }
    val c = host()
    render(Comp(), c)
    Scheduler.flushSync()
    assert(inits == 1)
    assert(c.textContent.contains("value-0"))
    bump(1) // force a re-render
    Scheduler.flushSync()
    bump(2)
    Scheduler.flushSync()
    assert(c.textContent.contains("value-2"))
    assert(inits == 1) // initializer never ran again

  test("a by-name-built mutable object is stable across renders"):
    class Box { var n = 0 }
    var bump: Int => Unit = null
    var seen: Box         = null
    val Comp = view {
      val (box, _, _) = useState(new Box)
      val (n, set, _) = useState(0)
      bump = set
      seen = box
      div(s"$n")
    }
    val c = host()
    render(Comp(), c)
    Scheduler.flushSync()
    val first = seen
    seen.n = 42
    bump(1)
    Scheduler.flushSync()
    assert(seen eq first) // same object instance after a re-render
    assert(seen.n == 42)  // mutations to it survived

  test("the setter and functional updater still drive re-renders"):
    var set:    Int => Unit          = null
    var update: (Int => Int) => Unit = null
    val Comp = view {
      val (n, s, u) = useState(10)
      set = s
      update = u
      div(s"n: $n")
    }
    val c = host()
    render(Comp(), c)
    Scheduler.flushSync()
    assert(c.textContent.contains("n: 10"))
    set(20)
    Scheduler.flushSync()
    assert(c.textContent.contains("n: 20"))
    update(_ + 5)
    Scheduler.flushSync()
    assert(c.textContent.contains("n: 25"))
