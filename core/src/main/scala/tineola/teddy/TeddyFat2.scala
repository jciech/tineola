package tineola.teddy

import jdk.incubator.vector.{ByteVector, VectorOperators}

import tineola.Match
import tineola.automaton.DoubleArrayTrie

private[teddy] final class TeddyFat2(m: Masks, dat: DoubleArrayTrie) extends TeddyFat(m, dat) {

  private val lo0 = ByteVector.fromArray(S256, m.lo(0), 0)
  private val hi0 = ByteVector.fromArray(S256, m.hi(0), 0)
  private val lo1 = ByteVector.fromArray(S256, m.lo(1), 0)
  private val hi1 = ByteVector.fromArray(S256, m.hi(1), 0)
  private val shl1 = halfShift(1)
  private val stride = Chunk - 1

  final def scan(h: Array[Byte], from: Int, to: Int, out: Match => Boolean): Unit = {
    val bound = to - 32
    var i = from
    while (i <= bound) {
      val chunk = dupChunk(h, i)
      val lo = nibLo(chunk)
      val hi = nibHi(chunk)
      val c0 = lo.selectFrom(lo0).and(hi.selectFrom(hi0))
      val c1 = lo.selectFrom(lo1).and(hi.selectFrom(hi1))
      val cand = c0.and(c1.rearrange(shl1))
      if (cand.compare(VectorOperators.NE, 0.toByte).anyTrue())
        if (!verify(h, i, to, stride, cand, out)) return
      i += stride
    }
    dat.scan(h, i, to, out)
  }
}
