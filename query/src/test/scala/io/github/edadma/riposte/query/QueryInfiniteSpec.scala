package io.github.edadma.riposte.query

import org.scalatest.funsuite.AnyFunSuite
import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future, Promise}

// Infinite queries driven against the client with no DOM, the same harness as
// `QueryClientSpec`: subscribing to a cell is what mounting it does in a component,
// time and timers are faked through `QueryEnv`, and futures settle synchronously on
// the parasitic EC.
class QueryInfiniteSpec extends AnyFunSuite:

  private given ExecutionContext = ExecutionContext.parasitic

  private var clock      = 0.0
  private val timerQueue = mutable.ArrayBuffer.empty[() => Unit]

  private def install(): Unit =
    clock = 1000.0
    timerQueue.clear()
    QueryEnv.now = () => clock
    QueryEnv.schedule = (fn, _) =>
      timerQueue += fn
      () => timerQueue -= fn

  private def fireTimers(): Unit =
    val fns = timerQueue.toVector
    timerQueue.clear()
    fns.foreach(_())

  private def observeInfinite[D, P](
      client:               QueryClient,
      key:                  QueryKey,
      fetchPage:            P => Future[D],
      initialPageParam:     P,
      getNextPageParam:     (D, Vector[D]) => Option[P],
      getPreviousPageParam: (D, Vector[D]) => Option[P] = (_: D, _: Vector[D]) => None,
      opts:                 QueryOptions = QueryOptions(),
  ) =
    val cell  = client.registerInfinite(key, fetchPage, initialPageParam, getNextPageParam, getPreviousPageParam, opts)
    val unsub = client.store.sub(cell, () => ())
    (cell, unsub)

  private def infiniteData[D, P](client: QueryClient, cell: io.github.edadma.riposte.atoms.PrimitiveAtom[QueryState[Any]]) =
    client.store.get(cell).data.get.asInstanceOf[InfiniteData[D, P]]

  test("an infinite query loads its first page on observe"):
    install()
    val client = new QueryClient()
    val (cell, _) = observeInfinite(
      client,
      queryKey("feed"),
      (p: Int) => Future.successful(s"page$p"),
      initialPageParam = 0,
      getNextPageParam = (_, all) => if all.size < 3 then Some(all.size) else None,
    )
    val d = infiniteData[String, Int](client, cell)
    assert(d.pages == Vector("page0"))
    assert(d.pageParams == Vector(0))

  test("fetchNextPage appends the next page"):
    install()
    val client = new QueryClient()
    val (cell, _) = observeInfinite(
      client,
      queryKey("feed"),
      (p: Int) => Future.successful(s"page$p"),
      initialPageParam = 0,
      getNextPageParam = (_, all) => if all.size < 3 then Some(all.size) else None,
    )
    client.fetchNextPage(queryKey("feed"))
    val d = infiniteData[String, Int](client, cell)
    assert(d.pages == Vector("page0", "page1"))
    assert(d.pageParams == Vector(0, 1))

  test("fetchNextPage stops once getNextPageParam returns None"):
    install()
    val client = new QueryClient()
    val key    = queryKey("feed")
    val (cell, _) = observeInfinite(
      client,
      key,
      (p: Int) => Future.successful(s"page$p"),
      initialPageParam = 0,
      getNextPageParam = (_, all) => if all.size < 3 then Some(all.size) else None,
    )
    client.fetchNextPage(key) // page1
    client.fetchNextPage(key) // page2
    client.fetchNextPage(key) // size 3 → None, a no-op
    val d = infiniteData[String, Int](client, cell)
    assert(d.pages == Vector("page0", "page1", "page2"))

  test("refetch reloads every page currently held"):
    install()
    val client  = new QueryClient()
    val key     = queryKey("feed")
    var version = 0
    val (cell, _) = observeInfinite(
      client,
      key,
      (p: Int) => Future.successful(s"p$p-v$version"),
      initialPageParam = 0,
      getNextPageParam = (_, all) => if all.size < 2 then Some(all.size) else None,
    )
    client.fetchNextPage(key) // now holds pages [0, 1] at v0
    assert(infiniteData[String, Int](client, cell).pages == Vector("p0-v0", "p1-v0"))
    version = 1
    client.invalidate(key) // observed → reload all loaded pages at v1
    val d = infiniteData[String, Int](client, cell)
    assert(d.pages == Vector("p0-v1", "p1-v1"))
    assert(d.pageParams == Vector(0, 1))

  test("a page fetch in flight dedupes a second fetchNextPage"):
    install()
    val client = new QueryClient()
    val key    = queryKey("feed")
    val p      = Promise[String]()
    var calls  = 0
    val (cell, _) = observeInfinite(
      client,
      key,
      (param: Int) => { calls += 1; if param == 0 then Future.successful("page0") else p.future },
      initialPageParam = 0,
      getNextPageParam = (_, all) => if all.size < 3 then Some(all.size) else None,
    )
    assert(calls == 1)
    client.fetchNextPage(key) // starts the page-1 fetch (pending)
    client.fetchNextPage(key) // in flight → no-op
    assert(calls == 2)
    p.success("page1")
    val d = infiniteData[String, Int](client, cell)
    assert(d.pages == Vector("page0", "page1"))

  test("a failed page fetch is retried"):
    install()
    val client = new QueryClient()
    val key    = queryKey("feed")
    val boom   = new RuntimeException("boom")
    var n      = 0
    val (cell, _) = observeInfinite(
      client,
      key,
      (param: Int) =>
        if param == 0 then Future.successful("page0")
        else { n += 1; if n <= 1 then Future.failed[String](boom) else Future.successful("page1") },
      initialPageParam = 0,
      getNextPageParam = (_, all) => if all.size < 3 then Some(all.size) else None,
      opts = QueryOptions(retry = 1),
    )
    client.fetchNextPage(key) // attempt 0 fails → schedules a retry
    assert(n == 1)
    assert(client.store.get(cell).isFetching)
    fireTimers() // retry succeeds
    assert(n == 2)
    assert(infiniteData[String, Int](client, cell).pages == Vector("page0", "page1"))

  // A bidirectional query opens at a param in the middle of the range and can grow
  // from both ends. Pages here are their own param (an Int window index): the next
  // param is one higher, the previous one lower, each bounded.

  test("fetchPreviousPage prepends an older page"):
    install()
    val client = new QueryClient()
    val key    = queryKey("win")
    val (cell, _) = observeInfinite[Int, Int](
      client,
      key,
      (p: Int) => Future.successful(p),
      initialPageParam = 5,
      getNextPageParam = (last, _) => if last < 10 then Some(last + 1) else None,
      getPreviousPageParam = (first, _) => if first > 0 then Some(first - 1) else None,
    )
    assert(infiniteData[Int, Int](client, cell).pages == Vector(5))
    client.fetchPreviousPage(key)
    val d = infiniteData[Int, Int](client, cell)
    assert(d.pages == Vector(4, 5))
    assert(d.pageParams == Vector(4, 5))

  test("a bidirectional query can grow from both ends"):
    install()
    val client = new QueryClient()
    val key    = queryKey("win")
    val (cell, _) = observeInfinite[Int, Int](
      client,
      key,
      (p: Int) => Future.successful(p),
      initialPageParam = 5,
      getNextPageParam = (last, _) => if last < 10 then Some(last + 1) else None,
      getPreviousPageParam = (first, _) => if first > 0 then Some(first - 1) else None,
    )
    client.fetchPreviousPage(key) // [4, 5]
    client.fetchPreviousPage(key) // [3, 4, 5]
    client.fetchNextPage(key)     // [3, 4, 5, 6]
    val d = infiniteData[Int, Int](client, cell)
    assert(d.pages == Vector(3, 4, 5, 6))
    assert(d.pageParams == Vector(3, 4, 5, 6))

  test("fetchPreviousPage stops when getPreviousPageParam returns None"):
    install()
    val client = new QueryClient()
    val key    = queryKey("win")
    val (cell, _) = observeInfinite[Int, Int](
      client,
      key,
      (p: Int) => Future.successful(p),
      initialPageParam = 1,
      getNextPageParam = (_, _) => None,
      getPreviousPageParam = (first, _) => if first > 0 then Some(first - 1) else None,
    )
    client.fetchPreviousPage(key) // first 1 → prepend 0 → [0, 1]
    client.fetchPreviousPage(key) // first 0 → None, a no-op
    assert(infiniteData[Int, Int](client, cell).pages == Vector(0, 1))

  test("a forward-only query has no previous direction"):
    install()
    val client = new QueryClient()
    val key    = queryKey("feed")
    val (cell, _) = observeInfinite[String, Int](
      client,
      key,
      (p: Int) => Future.successful(s"page$p"),
      initialPageParam = 0,
      getNextPageParam = (_, all) => if all.size < 3 then Some(all.size) else None,
    ) // getPreviousPageParam defaults to None
    client.fetchPreviousPage(key) // no-op — no previous param
    assert(infiniteData[String, Int](client, cell).pages == Vector("page0"))
