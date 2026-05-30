package io.github.edadma.vdom

import scala.collection.mutable.ArrayBuffer

// useEffect / useLayoutEffect: run-after-commit, dependency-gated re-runs,
// cleanup before re-run and on unmount, and layout-before-passive ordering.
// `flushSync` drains renders, layout effects, and passive effects so the tests
// observe the settled result without awaiting the micro/macrotask queues.
class EffectSpec extends DomSuite:

  test("useEffect runs once after mount with empty deps"):
    val c = host()
    var runs = 0
    val Comp = view {
      useEffect(() => { runs += 1; noCleanup }, Array())
      div("x")
    }
    render(Comp(), c)
    Scheduler.flushSync()
    assert(runs == 1)

  test("empty-deps effect does not re-run on unrelated state changes"):
    val c = host()
    var runs = 0
    val Comp = view {
      val (n, _, update) = useState(0)
      useEffect(() => { runs += 1; noCleanup }, Array())
      button(onClick := (_ => update(_ + 1)), s"$n")
    }
    render(Comp(), c)
    Scheduler.flushSync()
    fireClick(c.querySelector("button"))
    fireClick(c.querySelector("button"))
    assert(c.querySelector("button").textContent == "2")
    assert(runs == 1)

  test("effect re-runs when a dep changes, cleaning up first"):
    val c = host()
    val log = ArrayBuffer.empty[String]
    val Comp = view {
      val (n, _, update) = useState(0)
      useEffect(() => {
        log += s"run$n"
        () => log += s"cleanup$n"
      }, Array(n))
      button(onClick := (_ => update(_ + 1)), s"$n")
    }
    render(Comp(), c)
    Scheduler.flushSync()
    fireClick(c.querySelector("button"))
    assert(log.toList == List("run0", "cleanup0", "run1"))

  test("null deps re-runs the effect on every render"):
    val c = host()
    var runs = 0
    val Comp = view {
      val (n, _, update) = useState(0)
      useEffect(() => { runs += 1; noCleanup }, null)
      button(onClick := (_ => update(_ + 1)), s"$n")
    }
    render(Comp(), c)
    Scheduler.flushSync()
    assert(runs == 1)
    fireClick(c.querySelector("button"))
    assert(runs == 2)
    fireClick(c.querySelector("button"))
    assert(runs == 3)

  test("cleanup runs on unmount"):
    val c = host()
    var cleaned = false
    val Comp = view {
      useEffect(() => () => cleaned = true, Array())
      div("x")
    }
    val root = createRoot(c)
    root.render(Comp())
    Scheduler.flushSync()
    assert(!cleaned)
    root.unmount()
    assert(cleaned)

  test("layout effects run before passive effects"):
    val c = host()
    val order = ArrayBuffer.empty[String]
    val Comp = view {
      useLayoutEffect(() => { order += "layout"; noCleanup }, Array())
      useEffect(() => { order += "passive"; noCleanup }, Array())
      div("x")
    }
    render(Comp(), c)
    Scheduler.flushSync()
    assert(order.toList == List("layout", "passive"))
