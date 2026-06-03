package io.github.edadma.riposte.query

import io.github.edadma.riposte.atoms.*
import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

// The cache. A `QueryClient` owns a reactive `Store` and a registry of live
// queries keyed by `QueryKey`. Each query is one primitive atom — the cell —
// holding a `QueryState`; components subscribe to a cell through `useQuery`, so a
// change to a query re-renders exactly the components observing it.
//
// The client is the policy layer over the atoms substrate. Atoms give it sharing
// (one cell per key), observation (a cell's `onMount` fires on first observe and
// its cleanup on last leave), and eviction (`Store.forget`). The client adds the
// query semantics atoms deliberately don't know about: when a result is stale,
// when to refetch, how to dedupe an in-flight fetch, and when to garbage-collect a
// query nothing is watching.
final class QueryClient(val store: Store = new Store)(using ec: ExecutionContext):

  // One live query. The cell holds the observable `QueryState`; the rest is the
  // bookkeeping the client needs to drive fetching and gc, kept off the cell
  // because components don't observe it.
  private final class Entry(
      val cell:       PrimitiveAtom[QueryState[Any]],
      var fetcher:    Option[() => Future[Any]],
      var staleTime:            Double,
      var gcTime:               Double,
      var retry:                Int,
      var retryDelay:           Int => Double,
      var refetchOnWindowFocus: Boolean,
      var refetchOnReconnect:   Boolean,
  ):
    // The in-flight fetch, if any — its presence dedupes concurrent fetches of the
    // same key onto one promise.
    var promise: Option[Future[Any]] = None

    // The pending gc cancel, set while this query is unobserved and counting down
    // to eviction; cleared (and cancelled) when it is observed again.
    var gcCancel: Option[() => Unit] = None

    // Whether a component is currently observing the cell. Tracked from the cell's
    // mount lifecycle so `invalidate` can refetch only queries someone is watching.
    var active: Boolean = false

    // Present only for infinite queries. Carries the erased page machinery the
    // imperative paths (`fetchNextPage`, refetch-all) need, since the cell itself
    // has lost the page types behind `QueryState[Any]`.
    var infinite: Option[InfiniteSpec] = None

  // The erased page machinery for an infinite query, captured at registration. The
  // cell stores a `QueryState[Any]` whose data is an `InfiniteData[Any, Any]`, so
  // fetching a page and choosing the next param go through these type-erased
  // closures rather than the original `D`/`P`.
  private final class InfiniteSpec(
      val fetchPage:        Any => Future[Any],
      val initialPageParam: Any,
      val getNextPageParam: (Any, Vector[Any]) => Option[Any],
  )

  private val entries = mutable.Map.empty[QueryKey, Entry]

  // The execution context queries settle on, shared with `useMutation` so a
  // mutation's callbacks run on the same context as the cache writes they trigger.
  private[query] def executionContext: ExecutionContext = ec

  // Window focus / network reconnect are subscribed lazily — once the first query
  // is observed — so a client that is never used (or runs without a window) never
  // touches the environment. Subscribed once and kept for the client's lifetime.
  private var focusSub:  Option[() => Unit] = None
  private var onlineSub: Option[() => Unit] = None

  private def ensureGlobalListeners(): Unit =
    if focusSub.isEmpty then
      focusSub = Some(QueryEnv.subscribeFocus(() => refetchActiveStale(_.refetchOnWindowFocus)))
    if onlineSub.isEmpty then
      onlineSub = Some(QueryEnv.subscribeOnline(() => refetchActiveStale(_.refetchOnReconnect)))

  // Refetch every query that a component is observing, whose data is stale, that
  // opts into this trigger, and that has no fetch already in flight. The shape of
  // the focus and reconnect handlers — they differ only in which option gates them.
  private def refetchActiveStale(wants: Entry => Boolean): Unit =
    entries.foreach { (key, e) =>
      if e.active && wants(e) && e.promise.isEmpty && e.fetcher.isDefined && isStale(e) then doFetch(key, e)
    }

  // Get-or-create the cell for `key`, refreshing the fetcher and options to the
  // latest call's values so a re-render with a new closure or new `staleTime`
  // takes effect. Called from `useQuery` on every render; the lifecycle is wired
  // once, when the entry is first created.
  private[query] def register(key: QueryKey, fetcher: () => Future[Any], opts: QueryOptions): PrimitiveAtom[QueryState[Any]] =
    val e = entries.getOrElseUpdate(key, createEntry(key, Some(fetcher), opts))
    e.fetcher              = Some(fetcher)
    e.staleTime            = opts.staleTime
    e.gcTime               = opts.gcTime
    e.retry                = opts.retry
    e.retryDelay           = opts.retryDelay
    e.refetchOnWindowFocus = opts.refetchOnWindowFocus
    e.refetchOnReconnect   = opts.refetchOnReconnect
    e.cell

  private def createEntry(key: QueryKey, fetcher: Option[() => Future[Any]], opts: QueryOptions): Entry =
    val cell = atom(QueryState.initial[Any])
    val e =
      new Entry(cell, fetcher, opts.staleTime, opts.gcTime, opts.retry, opts.retryDelay,
        opts.refetchOnWindowFocus, opts.refetchOnReconnect)
    // First observer: cancel any pending eviction and fetch if the data is stale.
    // Last observer: start the gc countdown. The store fires this on the cell's
    // listener set going empty→nonempty and the cleanup on nonempty→empty.
    onMount(cell) { _ =>
      ensureGlobalListeners()
      e.active = true
      e.gcCancel.foreach(_())
      e.gcCancel = None
      fetchIfStale(key)
      Some { () =>
        e.active = false
        scheduleGc(key, e)
      }
    }
    e

  // Create an entry that no component is observing and start its gc countdown
  // immediately — for cache contents that arrive without a `useQuery` mounting
  // them (`setQueryData` seeds, `prefetchQuery` warms). If a component later
  // observes the cell its `onMount` cancels the countdown.
  private def createUnobserved(key: QueryKey, fetcher: Option[() => Future[Any]], opts: QueryOptions): Entry =
    val e = createEntry(key, fetcher, opts)
    scheduleGc(key, e)
    e

  private def seedEntry(key: QueryKey): Entry =
    entries.getOrElseUpdate(key, createUnobserved(key, None, QueryOptions()))

  // True if the cell has never settled or its result has aged past `staleTime`.
  private def isStale(e: Entry): Boolean =
    val st = store.get(e.cell)
    st.updatedAt == 0.0 || (QueryEnv.now() - st.updatedAt) >= e.staleTime

  // Fetch only if the data is stale and no fetch is already in flight —
  // stale-while-revalidate plus in-flight dedup.
  private def fetchIfStale(key: QueryKey): Unit =
    entries.get(key).foreach { e =>
      if isStale(e) && e.promise.isEmpty && e.fetcher.isDefined then doFetch(key, e)
    }

  // Run the fetcher, marking the cell fetching, and fold the settled result back
  // into the cell. A failed attempt is retried up to `retry` times, waiting
  // `retryDelay(attempt)` between tries; `isFetching` stays true and the error
  // only surfaces once the retries are exhausted. A result (or final error) that
  // arrives after the entry was evicted or replaced is dropped — `_ eq e` confirms
  // this is still the same live entry — so a resurrected zombie cell can never be
  // written. A no-op if the entry has no fetcher (a key seeded by `setQueryData`
  // with no query observing it yet).
  private def doFetch(key: QueryKey, e: Entry): Unit =
    e.fetcher.foreach(fetch => startFetch(key, e, fetch))

  // Begin a fetch from an explicit thunk: mark the cell fetching and enter the
  // retry loop. The steady path passes `e.fetcher`; `fetchNextPage` passes a
  // one-shot thunk resolving to the page-appended `InfiniteData`, so both share
  // retry, in-flight dedup, and the zombie-cell guard.
  private def startFetch(key: QueryKey, e: Entry, fetch: () => Future[Any]): Unit =
    store.set(e.cell, store.get(e.cell).copy(isFetching = true))
    runAttempt(key, e, fetch, 0)

  private def runAttempt(key: QueryKey, e: Entry, fetch: () => Future[Any], attempt: Int): Unit =
    val f = fetch()
    e.promise = Some(f) // stays Some across retries, so the fetch reads as in-flight
    f.onComplete { res =>
      if entries.get(key).exists(_ eq e) then
        res match
          case Success(v) =>
            e.promise = None
            store.set(e.cell, QueryState(QueryStatus.Success, Some(v), None, isFetching = false, QueryEnv.now()))
          case Failure(err) =>
            if attempt < e.retry then
              QueryEnv.schedule(
                () => if entries.get(key).exists(_ eq e) then runAttempt(key, e, fetch, attempt + 1),
                e.retryDelay(attempt),
              )
            else
              e.promise = None
              store.set(e.cell, store.get(e.cell).copy(status = QueryStatus.Error, error = Some(err), isFetching = false))
    }

  private def scheduleGc(key: QueryKey, e: Entry): Unit =
    e.gcCancel.foreach(_())
    e.gcCancel = Some(QueryEnv.schedule(() => evict(key, e), e.gcTime))

  // Drop an unobserved query from the cache: forget its cell in the store and
  // remove its entry. Guarded so a query re-observed during the countdown (now
  // active again, or replaced by a fresh entry) survives.
  private def evict(key: QueryKey, e: Entry): Unit =
    if entries.get(key).exists(_ eq e) && !e.active then
      store.forget(e.cell)
      entries -= key

  // --- public cache control --------------------------------------------------

  // Force a refetch of a query, deduped against any fetch already in flight.
  def refetch(key: QueryKey): Unit =
    entries.get(key).foreach(e => if e.promise.isEmpty && e.fetcher.isDefined then doFetch(key, e))

  // Mark a query stale and, if a component is currently observing it, refetch it
  // now. An unobserved query is just marked stale, so it refetches the next time
  // it is observed.
  def invalidate(key: QueryKey): Unit =
    entries.get(key).foreach { e =>
      store.set(e.cell, store.get(e.cell).copy(updatedAt = 0.0))
      if e.active && e.promise.isEmpty && e.fetcher.isDefined then doFetch(key, e)
    }

  // Eagerly load a query into the cache without a component observing it — for
  // warming data ahead of navigation. Creates the entry if absent and fetches if
  // stale; because nothing is observing it, its gc countdown starts immediately,
  // so a prefetch nobody adopts is evicted after `gcTime`.
  def prefetchQuery(key: QueryKey, fetcher: () => Future[Any], opts: QueryOptions = QueryOptions()): Unit =
    val e = entries.getOrElseUpdate(key, createUnobserved(key, Some(fetcher), opts))
    e.fetcher              = Some(fetcher)
    e.staleTime            = opts.staleTime
    e.gcTime               = opts.gcTime
    e.retry                = opts.retry
    e.retryDelay           = opts.retryDelay
    e.refetchOnWindowFocus = opts.refetchOnWindowFocus
    e.refetchOnReconnect   = opts.refetchOnReconnect
    fetchIfStale(key)

  // Invalidate every query whose key begins with `prefix` — the structured-key
  // payoff: `invalidatePrefix(queryKey("todos"))` reaches every `["todos", *]`.
  def invalidatePrefix(prefix: QueryKey): Unit =
    entries.keys.filter(_.startsWith(prefix)).toVector.foreach(invalidate)

  // Write data into a query's cell directly, as a fresh successful result — the
  // hook for optimistic updates and seeding. If no query has observed `key` yet,
  // a fetcher-less entry is created to hold the data; a later `useQuery(key, …)`
  // adopts it and supplies the fetcher. An unadopted seed is unobserved, so its gc
  // countdown is already running and it is evicted after `gcTime`.
  def setQueryData[A](key: QueryKey, data: A): Unit =
    val e = seedEntry(key)
    store.set(e.cell, QueryState(QueryStatus.Success, Some(data), None, isFetching = false, QueryEnv.now()))

  // Update a query's data from its current value — the form optimistic updates
  // use (e.g. append to a list). `updater` sees `None` when the key has no data
  // yet.
  def setQueryData[A](key: QueryKey, updater: Option[A] => A): Unit =
    val e    = seedEntry(key)
    val prev = store.get(e.cell).data.map(_.asInstanceOf[A])
    store.set(e.cell, QueryState(QueryStatus.Success, Some(updater(prev)), None, isFetching = false, QueryEnv.now()))

  // Read a query's current data without subscribing — for imperative reads outside
  // a component (e.g. computing an optimistic update from the present value).
  def getQueryData[A](key: QueryKey): Option[A] =
    entries.get(key).flatMap(e => store.get(e.cell).data).map(_.asInstanceOf[A])

  // --- infinite queries ------------------------------------------------------

  // Get-or-create the entry backing an infinite query, refreshing its page
  // machinery and options. The cell holds an `InfiniteData` and is driven by the
  // same fetch/stale/gc paths as a plain query; only the fetcher differs — its
  // steady form reloads every page currently held (so staleness, refetch, and
  // focus refresh the whole list, not just the first page), and `fetchNextPage`
  // extends the list. Called from `useInfiniteQuery` on every render.
  private[query] def registerInfinite[D, P](
      key:              QueryKey,
      fetchPage:        P => Future[D],
      initialPageParam: P,
      getNextPageParam: (D, Vector[D]) => Option[P],
      opts:             QueryOptions,
  ): PrimitiveAtom[QueryState[Any]] =
    val e = entries.getOrElseUpdate(key, createEntry(key, None, opts))
    val spec = new InfiniteSpec(
      fetchPage        = (p: Any) => fetchPage(p.asInstanceOf[P]),
      initialPageParam = initialPageParam,
      getNextPageParam = (last, all) => getNextPageParam(last.asInstanceOf[D], all.asInstanceOf[Vector[D]]),
    )
    e.infinite             = Some(spec)
    e.fetcher              = Some(() => refetchAllPages(e, spec))
    e.staleTime            = opts.staleTime
    e.gcTime               = opts.gcTime
    e.retry                = opts.retry
    e.retryDelay           = opts.retryDelay
    e.refetchOnWindowFocus = opts.refetchOnWindowFocus
    e.refetchOnReconnect   = opts.refetchOnReconnect
    e.cell

  // Reload, in order, every page param the query currently holds and fold them into
  // a fresh `InfiniteData`. With nothing loaded yet it fetches the single initial
  // param. This is the thunk the steady fetcher runs for an infinite query.
  private def refetchAllPages(e: Entry, spec: InfiniteSpec): Future[Any] =
    val cur    = store.get(e.cell).data.asInstanceOf[Option[InfiniteData[Any, Any]]]
    val params = cur.map(_.pageParams).filter(_.nonEmpty).getOrElse(Vector(spec.initialPageParam))
    params
      .foldLeft(Future.successful(Vector.empty[Any]))((accF, p) => accF.flatMap(acc => spec.fetchPage(p).map(acc :+ _)))
      .map(pages => InfiniteData(pages, params))

  // Load and append the next page of an infinite query. The next param comes from
  // the query's `getNextPageParam` applied to the pages loaded so far; if it yields
  // `None`, no pages are loaded yet, or a fetch is already in flight, this is a
  // no-op. The append goes through `startFetch`, so it inherits retry and dedup and
  // the appended `InfiniteData` replaces the cell wholesale.
  def fetchNextPage(key: QueryKey): Unit =
    entries.get(key).foreach { e =>
      if e.promise.isEmpty then
        e.infinite.foreach { spec =>
          store.get(e.cell).data.asInstanceOf[Option[InfiniteData[Any, Any]]].foreach { cur =>
            if cur.pages.nonEmpty then
              spec.getNextPageParam(cur.pages.last, cur.pages).foreach { nextParam =>
                val fetch = () =>
                  spec.fetchPage(nextParam).map(page => InfiniteData(cur.pages :+ page, cur.pageParams :+ nextParam))
                startFetch(key, e, fetch)
              }
          }
        }
    }
