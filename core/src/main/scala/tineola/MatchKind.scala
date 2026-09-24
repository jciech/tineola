package tineola

sealed trait MatchKind

object MatchKind {
  case object LeftmostFirst extends MatchKind
  case object LeftmostLongest extends MatchKind
}
