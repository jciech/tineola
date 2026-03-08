package tineola.teddy

import jdk.incubator.vector.{ByteVector, VectorOperators, VectorShuffle}

import tineola.Match
import tineola.automaton.DoubleArrayTrie

private[tineola] abstract class TeddyFat(m: Masks, protected final val dat: DoubleArrayTrie)
    extends Teddy {

  protected final val S256 = ByteVector.SPECIES_256
  protected final val Chunk = 16
  protected final val nib = ByteVector.broadcast(S256, 0x0f)

  protected final val dupLo16 = {
    val idx = new Array[Int](32)
    var i = 0
    while (i < 32) { idx(i) = i & 15; i += 1 }
    VectorShuffle.fromArray(S256, idx, 0)
  }

  protected final val hiOff = {
    val a = new Array[Byte](32)
    var i = 16
    while (i < 32) { a(i) = 16; i += 1 }
    ByteVector.fromArray(S256, a, 0)
  }

  protected final def halfShift(n: Int): VectorShuffle[java.lang.Byte] = {
    val idx = new Array[Int](32)
    var i = 0
    while (i < 32) {
      val half = i & ~15
      val within = i & 15
      idx(i) = half + math.min(within + n, 15)
      i += 1
    }
    VectorShuffle.fromArray(S256, idx, 0)
  }

  private val buckets = m.buckets
  private val pats = m.patterns
  private val plens = dat.patternLengths
  private val maxPatLen = plens.max

  final def minHaystackLen: Int = 32 + maxPatLen

  @inline
  protected final def dupChunk(h: Array[Byte], i: Int): ByteVector =
    ByteVector.fromArray(S256, h, i).rearrange(dupLo16)

  @inline
  protected final def nibLo(chunk: ByteVector): ByteVector =
    chunk.and(nib).or(hiOff)

  @inline
  protected final def nibHi(chunk: ByteVector): ByteVector =
    chunk.lanewise(VectorOperators.LSHR, 4).and(nib).or(hiOff)

  protected final def verify(
      h: Array[Byte],
      base: Int,
      end: Int,
      stride: Int,
      cand: ByteVector,
      out: Match => Boolean
  ): Boolean = {
    val longs = cand.reinterpretAsLongs()
    var k = 0
    while (k < 4) {
      var bits = longs.lane(k)
      while (bits != 0L) {
        val tz = java.lang.Long.numberOfTrailingZeros(bits)
        bits &= bits - 1L
        val off = ((k & 1) << 3) + (tz >>> 3)
        if (off < stride) {
          val bucket = ((k & 2) << 2) | (tz & 7)
          val ps = buckets(bucket)
          val pos = base + off
          var j = 0
          while (j < ps.length) {
            val pid = ps(j)
            val plen = plens(pid)
            if (pos + plen <= end && matches(h, pos, pats(pid), plen))
              if (!out(Match(pid, pos, pos + plen))) return false
            j += 1
          }
        }
      }
      k += 1
    }
    true
  }

  @inline
  private def matches(h: Array[Byte], pos: Int, p: Array[Byte], len: Int): Boolean = {
    var i = 0
    while (i < len) { if (h(pos + i) != p(i)) return false; i += 1 }
    true
  }
}
