package tineola.bench

import java.nio.charset.StandardCharsets.UTF_8
import scala.util.Random

object Fixtures {

  private val rng = new Random(42)
  private val alphabet = "abcdefghijklmnopqrstuvwxyz"

  def randomPatterns(n: Int, minLen: Int, maxLen: Int): Array[String] = {
    val seen = scala.collection.mutable.Set.empty[String]
    while (seen.size < n) {
      val len = minLen + rng.nextInt(maxLen - minLen + 1)
      seen += (0 until len).map(_ => alphabet(rng.nextInt(alphabet.length))).mkString
    }
    seen.toArray
  }

  def randomHaystack(len: Int): Array[Byte] = {
    val bytes = new Array[Byte](len)
    var i = 0
    while (i < len) { bytes(i) = alphabet(rng.nextInt(alphabet.length)).toByte; i += 1 }
    bytes
  }

  def seededHaystack(len: Int, patterns: Array[String], density: Double): Array[Byte] = {
    val bytes = randomHaystack(len)
    val numSeeds = (len * density / patterns.map(_.length).sum.max(1)).toInt
    for (_ <- 0 until numSeeds) {
      val p = patterns(rng.nextInt(patterns.length)).getBytes(UTF_8)
      val pos = rng.nextInt(len - p.length)
      System.arraycopy(p, 0, bytes, pos, p.length)
    }
    bytes
  }
}
