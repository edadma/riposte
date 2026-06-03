package io.github.edadma.riposte.query

import org.scalatest.funsuite.AnyFunSuite
import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future, Promise}

// The cache policy in isolation, with no DOM. A `QueryClient` is driven directly:
// `register` creates the cell, and subscribing to it through the client's store is
// exactly what mounting the cell does in a component, so these tests exercise the
// real fetch-on-observe / stale / dedup / gc paths without rendering.
//
// Time and timers are faked through `QueryEnv`: `now` is a manual clock and
// `schedule` queues a thunk that `fireTimers()` runs on demand, so gc and
// staleness are deterministic. Futures resolve on the parasitic EC, so a completed
// promise runs its callback synchronously — no waiting, no flushing.
class QueryClientSpec extends AnyFunSuite:

  private given ExecutionContext = ExecutionContext.parasitic

  private var clock   = 0.0
  private val timerQueue = mutable.ArrayBuffer.empty[() => Unit]

  // Install the fake clock and timer queue. Called at the top of each test so the
  // global `QueryEnv` seam is reset between them.
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

  // Register a query and observe its cell, returning the cell and an unsubscribe —
  // observing is what triggers fetch-on-first-observe.
  private def observe[A](client: QueryClient, key: QueryKey, fetcher: () => Future[A], opts: QueryOptions = QueryOptions()) =
    val cell  = client.register(key, fetcher, opts)
    val unsub = client.store.sub(cell, () => ())
    (cell, unsub)

  test("a query fetches on first observe and exposes the data"):
    install()
    val client     = new QueryClient()
    var calls      = 0
    val (cell, _)  = observe(client, queryKey("x"), () => { calls += 1; Future.successful(42) })
    assert(calls == 1)
    val st = client.store.get(cell)
    assert(st.data == Some(42))
    assert(st.status == QueryStatus.Success)
    assert(!st.isFetching)

  test("two registrations of the same key share one cell"):
    install()
    val client = new QueryClient()
    val a      = client.register(queryKey("x"), () => Future.successful(1), QueryOptions())
    val b      = client.register(queryKey("x"), () => Future.successful(1), QueryOptions())
    assert(a eq b)

  test("structurally equal keys are the same query"):
    install()
    val client = new QueryClient()
    val a      = client.register(queryKey("todos", 7), () => Future.successful(1), QueryOptions())
    val b      = client.register(queryKey("todos", 7), () => Future.successful(2), QueryOptions())
    assert(a eq b)

  test("a fetch in flight dedupes a concurrent refetch"):
    install()
    val client    = new QueryClient()
    val p         = Promise[Int]()
    var calls     = 0
    val (cell, _) = observe(client, queryKey("x"), () => { calls += 1; p.future })
    assert(calls == 1)
    assert(client.store.get(cell).isFetching)
    client.refetch(queryKey("x"))
    assert(calls == 1) // deduped against the in-flight promise
    p.success(7)
    assert(calls == 1)
    assert(client.store.get(cell).data == Some(7))
    assert(!client.store.get(cell).isFetching)

  test("a fresh result within staleTime is not refetched on re-observe"):
    install()
    val client     = new QueryClient()
    var calls      = 0
    val fetcher    = () => { calls += 1; Future.successful(1) }
    val opts       = QueryOptions(staleTime = 1000.0)
    val (_, unsub) = observe(client, queryKey("x"), fetcher, opts)
    assert(calls == 1)
    unsub() // last observer leaves; gc is scheduled but not fired
    observe(client, queryKey("x"), fetcher, opts) // re-observe with the clock unmoved
    assert(calls == 1) // still fresh — no refetch

  test("a result older than staleTime is refetched on re-observe"):
    install()
    val client     = new QueryClient()
    var calls      = 0
    val fetcher    = () => { calls += 1; Future.successful(1) }
    val opts       = QueryOptions(staleTime = 1000.0)
    val (_, unsub) = observe(client, queryKey("x"), fetcher, opts)
    assert(calls == 1)
    unsub()
    clock += 2000 // age the result past staleTime
    observe(client, queryKey("x"), fetcher, opts)
    assert(calls == 2)

  test("invalidate refetches an observed query"):
    install()
    val client    = new QueryClient()
    var calls     = 0
    val opts      = QueryOptions(staleTime = 1e9) // never auto-stale
    val (cell, _) = observe(client, queryKey("x"), () => { calls += 1; Future.successful(calls) }, opts)
    assert(calls == 1)
    assert(client.store.get(cell).data == Some(1))
    client.invalidate(queryKey("x"))
    assert(calls == 2)
    assert(client.store.get(cell).data == Some(2))

  test("invalidate of an unobserved query defers the refetch until re-observe"):
    install()
    val client     = new QueryClient()
    var calls      = 0
    val fetcher    = () => { calls += 1; Future.successful(calls) }
    val opts       = QueryOptions(staleTime = 1e9)
    val (_, unsub) = observe(client, queryKey("x"), fetcher, opts)
    assert(calls == 1)
    unsub()
    client.invalidate(queryKey("x")) // not observed — just marked stale
    assert(calls == 1)
    observe(client, queryKey("x"), fetcher, opts) // re-observe runs the deferred fetch
    assert(calls == 2)

  test("invalidatePrefix invalidates only matching keys"):
    install()
    val client    = new QueryClient()
    var todos1    = 0
    var todos2    = 0
    var users1    = 0
    val opts      = QueryOptions(staleTime = 1e9)
    observe(client, queryKey("todos", 1), () => { todos1 += 1; Future.successful(1) }, opts)
    observe(client, queryKey("todos", 2), () => { todos2 += 1; Future.successful(1) }, opts)
    observe(client, queryKey("users", 1), () => { users1 += 1; Future.successful(1) }, opts)
    assert((todos1, todos2, users1) == (1, 1, 1))
    client.invalidatePrefix(queryKey("todos"))
    assert((todos1, todos2, users1) == (2, 2, 1))

  test("setQueryData writes data without fetching, getQueryData reads it"):
    install()
    val client    = new QueryClient()
    var calls     = 0
    val opts      = QueryOptions(staleTime = 1e9)
    val (cell, _) = observe(client, queryKey("x"), () => { calls += 1; Future.successful(1) }, opts)
    assert(calls == 1)
    client.setQueryData(queryKey("x"), 99)
    assert(calls == 1) // no fetch
    assert(client.store.get(cell).data == Some(99))
    assert(client.getQueryData[Int](queryKey("x")) == Some(99))

  test("getQueryData is None for an unknown key"):
    install()
    val client = new QueryClient()
    assert(client.getQueryData[Int](queryKey("nope")) == None)

  test("a failed fetch sets Error status and the error"):
    install()
    val client    = new QueryClient()
    val boom      = new RuntimeException("boom")
    val (cell, _) = observe(client, queryKey("x"), () => Future.failed[Int](boom))
    val st        = client.store.get(cell)
    assert(st.status == QueryStatus.Error)
    assert(st.error.contains(boom))
    assert(!st.isFetching)
    assert(st.data == None)

  test("an unobserved query is evicted after gcTime, dropping its cell"):
    install()
    val client     = new QueryClient()
    val fetcher    = () => Future.successful(1)
    val (cell, unsub) = observe(client, queryKey("x"), fetcher)
    unsub()      // schedules gc
    fireTimers() // gc fires — evict
    val cell2 = client.register(queryKey("x"), fetcher, QueryOptions())
    assert(!(cell2 eq cell)) // a fresh cell, so the old entry was truly dropped

  test("re-observing before gcTime cancels eviction and keeps the cell"):
    install()
    val client        = new QueryClient()
    val fetcher       = () => Future.successful(1)
    val (cell, unsub) = observe(client, queryKey("x"), fetcher)
    unsub()                                          // schedules gc
    val (cell2, _) = observe(client, queryKey("x"), fetcher) // re-observe cancels it
    fireTimers()                                     // nothing timerQueue to fire
    assert(cell2 eq cell)
