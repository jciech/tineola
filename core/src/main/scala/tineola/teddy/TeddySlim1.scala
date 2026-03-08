package tineola.teddy

import jdk.incubator.vector.{ByteVector, VectorOperators, VectorSpecies}

import tineola.Match
import tineola.automaton.DoubleArrayTrie

private[teddy] final class TeddySlim1(
    S: VectorSpecies[java.lang.Byte],
    m: Masks,
    dat: DoubleArrayTrie
) extends TeddySlim(S, m, dat) {

  private val loMask = ByteVector.fromArray(S, Masks.tile(m.lo(0), lane), 0)
  private val hiMask = ByteVector.fromArray(S, Masks.tile(m.hi(0), lane), 0)
  private val stride = lane

  final def scan(h: Array[Byte], from: Int, to: Int, out: Match => Boolean): Unit = {
    val bound = to - lane
    var i = from
    while (i <= bound) {
      val chunk = ByteVector.fromArray(S, h, i)
      val lo = chunk.and(nib)
      val hi = chunk.lanewise(VectorOperators.LSHR, 4).and(nib)
      val cand = lo.selectFrom(loMask).and(hi.selectFrom(hiMask))
      if (cand.compare(VectorOperators.NE, 0.toByte).anyTrue())
        if (!verify(h, i, to, stride, cand, out)) return
      i += stride
    }
    dat.scan(h, i, to, out)
  }
}
