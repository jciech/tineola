package tineola

import org.scalacheck.Gen
import org.scalacheck.Prop.forAll

import java.nio.charset.StandardCharsets.UTF_8
import jdk.incubator.vector.ByteVector

class PropertySuite extends munit.ScalaCheckSuite {

  override def scalaCheckTestParameters =
    super.scalaCheckTestParameters.withMinSuccessfulTests(200)

  private val alphabet = "abcdefghijklmnopqrstuvwxyz0123456789"

  private val patternGen: Gen[String] =
    for {
      len <- Gen.choose(1, 8)
      chars <- Gen.listOfN(len, Gen.oneOf(alphabet))
    } yield chars.mkString

  private val patternsGen: Gen[List[String]] =
    for {
      n <- Gen.choose(1, 30)
      ps <- Gen.listOfN(n, patternGen)
    } yield ps.distinct

  private val manyPatternsGen: Gen[List[String]] =
    for {
      n <- Gen.choose(30, 80)
      ps <- Gen.listOfN(n, patternGen)
    } yield ps.distinct

  private val haystackGen: Gen[String] =
    for {
      len <- Gen.choose(0, 500)
      chars <- Gen.listOfN(len, Gen.oneOf(alphabet))
    } yield chars.mkString

  property("teddy (128) findAll == DAT findAll") {
    forAll(patternsGen, haystackGen) { (patterns, hay) =>
      parity(patterns, hay, ByteVector.SPECIES_128)
    }
  }

  property("teddy (256) findAll == DAT findAll") {
    forAll(patternsGen, haystackGen) { (patterns, hay) =>
      parity(patterns, hay, ByteVector.SPECIES_256)
    }
  }

  property("fat teddy findAll == DAT findAll") {
    forAll(manyPatternsGen, haystackGen) { (patterns, hay) =>
      parity(patterns, hay, ByteVector.SPECIES_256)
    }
  }

  private def parity(
      patterns: List[String],
      hay: String,
      species: jdk.incubator.vector.VectorSpecies[java.lang.Byte]
  ): Boolean = {
    if (patterns.isEmpty) return true
    val ps = patterns.map(_.getBytes(UTF_8))
    val teddy = AhoCorasick.builder.addAll(ps).teddySpecies(species).build()
    val dat = AhoCorasick.builder.addAll(ps).enableTeddy(false).build()
    val h = hay.getBytes(UTF_8)
    def collect(ac: AhoCorasick) =
      ac.findAll(h).map(m => (m.pattern, m.start, m.end)).toSet
    collect(teddy) == collect(dat)
  }

  property("DAT matches naive substring search") {
    forAll(patternsGen, haystackGen) { (patterns, hay) =>
      if (patterns.isEmpty) true
      else {
        val ac = AhoCorasick.builder
          .addAll(patterns.map(_.getBytes(UTF_8)))
          .enableTeddy(false)
          .build()
        val got = ac.findAll(hay).map(m => (m.pattern, m.start, m.end)).toSet
        val expected = (for {
          (p, i) <- patterns.zipWithIndex
          j <- 0 to (hay.length - p.length)
          if hay.substring(j, j + p.length) == p
        } yield (i, j, j + p.length)).toSet
        got == expected
      }
    }
  }
}
