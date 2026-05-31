package io.github.edadma.riposte.salle

// The style vocabulary shared across salle's components. Colour and size are
// *universal* — every component that has them uses these exact values — so they live
// here rather than being redeclared per component. The visual *variant* axis differs
// from one component to the next (a button's `outline` is meaningless for a card), so
// each component declares its own variant enum alongside itself.
//
// Components never name a literal colour or pixel size; they describe intent with
// these enums, and the active Skin turns intent into classes. That indirection is
// what lets one set of call sites target salle's own look or DaisyUI's unchanged.

/** The semantic colour roles, mirroring DaisyUI's palette. `Default` applies no
  * colour modifier — the component keeps its base look (DaisyUI's neutral default).
  */
enum Color:
  case Default, Primary, Secondary, Accent, Neutral, Info, Success, Warning, Error

  /** The lowercase modifier token (`"primary"`); empty for `Default`, which a
    * skin renders as "no colour class". */
  def token: String = this match
    case Default => ""
    case c       => c.toString.toLowerCase

/** The universal size scale, shared by every sized component. */
enum Size:
  case Xs, Sm, Md, Lg, Xl

  /** The lowercase modifier token (`"sm"`). */
  def token: String = toString.toLowerCase
