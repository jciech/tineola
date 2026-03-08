package tineola.teddy

import jdk.incubator.vector.{ByteVector, VectorSpecies}

import tineola.Match
import tineola.automaton.DoubleArrayTrie

private[teddy] abstract class TeddySlim(
    protected final val S: VectorSpecies[java.lang.Byte],
    m: Masks,
    protected final val dat: DoubleArrayTrie
) extends Teddy {

  protected final val lane = S.length()
  private val numLongs = lane >>> 3
  protected final val nib = ByteVector.broadcast(S, 0x0f)
  private val buckets = m.buckets
  private val pats = m.patterns
  private val plens = dat.patternLengths
  private val maxPatLen = plens.max

  final def minHaystackLen: Int = lane + maxPatLen

  protected final def verify(
      h: Array[Byte],
      base: Int,
      end: Int,
      stride: Int,
      cand: ByteVector,
      out: Match => Unit
  ): Unit = {
    val longs = cand.reinterpretAsLongs()
    var k = 0
    while (k < numLongs) {
      var bits = longs.lane(k)
      while (bits != 0L) {
        val tz = java.lang.Long.numberOfTrailingZeros(bits)
        bits &= bits - 1L
        val off = (k << 3) + (tz >>> 3)
        if (off < stride) {
          val bucket = tz & 7
          val ps = buckets(bucket)
          val pos = base + off
          var j = 0
          while (j < ps.length) {
            val pid = ps(j)
            val plen = plens(pid)
            if (pos + plen <= end && matches(h, pos, pats(pid), plen))
              out(Match(pid, pos, pos + plen))
            j += 1
          }
        }
      }
      k += 1
    }
  }

  @inline
  private def matches(h: Array[Byte], pos: Int, p: Array[Byte], len: Int): Boolean = {
    var i = 0
    while (i < len) { if (h(pos + i) != p(i)) return false; i += 1 }
    true
  }
}
