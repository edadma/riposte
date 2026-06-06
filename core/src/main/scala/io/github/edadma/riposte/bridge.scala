package io.github.edadma.riposte

import io.github.edadma.vdom
import org.scalajs.dom

// The bridge from the host-agnostic `vdom` core to riposte's public surface. The
// engine — VNode model, reconciler, hooks, scheduler, components, context — lives
// in package `io.github.edadma.vdom` and names no DOM type. This file re-exports it
// under `io.github.edadma.riposte` so that every existing import (`import
// io.github.edadma.riposte.*` in salle, forms, router, query, and apps) keeps
// resolving exactly as before. The HTML/SVG builder DSL and the DOM-specific hooks
// stay in this package (Dsl.scala, DomHooks.scala) — they are the DOM host's own
// vocabulary, not part of the portable core.
//
// Type aliases carry the implicit conversions for free: an expected `riposte.VNode`
// dealiases to `vdom.VNode`, so the `Conversion[String, VNode]` in vdom's companion
// is still found. Case-class construction (`VElement(...)`, `Attr(...)`) needs a term
// in scope, so each constructed type also gets a `val` bound to its companion.

// --- host installation -----------------------------------------------------

// Wire the DOM host and the browser scheduling/timing seams the first time anything
// in this file's package object is touched. That set includes Scheduler / Transition
// / Timers (which the tests configure) and createRoot / render (every mount), so the
// real seams are always in place before a tree mounts or a test overrides one.
private val _hostInstalled: Unit = RiposteDom.install()

// --- VNode model -----------------------------------------------------------

type VNode          = vdom.VNode
type VText          = vdom.VText
type VElement       = vdom.VElement
type VFragment      = vdom.VFragment
type VComponent[P]  = vdom.VComponent[P]
type VProvider[T]   = vdom.VProvider[T]
type VPortal        = vdom.VPortal
type VErrorBoundary = vdom.VErrorBoundary

val VText          = vdom.VText
val VElement       = vdom.VElement
val VFragment      = vdom.VFragment
val VComponent     = vdom.VComponent
val VProvider      = vdom.VProvider
val VPortal        = vdom.VPortal
val VErrorBoundary = vdom.VErrorBoundary
val VEmpty         = vdom.VEmpty

type Prop         = vdom.Prop
type Attr         = vdom.Attr
type BoolAttr     = vdom.BoolAttr
type Handler      = vdom.Handler
type StyleProp    = vdom.StyleProp
type RawHtml      = vdom.RawHtml
type PropValue    = vdom.PropValue
type EventOptions = vdom.EventOptions

val Attr         = vdom.Attr
val BoolAttr     = vdom.BoolAttr
val Handler      = vdom.Handler
val StyleProp    = vdom.StyleProp
val RawHtml      = vdom.RawHtml
val PropValue    = vdom.PropValue
val EventOptions = vdom.EventOptions

type ElementRef = vdom.ElementRef
type BoxRef[T]  = vdom.BoxRef[T]
type FnRef      = vdom.FnRef

val BoxRef = vdom.BoxRef
val FnRef  = vdom.FnRef

// --- components, context, hooks state --------------------------------------

type Component[P]        = vdom.Component[P]
type Component2[A, B]    = vdom.Component2[A, B]
type Component3[A, B, C] = vdom.Component3[A, B, C]
type Component4[A, B, C, D] = vdom.Component4[A, B, C, D]
type Container           = vdom.Container
type ContainerP[P]       = vdom.ContainerP[P]
type Children            = vdom.Children
type Context[T]          = vdom.Context[T]

type Hooks   = vdom.Hooks
type Ref[T]  = vdom.Ref[T]
type Cleanup = vdom.Cleanup
type Root    = vdom.Root

// Term aliases: `Hooks.transitionCurrent` (white-box tested), the singleton seams,
// and the no-op cleanup.
val Hooks      = vdom.Hooks
val Scheduler  = vdom.Scheduler
val Transition = vdom.Transition
val Timers     = vdom.Timers
val noCleanup  = vdom.noCleanup

// Exposed for the transition tests, which build a cell and call the easing math.
type TransitionCell = vdom.TransitionCell

// Presence (exit-animation lifecycle), used by salle's overlay components.
type Presence      = vdom.Presence
type PresencePhase = vdom.PresencePhase
val PresencePhase  = vdom.PresencePhase

// --- hook entry points ------------------------------------------------------

def useState[T](initial: => T)(using Hooks): (T, T => Unit, (T => T) => Unit) =
  vdom.useState(initial)

def useEffect(body: () => Cleanup, deps: Array[Any] | Null)(using Hooks): Unit =
  vdom.useEffect(body, deps)

def useLayoutEffect(body: () => Cleanup, deps: Array[Any] | Null)(using Hooks): Unit =
  vdom.useLayoutEffect(body, deps)

def useRef[T](initial: T)(using Hooks): Ref[T] = vdom.useRef(initial)

def useMemo[T](compute: () => T, deps: Array[Any])(using Hooks): T = vdom.useMemo(compute, deps)

def useCallback[F](fn: F, deps: Array[Any])(using Hooks): F = vdom.useCallback(fn, deps)

def useReducer[S, A](reducer: (S, A) => S, initial: S)(using Hooks): (S, A => Unit) =
  vdom.useReducer(reducer, initial)

def useId()(using Hooks): String = vdom.useId()

def useTransition(target: Double, durationMs: Int)(using Hooks): Double =
  vdom.useTransition(target, durationMs)

def useContext[T](ctx: Context[T])(using Hooks): T = vdom.useContext(ctx)

def useSyncExternalStore[T](
    subscribe:   (() => Unit) => (() => Unit),
    getSnapshot: () => T,
)(using Hooks): T = vdom.useSyncExternalStore(subscribe, getSnapshot)

def useImperativeHandle[T](ref: Ref[T], factory: () => T, deps: Array[Any] | Null)(using Hooks): Unit =
  vdom.useImperativeHandle(ref, factory, deps)

def useDeferredValue[T](value: T)(using Hooks): T = vdom.useDeferredValue(value)

def useDebouncedValue[T](value: T, delayMs: Int)(using Hooks): T =
  vdom.useDebouncedValue(value, delayMs)

def useThrottledValue[T](value: T, intervalMs: Int)(using Hooks): T =
  vdom.useThrottledValue(value, intervalMs)

def usePresence(open: Boolean, exitMs: Int)(using Hooks): Presence =
  vdom.usePresence(open, exitMs)

// --- component / container / context builders -------------------------------

def component[P](render: P => (Hooks ?=> VNode)): Component[P] = vdom.component(render)
def component[A, B](render: (A, B) => (Hooks ?=> VNode)): Component2[A, B] = vdom.component(render)
def component[A, B, C](render: (A, B, C) => (Hooks ?=> VNode)): Component3[A, B, C] = vdom.component(render)
def component[A, B, C, D](render: (A, B, C, D) => (Hooks ?=> VNode)): Component4[A, B, C, D] = vdom.component(render)

def view(render: Hooks ?=> VNode): Component[Unit] = vdom.view(render)

extension (c: Component[Unit]) def apply(): VNode = c.apply(())

def memo[P](c: Component[P]): Component[P]                = vdom.memo(c)
def memo[A, B](c: Component2[A, B]): Component2[A, B]     = vdom.memo(c)
def memo[A, B, C](c: Component3[A, B, C]): Component3[A, B, C] = vdom.memo(c)
def memo[A, B, C, D](c: Component4[A, B, C, D]): Component4[A, B, C, D] = vdom.memo(c)
def memo(c: Container): Container                        = vdom.memo(c)
def memo[P](c: ContainerP[P]): ContainerP[P]             = vdom.memo(c)

def container(render: Children => (Hooks ?=> VNode)): Container = vdom.container(render)
def container[P](render: (P, Children) => (Hooks ?=> VNode)): ContainerP[P] = vdom.container(render)

def createContext[T](default: T): Context[T] = vdom.createContext(default)

// --- entry point ------------------------------------------------------------

// The container is narrowed to `dom.Element` for callers; vdom's `Root` mutates it
// through the installed DOM host.
def createRoot(container: dom.Element): Root = vdom.createRoot(container)

def render(vnode: VNode, container: dom.Element): Root = vdom.render(vnode, container)
