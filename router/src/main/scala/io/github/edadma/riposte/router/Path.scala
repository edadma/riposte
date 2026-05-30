package io.github.edadma.riposte.router

import scala.collection.mutable
import scala.scalajs.js.URIUtils.{decodeURIComponent, encodeURIComponent}

// Pure path matching for the router: turn a route pattern and a concrete URL path
// into the captured params (or no match), and rank patterns by specificity so the
// most specific matching route wins regardless of declaration order. Nothing here
// touches the DOM, so it is exercised by plain-`assert` unit tests.

// The path parameters captured from a matched route — `:id` segments by name, and
// a trailing `*` under the key "*".
type Params = Map[String, String]

// Split a path or pattern into its non-empty segments, so leading, trailing, and
// doubled slashes don't affect matching ("/users/" and "users" both → ["users"]).
private[router] def segments(s: String): Vector[String] =
  s.split('/').iterator.filter(_.nonEmpty).toVector

// Match `pat`'s segments against a *prefix* of `seg`, returning the captured
// params and the segments left unconsumed. A `:name` segment captures one path
// segment; a trailing `*` captures the whole remainder under "*" (and leaves
// nothing); every other segment must match literally. An empty pattern (the index
// route) consumes nothing. This is the shared primitive: a full match is a prefix
// match that consumes everything, and a nested parent consumes a prefix and hands
// the remainder to its children.
private[router] def matchPrefix(pat: Vector[String], seg: Vector[String]): Option[(Params, Vector[String])] =
  val params = mutable.LinkedHashMap.empty[String, String]
  var i      = 0
  while i < pat.length do
    val p = pat(i)
    if p == "*" then
      params("*") = seg.drop(i).mkString("/")
      return Some((params.toMap, Vector.empty))
    else if i >= seg.length then return None
    else if p.startsWith(":") then
      params(p.drop(1)) = seg(i)
      i += 1
    else if p == seg(i) then i += 1
    else return None
  Some((params.toMap, seg.drop(pat.length)))

// Match `path` fully against `pattern`: a prefix match that leaves no remainder.
// A `:name` segment captures that path segment under `name`; a trailing `*`
// captures the whole remainder under "*"; every other segment must match
// literally. Without a splat the lengths must be equal, so `/users` does not match
// `/users/:id`.
def matchPath(pattern: String, path: String): Option[Params] =
  matchPrefix(segments(pattern), segments(path)).collect {
    case (params, leftover) if leftover.isEmpty => params
  }

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

// Parse a raw query string (without the leading "?") into key/value pairs. A key
// with no "=" maps to the empty string; "+" decodes to a space, and percent
// escapes are decoded. Repeated keys keep the last value — single-valued, which
// covers the common case; a multi-valued reader can come later if needed.
def parseQuery(raw: String): Params =
  if raw.isEmpty then Map.empty
  else
    val out = mutable.LinkedHashMap.empty[String, String]
    for pair <- raw.split('&').iterator.filter(_.nonEmpty) do
      val i = pair.indexOf('=')
      if i < 0 then out(decode(pair)) = ""
      else out(decode(pair.substring(0, i))) = decode(pair.substring(i + 1))
    out.toMap

// Render key/value pairs back into a query string (without the leading "?"),
// percent-encoding each key and value. The inverse of `parseQuery` for the kinds
// of values a browser round-trips.
def encodeQuery(params: Params): String =
  params.iterator.map((k, v) => s"${encodeURIComponent(k)}=${encodeURIComponent(v)}").mkString("&")

private def decode(s: String): String = decodeURIComponent(s.replace('+', ' '))
