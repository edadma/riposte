package io.github.edadma.riposte.query

import io.github.edadma.riposte.*
import org.scalajs.dom
import org.scalatest.funsuite.AnyFunSuite
import scala.concurrent.{ExecutionContext, Future, Promise}

// `useMutation` driven through real components under jsdom. Mutation functions
// resolve on the parasitic EC, so a settle runs its callbacks synchronously and a
// single `flushSync` after a click drains the re-render the settle schedules.
class QueryMutationSpec extends AnyFunSuite:

  private given ExecutionContext = ExecutionContext.parasitic

  private def host(): dom.Element =
    val el = dom.document.createElement("div")
    dom.document.body.appendChild(el)
    el

  private def fireClick(el: dom.Element): Unit =
    el.dispatchEvent(new dom.Event("click"))
    Scheduler.flushSync()

  test("a mutation runs from idle to success and exposes its result"):
    val c      = host()
    val client = new QueryClient()
    val App = view {
      val m = useMutation[Int, Int]((v: Int) => Future.successful(v * 2))
      div(
        span(cls := "st", m.status.toString),
        span(cls := "d", m.data.map(_.toString).getOrElse("-")),
        button(onClick := (_ => m.mutate(21)), "go"),
      )
    }
    render(QueryClientProvider(client)(App()), c)
    Scheduler.flushSync()
    assert(c.querySelector("span.st").textContent == "Idle")
    fireClick(c.querySelector("button"))
    assert(c.querySelector("span.st").textContent == "Success")
    assert(c.querySelector("span.d").textContent == "42")

  test("a mutation is pending while its function is in flight"):
    val c      = host()
    val client = new QueryClient()
    val p      = Promise[Int]()
    val App = view {
      val m = useMutation[Int, Int]((_: Int) => p.future)
      div(span(cls := "st", m.status.toString), button(onClick := (_ => m.mutate(1)), "go"))
    }
    render(QueryClientProvider(client)(App()), c)
    Scheduler.flushSync()
    fireClick(c.querySelector("button"))
    assert(c.querySelector("span.st").textContent == "Pending")
    p.success(9)
    Scheduler.flushSync()
    assert(c.querySelector("span.st").textContent == "Success")

  test("a failed mutation exposes the error"):
    val c      = host()
    val client = new QueryClient()
    val boom   = new RuntimeException("boom")
    val App = view {
      val m = useMutation[Int, Int]((_: Int) => Future.failed[Int](boom))
      div(
        span(cls := "st", m.status.toString),
        span(cls := "e", m.error.map(_.getMessage).getOrElse("-")),
        button(onClick := (_ => m.mutate(1)), "go"),
      )
    }
    render(QueryClientProvider(client)(App()), c)
    Scheduler.flushSync()
    fireClick(c.querySelector("button"))
    assert(c.querySelector("span.st").textContent == "Error")
    assert(c.querySelector("span.e").textContent == "boom")

  test("an optimistic update is rolled back when the mutation fails"):
    val c      = host()
    val client = new QueryClient()
    client.setQueryData(queryKey("count"), 0) // seed the query the view reads
    val p = Promise[Int]()
    val App = view {
      val q        = useQuery(queryKey("count"), () => Future.successful(0), QueryOptions(staleTime = 1e9))
      val snapshot = useRef[Option[Int]](None)
      val m = useMutation[Int, Int](
        mutationFn = (_: Int) => p.future,
        onMutate = (_: Int) =>
          snapshot.current = client.getQueryData[Int](queryKey("count"))
          client.setQueryData[Int](queryKey("count"), _.getOrElse(0) + 1),
        onError = (_, _) => client.setQueryData(queryKey("count"), snapshot.current.getOrElse(0)),
      )
      div(span(cls := "v", q.data.map(_.toString).getOrElse("-")), button(onClick := (_ => m.mutate(1)), "go"))
    }
    render(QueryClientProvider(client)(App()), c)
    Scheduler.flushSync()
    assert(c.querySelector("span.v").textContent == "0")
    fireClick(c.querySelector("button")) // optimistic bump to 1
    assert(c.querySelector("span.v").textContent == "1")
    p.failure(new RuntimeException("nope"))
    Scheduler.flushSync()
    assert(c.querySelector("span.v").textContent == "0") // rolled back

  test("onSuccess can invalidate a query so it refetches the new value"):
    val c           = host()
    val client      = new QueryClient()
    var serverValue = 1
    val App = view {
      val q = useQuery(queryKey("v"), () => Future.successful(serverValue), QueryOptions(staleTime = 1e9))
      val m = useMutation[Unit, Unit](
        mutationFn = (_: Unit) => { serverValue = 2; Future.successful(()) },
        onSuccess = (_, _) => client.invalidate(queryKey("v")),
      )
      div(span(cls := "v", q.data.map(_.toString).getOrElse("-")), button(onClick := (_ => m.mutate(())), "go"))
    }
    render(QueryClientProvider(client)(App()), c)
    Scheduler.flushSync()
    assert(c.querySelector("span.v").textContent == "1")
    fireClick(c.querySelector("button"))
    assert(c.querySelector("span.v").textContent == "2")

  test("mutateAsync resolves with the mutation result"):
    val c      = host()
    val client = new QueryClient()
    var got    = 0
    val App = view {
      val m = useMutation[Int, Int]((v: Int) => Future.successful(v + 100))
      button(onClick := (_ => m.mutateAsync(5).foreach(d => got = d)), "go")
    }
    render(QueryClientProvider(client)(App()), c)
    Scheduler.flushSync()
    fireClick(c.querySelector("button"))
    assert(got == 105)

  test("reset returns a settled mutation to idle"):
    val c      = host()
    val client = new QueryClient()
    val App = view {
      val m = useMutation[Int, Int]((v: Int) => Future.successful(v))
      div(
        span(cls := "st", m.status.toString),
        button(cls := "go", onClick := (_ => m.mutate(1)), "go"),
        button(cls := "rs", onClick := (_ => m.reset()), "reset"),
      )
    }
    render(QueryClientProvider(client)(App()), c)
    Scheduler.flushSync()
    fireClick(c.querySelector("button.go"))
    assert(c.querySelector("span.st").textContent == "Success")
    fireClick(c.querySelector("button.rs"))
    assert(c.querySelector("span.st").textContent == "Idle")
