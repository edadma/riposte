package io.github.edadma.riposte.query

import io.github.edadma.riposte.*
import org.scalajs.dom
import org.scalatest.funsuite.AnyFunSuite
import scala.concurrent.{ExecutionContext, Future, Promise}

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

  test("a component reads a pre-seeded query with no loading flash"):
    val c      = host()
    val client = new QueryClient()
    client.setQueryData(queryKey("seeded"), "hello") // primed before any render
    val App = view {
      val q = useQuery(queryKey("seeded"), () => Future.successful("fetched"), QueryOptions(staleTime = 1e9))
      span(cls := "s", if q.isLoading then "loading" else q.data.getOrElse(""))
    }
    render(QueryClientProvider(client)(App()), c)
    Scheduler.flushSync()
    assert(c.querySelector("span.s").textContent == "hello")

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

  test("useInfiniteQuery loads the first page and fetchNextPage appends"):
    val c      = host()
    val client = new QueryClient()
    val App = view {
      val q = useInfiniteQuery[String, Int](
        queryKey("feed"),
        (p: Int) => Future.successful(s"page$p"),
        initialPageParam = 0,
        getNextPageParam = (_, all) => if all.size < 2 then Some(all.size) else None,
        options = QueryOptions(staleTime = 1e9),
      )
      div(
        span(cls := "pages", q.pages.mkString(",")),
        span(cls := "more", q.hasNextPage.toString),
        button(onClick := (_ => q.fetchNextPage()), "more"),
      )
    }
    render(QueryClientProvider(client)(App()), c)
    Scheduler.flushSync()
    assert(c.querySelector("span.pages").textContent == "page0")
    assert(c.querySelector("span.more").textContent == "true")
    fireClick(c.querySelector("button"))
    assert(c.querySelector("span.pages").textContent == "page0,page1")
    assert(c.querySelector("span.more").textContent == "false")

  test("useInfiniteQuery shows loading until the first page resolves"):
    val c      = host()
    val client = new QueryClient()
    val p      = Promise[String]()
    val App = view {
      val q = useInfiniteQuery[String, Int](
        queryKey("feed"),
        (_: Int) => p.future,
        initialPageParam = 0,
        getNextPageParam = (_, _) => None,
        options = QueryOptions(staleTime = 1e9),
      )
      span(cls := "s", if q.isLoading then "loading" else q.pages.mkString(","))
    }
    render(QueryClientProvider(client)(App()), c)
    Scheduler.flushSync()
    assert(c.querySelector("span.s").textContent == "loading")
    p.success("page0")
    Scheduler.flushSync()
    assert(c.querySelector("span.s").textContent == "page0")

  test("useInfiniteQuery surfaces a page-fetch error"):
    val c      = host()
    val client = new QueryClient()
    val boom   = new RuntimeException("kaboom")
    val App = view {
      val q = useInfiniteQuery[String, Int](
        queryKey("feed"),
        (_: Int) => Future.failed[String](boom),
        initialPageParam = 0,
        getNextPageParam = (_, _) => None,
        options = QueryOptions(staleTime = 1e9),
      )
      span(cls := "s", if q.isError then q.error.map(_.getMessage).getOrElse("?") else "ok")
    }
    render(QueryClientProvider(client)(App()), c)
    Scheduler.flushSync()
    assert(c.querySelector("span.s").textContent == "kaboom")

  test("useInfiniteQuery refetch reloads its loaded pages"):
    val c       = host()
    val client  = new QueryClient()
    var version = 0
    val App = view {
      val q = useInfiniteQuery[String, Int](
        queryKey("feed"),
        (pp: Int) => Future.successful(s"p$pp-v$version"),
        initialPageParam = 0,
        getNextPageParam = (_, _) => None,
        options = QueryOptions(staleTime = 1e9),
      )
      div(span(cls := "v", q.pages.mkString(",")), button(onClick := (_ => q.refetch()), "r"))
    }
    render(QueryClientProvider(client)(App()), c)
    Scheduler.flushSync()
    assert(c.querySelector("span.v").textContent == "p0-v0")
    version = 1
    fireClick(c.querySelector("button"))
    assert(c.querySelector("span.v").textContent == "p0-v1")

  test("a background refetch keeps the previous data visible while it is in flight"):
    val c      = host()
    val client = new QueryClient()
    val p      = Promise[Int]()
    var first  = true
    val App = view {
      val q = useQuery(
        queryKey("n"),
        () => if first then { first = false; Future.successful(1) } else p.future,
        QueryOptions(staleTime = 1e9),
      )
      div(
        span(cls := "v", q.data.map(_.toString).getOrElse("-")),
        span(cls := "f", q.isFetching.toString),
        button(onClick := (_ => q.refetch()), "r"),
      )
    }
    render(QueryClientProvider(client)(App()), c)
    Scheduler.flushSync()
    assert(c.querySelector("span.v").textContent == "1")
    assert(c.querySelector("span.f").textContent == "false")
    fireClick(c.querySelector("button")) // refetch is in flight on the unresolved promise
    assert(c.querySelector("span.v").textContent == "1")    // previous data kept
    assert(c.querySelector("span.f").textContent == "true") // but marked fetching
    p.success(2)
    Scheduler.flushSync()
    assert(c.querySelector("span.v").textContent == "2")
    assert(c.querySelector("span.f").textContent == "false")

  test("useInfiniteQuery grows from the front and tracks hasPreviousPage"):
    val c      = host()
    val client = new QueryClient()
    val App = view {
      val q = useInfiniteQuery[Int, Int](
        queryKey("win"),
        (p: Int) => Future.successful(p),
        initialPageParam = 1,
        getNextPageParam = (last, _) => if last < 3 then Some(last + 1) else None,
        getPreviousPageParam = (first, _) => if first > 0 then Some(first - 1) else None,
        options = QueryOptions(staleTime = 1e9),
      )
      div(
        span(cls := "pages", q.pages.mkString(",")),
        span(cls := "prev", q.hasPreviousPage.toString),
        button(onClick := (_ => q.fetchPreviousPage()), "prev"),
      )
    }
    render(QueryClientProvider(client)(App()), c)
    Scheduler.flushSync()
    assert(c.querySelector("span.pages").textContent == "1")
    assert(c.querySelector("span.prev").textContent == "true")
    fireClick(c.querySelector("button")) // prepend the older page 0
    assert(c.querySelector("span.pages").textContent == "0,1")
    assert(c.querySelector("span.prev").textContent == "false") // first is 0 → no previous
