package io.github.edadma.riposte.query

import io.github.edadma.riposte.*
import scala.concurrent.Future

// Infinite (paginated) queries. Where `useQuery` holds one result, an infinite
// query holds a growing list of pages: each `fetchNextPage` chooses the next page
// param from the pages loaded so far and appends the page it fetches. The cell
// stores an `InfiniteData`, so the whole list is one observable snapshot and the
// ordinary staleness / refetch / gc machinery applies to it unchanged.

// The accumulated pages of an infinite query and the param each was fetched with.
// `pages(i)` was loaded with `pageParams(i)`, so the two vectors stay the same
// length and in step.
final case class InfiniteData[D, P](pages: Vector[D], pageParams: Vector[P])

// What `useInfiniteQuery` returns — a named tuple, like `useQuery`'s result.
// `pages` is the convenience flattening of `data.pages`; `fetchNextPage` loads and
// appends the next page, `fetchPreviousPage` loads and prepends an older one (each
// a no-op when a fetch is in flight or that direction is exhausted). `hasNextPage`
// / `hasPreviousPage` reflect whether the respective param function yields another
// param from the pages loaded so far — `hasPreviousPage` stays false for a
// forward-only query (the default `getPreviousPageParam` returns `None`).
type InfiniteQueryResult[D, P] = (
    data:              Option[InfiniteData[D, P]],
    pages:             Vector[D],
    error:             Option[Throwable],
    isLoading:         Boolean,
    isFetching:        Boolean,
    isError:           Boolean,
    hasNextPage:       Boolean,
    fetchNextPage:     () => Unit,
    hasPreviousPage:   Boolean,
    fetchPreviousPage: () => Unit,
    refetch:           () => Unit,
)

// Subscribe a component to a paginated query. The first observe loads the page at
// `initialPageParam`; `result.fetchNextPage()` extends the list, using
// `getNextPageParam(lastPage, allPages)` to choose the next param and stopping when
// it returns `None`. `fetchPage` loads one page given its param.
//
// Pass `getPreviousPageParam` to make the query bidirectional — it can then also
// grow from the front via `result.fetchPreviousPage()`, choosing the previous param
// from the first page held. Omit it (the default returns `None`) for the common
// forward-only "load more" case, where `hasPreviousPage` stays false.
//
//   val q = useInfiniteQuery(
//     queryKey("messages", roomId),
//     (cursor: Long) => api.messages(roomId, cursor),
//     initialPageParam = openAt,
//     getNextPageParam = (last, _) => last.olderCursor,      // scroll down → history
//     getPreviousPageParam = (first, _) => first.newerCursor, // scroll up → newer
//   )
def useInfiniteQuery[D, P](
    key:                  QueryKey,
    fetchPage:            P => Future[D],
    initialPageParam:     P,
    getNextPageParam:     (D, Vector[D]) => Option[P],
    getPreviousPageParam: (D, Vector[D]) => Option[P] = (_: D, _: Vector[D]) => None,
    options:              QueryOptions = QueryOptions(),
)(using Hooks): InfiniteQueryResult[D, P] =
  val client = useQueryClient
  val cell   = client.registerInfinite(key, fetchPage, initialPageParam, getNextPageParam, getPreviousPageParam, options)

  // Subscribe through the client's store, exactly like `useQuery`; the cell holds
  // an `InfiniteData[Any, Any]` cast back to the page types on read.
  val subscribe = useCallback((cb: () => Unit) => client.store.sub(cell, cb), Array(cell))
  val st        = useSyncExternalStore(subscribe, () => client.store.get(cell))
  val data      = st.data.map(_.asInstanceOf[InfiniteData[D, P]])

  (
    data              = data,
    pages             = data.map(_.pages).getOrElse(Vector.empty),
    error             = st.error,
    isLoading         = st.status == QueryStatus.Pending && st.data.isEmpty,
    isFetching        = st.isFetching,
    isError           = st.status == QueryStatus.Error,
    hasNextPage       = data.exists(d => d.pages.nonEmpty && getNextPageParam(d.pages.last, d.pages).isDefined),
    fetchNextPage     = () => client.fetchNextPage(key),
    hasPreviousPage   = data.exists(d => d.pages.nonEmpty && getPreviousPageParam(d.pages.head, d.pages).isDefined),
    fetchPreviousPage = () => client.fetchPreviousPage(key),
    refetch           = () => client.refetch(key),
  )
