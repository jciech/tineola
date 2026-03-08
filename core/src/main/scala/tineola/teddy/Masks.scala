package tineola.teddy

private[tineola] final class Masks(
    val lo: Array[Array[Byte]],
    val hi: Array[Array[Byte]],
    val buckets: Array[Array[Int]],
    val patterns: Array[Array[Byte]]
)

private[tineola] object Masks {

  val NumBuckets = 8

  def tile(mask16: Array[Byte], width: Int): Array[Byte] = {
    val out = new Array[Byte](width)
    var i = 0
    while (i < width) { System.arraycopy(mask16, 0, out, i, 16); i += 16 }
    out
  }

  def build(patterns: Array[Array[Byte]], fingerprintLen: Int): Masks = {
    val buckets = assignBuckets(patterns, fingerprintLen)
    val lo = Array.ofDim[Byte](fingerprintLen, 16)
    val hi = Array.ofDim[Byte](fingerprintLen, 16)

    for (bucket <- 0 until NumBuckets) {
      val bit = (1 << bucket).toByte
      for (pid <- buckets(bucket)) {
        val p = patterns(pid)
        var k = 0
        while (k < fingerprintLen) {
          val b = p(k) & 0xff
          lo(k)(b & 0x0f) = (lo(k)(b & 0x0f) | bit).toByte
          hi(k)(b >>> 4) = (hi(k)(b >>> 4) | bit).toByte
          k += 1
        }
      }
    }

    new Masks(lo, hi, buckets, patterns)
  }

  private def assignBuckets(patterns: Array[Array[Byte]], flen: Int): Array[Array[Int]] = {
    val byFingerprint = patterns.zipWithIndex
      .groupBy { case (p, _) => new String(p, 0, flen, "ISO-8859-1") }
      .values
      .toArray
      .sortBy(-_.length)

    val buckets = Array.fill(NumBuckets)(scala.collection.mutable.ArrayBuffer.empty[Int])
    val sizes = new Array[Int](NumBuckets)

    for (group <- byFingerprint) {
      var best = 0
      var i = 1
      while (i < NumBuckets) { if (sizes(i) < sizes(best)) best = i; i += 1 }
      for ((_, pid) <- group) buckets(best) += pid
      sizes(best) += group.length
    }

    buckets.map(_.toArray)
  }
}
