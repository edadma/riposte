package io.github.edadma.riposte.router

import scala.collection.mutable

// Pure path matching for the router: turn a route pattern and a concrete URL path
// into the captured params (or no match), and rank patterns by specificity so the
// most specific matching route wins regardless of declaration order. Nothing here
// touches the DOM, so it is exercised by plain-`assert` unit tests.

// The path parameters captured from a matched route — `:id` segments by name, and
// a trailing `*` under the key "*".
type Params = Map[String, String]

// Split a path or pattern into its non-empty segments, so leading, trailing, and
// doubled slashes don't affect matching ("/users/" and "users" both → ["users"]).
private def segments(s: String): Vector[String] =
  s.split('/').iterator.filter(_.nonEmpty).toVector

// Match `path` against `pattern`. A `:name` segment captures that path segment
// under `name`; a `*` segment (meaningful only as the last one) captures the whole
// remainder under "*"; every other segment must match literally. Without a splat
// the lengths must be equal, so `/users` does not match `/users/:id`.
def matchPath(pattern: String, path: String): Option[Params] =
  val pat    = segments(pattern)
  val seg    = segments(path)
  val params = mutable.LinkedHashMap.empty[String, String]
  var i      = 0
  while i < pat.length do
    val p = pat(i)
    if p == "*" then
      params("*") = seg.drop(i).mkString("/")
      return Some(params.toMap)
    else if i >= seg.length then return None
    else if p.startsWith(":") then
      params(p.drop(1)) = seg(i)
      i += 1
    else if p == seg(i) then i += 1
    else return None
  if seg.length == pat.length then Some(params.toMap) else None

// A pattern's specificity, most-significant component first: more literal segments
// beat fewer, then more params, then the absence of a splat (`-splat`, so 0 beats
// -1). Comparing these tuples lets `/users/new` win over `/users/:id`, `/users/:id`
// win over `/users/*`, and `/` win over a `*` catch-all.
def specificity(pattern: String): (Int, Int, Int) =
  val segs    = segments(pattern)
  val literal = segs.count(s => s != "*" && !s.startsWith(":"))
  val param   = segs.count(_.startsWith(":"))
  val splat   = if segs.contains("*") then 1 else 0
  (literal, param, -splat)
