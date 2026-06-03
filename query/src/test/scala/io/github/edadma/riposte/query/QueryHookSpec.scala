package io.github.edadma.riposte.query

import io.github.edadma.riposte.*
import org.scalajs.dom
import org.scalatest.funsuite.AnyFunSuite
import scala.concurrent.{ExecutionContext, Future}

// `useQuery` wired into real components under jsdom. Fetchers resolve on the
// parasitic EC, so a fetch settles synchronously within the layout effect that
// `useSyncExternalStore` uses to subscribe; `flushSync` then drains the re-render
// the settle schedules, so a mounted query shows its data without any waiting.
class QueryHookSpec extends AnyFunSuite:

  private given ExecutionContext = ExecutionContext.parasitic

  private def host(): dom.Element =
    val el = dom.document.createElement("div")
    dom.document.body.appendChild(el)
    el

  private def fireClick(el: dom.Element): Unit =
    el.dispatchEvent(new dom.Event("click"))
    Scheduler.flushSync()

  test("useQuery shows loading, then the fetched data"):
    val c      = host()
    val client = new QueryClient()
    val App = view {
      val q = useQuery(queryKey("greeting"), () => Future.successful("hi"))
      if q.isLoading then span(cls := "s", "loading")
      else span(cls := "s", q.data.getOrElse(""))
    }
    render(QueryClientProvider(client)(App()), c)
    Scheduler.flushSync()
    assert(c.querySelector("span.s").textContent == "hi")

  test("the result's refetch triggers a new fetch and updates the view"):
    val c      = host()
    var calls  = 0
    val client = new QueryClient()
    val App = view {
      val q = useQuery(queryKey("n"), () => { calls += 1; Future.successful(calls) }, QueryOptions(staleTime = 1e9))
      div(
        span(cls := "v", q.data.map(_.toString).getOrElse("-")),
        button(onClick := (_ => q.refetch()), "refetch"),
      )
    }
    render(QueryClientProvider(client)(App()), c)
    Scheduler.flushSync()
    assert(c.querySelector("span.v").textContent == "1")
    fireClick(c.querySelector("button"))
    assert(c.querySelector("span.v").textContent == "2")

  test("setQueryData on the client updates an observing component"):
    val c      = host()
    val client = new QueryClient()
    val App = view {
      val q = useQuery(queryKey("x"), () => Future.successful(1), QueryOptions(staleTime = 1e9))
      span(cls := "v", q.data.map(_.toString).getOrElse("-"))
    }
    render(QueryClientProvider(client)(App()), c)
    Scheduler.flushSync()
    assert(c.querySelector("span.v").textContent == "1")
    client.setQueryData(queryKey("x"), 99)
    Scheduler.flushSync()
    assert(c.querySelector("span.v").textContent == "99")

  test("two components reading the same key share one fetch"):
    val c      = host()
    var calls  = 0
    val client = new QueryClient()
    val Reader = view {
      val q = useQuery(queryKey("shared"), () => { calls += 1; Future.successful(7) })
      span(cls := "r", q.data.map(_.toString).getOrElse("-"))
    }
    render(QueryClientProvider(client)(div(Reader(), Reader())), c)
    Scheduler.flushSync()
    assert(calls == 1) // one shared cell, one fetch
    val spans = c.querySelectorAll("span.r")
    assert(spans.length == 2)
    assert((0 until spans.length).forall(i => spans(i).textContent == "7"))
