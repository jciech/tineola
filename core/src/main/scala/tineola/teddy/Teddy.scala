package tineola.teddy

import tineola.Match
import tineola.automaton.DoubleArrayTrie

private[tineola] trait Teddy {
  def minHaystackLen: Int
  def findAll(haystack: Array[Byte], from: Int, to: Int, out: Match => Unit): Unit
}

private[tineola] object Teddy {

  val MaxPatterns = 128

  def tryBuild(patterns: Array[Array[Byte]], dat: DoubleArrayTrie): Option[Teddy] = {
    if (patterns.isEmpty || patterns.length > MaxPatterns) return None
    val minLen = patterns.iterator.map(_.length).min
    if (minLen < 1) return None
    val n = if (minLen >= 3) 3 else if (minLen >= 2) 2 else 1
    val masks = Masks.build(patterns, n)
    Some(n match {
      case 1 => new TeddySlim1(masks, dat)
      case 2 => new TeddySlim2(masks, dat)
      case 3 => new TeddySlim3(masks, dat)
    })
  }
}
