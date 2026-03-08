package tineola.teddy

import jdk.incubator.vector.{ByteVector, VectorOperators}

import tineola.Match
import tineola.automaton.DoubleArrayTrie

private[teddy] final class TeddyFat1(m: Masks, dat: DoubleArrayTrie) extends TeddyFat(m, dat) {

  private val loMask = ByteVector.fromArray(S256, m.lo(0), 0)
  private val hiMask = ByteVector.fromArray(S256, m.hi(0), 0)
  private val stride = Chunk

  final def findAll(h: Array[Byte], from: Int, to: Int, out: Match => Unit): Unit = {
    val bound = to - 32
    var i = from
    while (i <= bound) {
      val chunk = dupChunk(h, i)
      val lo = nibLo(chunk)
      val hi = nibHi(chunk)
      val cand = lo.selectFrom(loMask).and(hi.selectFrom(hiMask))
      if (cand.compare(VectorOperators.NE, 0.toByte).anyTrue())
        verify(h, i, to, stride, cand, out)
      i += stride
    }
    dat.findAll(h, i, to, out)
  }
}
