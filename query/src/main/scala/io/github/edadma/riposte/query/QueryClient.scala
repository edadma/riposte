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
      val cell:      PrimitiveAtom[QueryState[Any]],
      var fetcher:   Option[() => Future[Any]],
      var staleTime: Double,
      var gcTime:    Double,
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

  private val entries = mutable.Map.empty[QueryKey, Entry]

  // Get-or-create the cell for `key`, refreshing the fetcher and options to the
  // latest call's values so a re-render with a new closure or new `staleTime`
  // takes effect. Called from `useQuery` on every render; the lifecycle is wired
  // once, when the entry is first created.
  private[query] def register(key: QueryKey, fetcher: () => Future[Any], opts: QueryOptions): PrimitiveAtom[QueryState[Any]] =
    val e = entries.getOrElseUpdate(key, createEntry(key, Some(fetcher), opts))
    e.fetcher   = Some(fetcher)
    e.staleTime = opts.staleTime
    e.gcTime    = opts.gcTime
    e.cell

  private def createEntry(key: QueryKey, fetcher: Option[() => Future[Any]], opts: QueryOptions): Entry =
    val cell = atom(QueryState.initial[Any])
    val e    = new Entry(cell, fetcher, opts.staleTime, opts.gcTime)
    // First observer: cancel any pending eviction and fetch if the data is stale.
    // Last observer: start the gc countdown. The store fires this on the cell's
    // listener set going empty→nonempty and the cleanup on nonempty→empty.
    onMount(cell) { _ =>
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

  // Fetch only if the cell has never settled or its result has aged past
  // `staleTime`, and only if no fetch is already in flight — stale-while-revalidate
  // plus in-flight dedup.
  private def fetchIfStale(key: QueryKey): Unit =
    entries.get(key).foreach { e =>
      val st    = store.get(e.cell)
      val stale = st.updatedAt == 0.0 || (QueryEnv.now() - st.updatedAt) >= e.staleTime
      if stale && e.promise.isEmpty && e.fetcher.isDefined then doFetch(key, e)
    }

  // Run the fetcher, marking the cell fetching, and fold the settled result back
  // into the cell. A result that arrives after the entry was evicted or replaced
  // is dropped — `_ eq e` confirms this is still the same live entry — so a
  // resurrected zombie cell can never be written. A no-op if the entry has no
  // fetcher (a key seeded by `setQueryData` with no query observing it yet).
  private def doFetch(key: QueryKey, e: Entry): Unit =
    e.fetcher.foreach { fetch =>
      store.set(e.cell, store.get(e.cell).copy(isFetching = true))
      val f = fetch()
      e.promise = Some(f)
      f.onComplete { res =>
        if entries.get(key).exists(_ eq e) then
          e.promise = None
          res match
            case Success(v) =>
              store.set(e.cell, QueryState(QueryStatus.Success, Some(v), None, isFetching = false, QueryEnv.now()))
            case Failure(err) =>
              store.set(e.cell, store.get(e.cell).copy(status = QueryStatus.Error, error = Some(err), isFetching = false))
      }
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
    e.fetcher   = Some(fetcher)
    e.staleTime = opts.staleTime
    e.gcTime    = opts.gcTime
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
