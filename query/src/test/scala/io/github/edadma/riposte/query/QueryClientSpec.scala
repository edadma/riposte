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

  private var clock      = 0.0
  private val timerQueue = mutable.ArrayBuffer.empty[() => Unit]
  private val focusCbs   = mutable.ArrayBuffer.empty[() => Unit]
  private val onlineCbs  = mutable.ArrayBuffer.empty[() => Unit]

  // Install the fake clock, timer queue, and focus/online seams. Called at the top
  // of each test so the global `QueryEnv` seam is reset between them — the focus
  // and online stubs just capture the client's handler so a test can fire it.
  private def install(): Unit =
    clock = 1000.0
    timerQueue.clear()
    focusCbs.clear()
    onlineCbs.clear()
    QueryEnv.now = () => clock
    QueryEnv.schedule = (fn, _) =>
      timerQueue += fn
      () => timerQueue -= fn
    QueryEnv.subscribeFocus = cb =>
      focusCbs += cb
      () => focusCbs -= cb
    QueryEnv.subscribeOnline = cb =>
      onlineCbs += cb
      () => onlineCbs -= cb

  private def fireTimers(): Unit =
    val fns = timerQueue.toVector
    timerQueue.clear()
    fns.foreach(_())

  private def fireFocus(): Unit  = focusCbs.toVector.foreach(_())
  private def fireOnline(): Unit = onlineCbs.toVector.foreach(_())

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

  test("setQueryData seeds a key no query has observed"):
    install()
    val client = new QueryClient()
    client.setQueryData(queryKey("x"), 5)
    assert(client.getQueryData[Int](queryKey("x")) == Some(5))

  test("setQueryData's updater form sees the previous value"):
    install()
    val client = new QueryClient()
    client.setQueryData(queryKey("x"), 1)
    client.setQueryData[Int](queryKey("x"), _.getOrElse(0) + 10)
    assert(client.getQueryData[Int](queryKey("x")) == Some(11))

  test("the updater sees None for a never-seen key"):
    install()
    val client = new QueryClient()
    client.setQueryData[Int](queryKey("x"), _.getOrElse(0) + 1)
    assert(client.getQueryData[Int](queryKey("x")) == Some(1))

  test("a seeded key with no observer is evicted after gcTime"):
    install()
    val client = new QueryClient()
    client.setQueryData(queryKey("x"), 5)
    fireTimers() // the seed's gc countdown fires
    assert(client.getQueryData[Int](queryKey("x")) == None)

  test("a useQuery adopts a seeded entry, keeping its data and gaining a fetcher"):
    install()
    val client    = new QueryClient()
    var calls     = 0
    val opts      = QueryOptions(staleTime = 1e9) // the fresh seed is not stale
    client.setQueryData(queryKey("x"), 5)
    val (cell, _) = observe(client, queryKey("x"), () => { calls += 1; Future.successful(99) }, opts)
    assert(client.store.get(cell).data == Some(5)) // kept the seed
    assert(calls == 0)                             // fresh, so not refetched on observe
    client.refetch(queryKey("x"))                  // the adopted fetcher now drives a refetch
    assert(calls == 1)
    assert(client.store.get(cell).data == Some(99))

  test("prefetchQuery loads data into the cache with no observer"):
    install()
    val client = new QueryClient()
    var calls  = 0
    client.prefetchQuery(queryKey("x"), () => { calls += 1; Future.successful(7) })
    assert(calls == 1)
    assert(client.getQueryData[Int](queryKey("x")) == Some(7))

  test("an unadopted prefetch is evicted after gcTime"):
    install()
    val client = new QueryClient()
    client.prefetchQuery(queryKey("x"), () => Future.successful(7))
    fireTimers()
    assert(client.getQueryData[Int](queryKey("x")) == None)

  test("observing a prefetched query reuses its fresh data without refetching"):
    install()
    val client    = new QueryClient()
    var calls     = 0
    val fetcher   = () => { calls += 1; Future.successful(7) }
    val opts      = QueryOptions(staleTime = 1e9)
    client.prefetchQuery(queryKey("x"), fetcher, opts)
    assert(calls == 1)
    val (cell, _) = observe(client, queryKey("x"), fetcher, opts)
    assert(calls == 1) // still fresh — no refetch
    assert(client.store.get(cell).data == Some(7))

  test("a fetch that fails is retried and can ultimately succeed"):
    install()
    val client    = new QueryClient()
    val boom      = new RuntimeException("boom")
    var n         = 0
    val fetcher   = () => { n += 1; if n <= 2 then Future.failed[Int](boom) else Future.successful(42) }
    val (cell, _) = observe(client, queryKey("x"), fetcher, QueryOptions(retry = 2))
    assert(n == 1)
    assert(client.store.get(cell).isFetching)              // retrying, not settled
    assert(client.store.get(cell).status == QueryStatus.Pending)
    fireTimers()                                           // second attempt — fails again
    assert(n == 2)
    assert(client.store.get(cell).status == QueryStatus.Pending)
    fireTimers()                                           // third attempt — succeeds
    assert(n == 3)
    assert(client.store.get(cell).data == Some(42))
    assert(!client.store.get(cell).isFetching)

  test("a fetch that keeps failing surfaces the error once retries are exhausted"):
    install()
    val client    = new QueryClient()
    val boom      = new RuntimeException("boom")
    var n         = 0
    val fetcher   = () => { n += 1; Future.failed[Int](boom) }
    val (cell, _) = observe(client, queryKey("x"), fetcher, QueryOptions(retry = 1))
    assert(n == 1)
    assert(client.store.get(cell).status == QueryStatus.Pending) // still retrying
    fireTimers()                                                 // retry — fails, exhausted
    assert(n == 2)
    assert(client.store.get(cell).status == QueryStatus.Error)
    assert(client.store.get(cell).error.contains(boom))
    assert(!client.store.get(cell).isFetching)

  test("retryDelay is consulted with each just-failed attempt index"):
    install()
    val client  = new QueryClient()
    val boom    = new RuntimeException("boom")
    val delays  = mutable.ArrayBuffer.empty[Int]
    val fetcher = () => Future.failed[Int](boom)
    val opts    = QueryOptions(retry = 2, retryDelay = a => { delays += a; 5.0 })
    observe(client, queryKey("x"), fetcher, opts) // attempt 0 fails → retryDelay(0)
    fireTimers()                                  // attempt 1 fails → retryDelay(1)
    fireTimers()                                  // attempt 2 fails → exhausted, no delay asked
    assert(delays.toVector == Vector(0, 1))

  test("window focus refetches an observed stale query"):
    install()
    val client    = new QueryClient()
    var calls     = 0
    val opts      = QueryOptions(staleTime = 1000.0)
    val (cell, _) = observe(client, queryKey("x"), () => { calls += 1; Future.successful(calls) }, opts)
    assert(calls == 1)
    clock += 2000 // age it past staleTime
    fireFocus()
    assert(calls == 2)
    assert(client.store.get(cell).data == Some(2))

  test("window focus leaves a fresh query alone"):
    install()
    val client = new QueryClient()
    var calls  = 0
    observe(client, queryKey("x"), () => { calls += 1; Future.successful(1) }, QueryOptions(staleTime = 1e9))
    assert(calls == 1)
    fireFocus()
    assert(calls == 1) // still fresh

  test("window focus is ignored when refetchOnWindowFocus is off"):
    install()
    val client = new QueryClient()
    var calls  = 0
    val opts   = QueryOptions(staleTime = 1000.0, refetchOnWindowFocus = false)
    observe(client, queryKey("x"), () => { calls += 1; Future.successful(1) }, opts)
    assert(calls == 1)
    clock += 2000
    fireFocus()
    assert(calls == 1) // opted out

  test("window focus does not refetch an unobserved query"):
    install()
    val client     = new QueryClient()
    var calls      = 0
    val opts       = QueryOptions(staleTime = 1000.0)
    val (_, unsub) = observe(client, queryKey("x"), () => { calls += 1; Future.successful(1) }, opts)
    assert(calls == 1)
    unsub()       // no longer active
    clock += 2000
    fireFocus()
    assert(calls == 1) // nothing is watching it

  test("network reconnect refetches an observed stale query"):
    install()
    val client = new QueryClient()
    var calls  = 0
    observe(client, queryKey("x"), () => { calls += 1; Future.successful(1) }, QueryOptions(staleTime = 1000.0))
    assert(calls == 1)
    clock += 2000
    fireOnline()
    assert(calls == 2)

  test("a disabled query does not fetch on observe"):
    install()
    val client = new QueryClient()
    var calls  = 0
    observe(client, queryKey("x"), () => { calls += 1; Future.successful(1) }, QueryOptions(enabled = false))
    assert(calls == 0)

  test("enabling a disabled query fetches it"):
    install()
    val client  = new QueryClient()
    var calls   = 0
    val fetcher = () => { calls += 1; Future.successful(1) }
    observe(client, queryKey("x"), fetcher, QueryOptions(enabled = false))
    assert(calls == 0)
    client.register(queryKey("x"), fetcher, QueryOptions(enabled = true)) // a re-render that turns it on
    assert(calls == 1)

  test("a disabled query ignores window focus"):
    install()
    val client = new QueryClient()
    var calls  = 0
    val opts   = QueryOptions(staleTime = 1000.0, enabled = false)
    observe(client, queryKey("x"), () => { calls += 1; Future.successful(1) }, opts)
    clock += 2000
    fireFocus()
    assert(calls == 0)

  test("refetchInterval polls an observed query even while it is fresh"):
    install()
    val client = new QueryClient()
    var calls  = 0
    val opts   = QueryOptions(staleTime = 1e9, refetchInterval = Some(1000.0))
    observe(client, queryKey("x"), () => { calls += 1; Future.successful(calls) }, opts)
    assert(calls == 1) // initial fetch
    fireTimers()       // one poll tick — refetches despite staleTime not elapsing
    assert(calls == 2)
    fireTimers()
    assert(calls == 3)

  test("polling stops when the last observer leaves"):
    install()
    val client     = new QueryClient()
    var calls      = 0
    val opts       = QueryOptions(staleTime = 1e9, refetchInterval = Some(1000.0))
    val (_, unsub) = observe(client, queryKey("x"), () => { calls += 1; Future.successful(1) }, opts)
    assert(calls == 1)
    unsub()      // cancels the poll and schedules gc
    fireTimers() // only gc fires
    assert(calls == 1)

  test("a disabled query is not polled"):
    install()
    val client = new QueryClient()
    var calls  = 0
    val opts   = QueryOptions(enabled = false, refetchInterval = Some(1000.0))
    observe(client, queryKey("x"), () => { calls += 1; Future.successful(1) }, opts)
    fireTimers()
    assert(calls == 0)
