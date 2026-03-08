package tineola.teddy

private[tineola] final class Masks(
    val lo: Array[Array[Byte]],
    val hi: Array[Array[Byte]],
    val buckets: Array[Array[Int]],
    val patterns: Array[Array[Byte]]
) {
  def numBuckets: Int = buckets.length
  def maxBucketSize: Int = buckets.iterator.map(_.length).max
}

private[tineola] object Masks {

  val SlimBuckets = 8
  val FatBuckets = 16

  def tile(mask16: Array[Byte], width: Int): Array[Byte] = {
    val out = new Array[Byte](width)
    var i = 0
    while (i < width) { System.arraycopy(mask16, 0, out, i, 16); i += 16 }
    out
  }

  def concat(lo: Array[Byte], hi: Array[Byte]): Array[Byte] = {
    val out = new Array[Byte](32)
    System.arraycopy(lo, 0, out, 0, 16)
    System.arraycopy(hi, 0, out, 16, 16)
    out
  }

  def buildSlim(patterns: Array[Array[Byte]], fingerprintLen: Int): Masks =
    build(patterns, fingerprintLen, SlimBuckets)

  def buildFat(patterns: Array[Array[Byte]], fingerprintLen: Int): Masks =
    build(patterns, fingerprintLen, FatBuckets)

  private def build(patterns: Array[Array[Byte]], fingerprintLen: Int, nBuckets: Int): Masks = {
    val buckets = assignBuckets(patterns, fingerprintLen, nBuckets)
    val bytesPerPos = if (nBuckets <= 8) 1 else 2
    val lo = Array.ofDim[Byte](fingerprintLen, 16 * bytesPerPos)
    val hi = Array.ofDim[Byte](fingerprintLen, 16 * bytesPerPos)

    for (bucket <- 0 until nBuckets) {
      val bit = (1 << (bucket & 7)).toByte
      val half = (bucket >>> 3) * 16
      for (pid <- buckets(bucket)) {
        val p = patterns(pid)
        var k = 0
        while (k < fingerprintLen) {
          val b = p(k) & 0xff
          lo(k)(half + (b & 0x0f)) = (lo(k)(half + (b & 0x0f)) | bit).toByte
          hi(k)(half + (b >>> 4)) = (hi(k)(half + (b >>> 4)) | bit).toByte
          k += 1
        }
      }
    }

    new Masks(lo, hi, buckets, patterns)
  }

  private def assignBuckets(
      patterns: Array[Array[Byte]],
      flen: Int,
      nBuckets: Int
  ): Array[Array[Int]] = {
    val byFingerprint = patterns.zipWithIndex
      .groupBy { case (p, _) => new String(p, 0, flen, "ISO-8859-1") }
      .values
      .toArray
      .sortBy(-_.length)

    val buckets = Array.fill(nBuckets)(scala.collection.mutable.ArrayBuffer.empty[Int])
    val sizes = new Array[Int](nBuckets)

    for (group <- byFingerprint) {
      var best = 0
      var i = 1
      while (i < nBuckets) { if (sizes(i) < sizes(best)) best = i; i += 1 }
      for ((_, pid) <- group) buckets(best) += pid
      sizes(best) += group.length
    }

    buckets.map(_.toArray)
  }
}
