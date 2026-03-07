package tineola.automaton

import tineola.Match

private[tineola] final class DoubleArrayTrie(
    private val base: Array[Int],
    private val check: Array[Int],
    private val fail: Array[Int],
    private val output: Array[Array[Int]],
    val patternLengths: Array[Int]
) {

  def numPatterns: Int = patternLengths.length

  def findAll(haystack: Array[Byte], from: Int, to: Int, out: Match => Unit): Unit = {
    var state = 0
    var i = from
    val n = to
    while (i < n) {
      state = step(state, haystack(i) & 0xff)
      val os = output(state)
      if (os != null) {
        var j = 0
        while (j < os.length) {
          val p = os(j)
          val len = patternLengths(p)
          out(Match(p, i - len + 1, i + 1))
          j += 1
        }
      }
      i += 1
    }
  }

  @inline
  private def step(state: Int, b: Int): Int = {
    var s = state
    while (true) {
      val t = base(s) + b
      if (t < check.length && check(t) == s) return t
      if (s == 0) return 0
      s = fail(s)
    }
    0
  }
}
