package tineola.automaton

import tineola.Match

private[tineola] final class DoubleArrayTrie(
    private val base: Array[Int],
    private val check: Array[Int],
    private val fail: Array[Int],
    private val output: Array[Array[Int]],
    private val rootGoto: Array[Int],
    val patternLengths: Array[Int]
) {

  def numPatterns: Int = patternLengths.length

  def findAll(haystack: Array[Byte], from: Int, to: Int, out: Match => Unit): Unit = {
    val base = this.base
    val check = this.check
    val fail = this.fail
    val output = this.output
    val rootGoto = this.rootGoto

    var state = 0
    var i = from
    while (i < to) {
      val b = haystack(i) & 0xff
      if (state == 0) {
        state = rootGoto(b)
      } else {
        var s = state
        var done = false
        while (!done) {
          val t = base(s) + b
          if (check(t) == s) { state = t; done = true }
          else {
            s = fail(s)
            if (s == 0) { state = rootGoto(b); done = true }
          }
        }
      }
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
}
