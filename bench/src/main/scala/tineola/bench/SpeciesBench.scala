package tineola.bench

import java.util.concurrent.TimeUnit

import scala.compiletime.uninitialized

import jdk.incubator.vector.ByteVector
import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole

import tineola.AhoCorasick

@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(value = 1, jvmArgsAppend = Array("--add-modules=jdk.incubator.vector"))
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
class SpeciesBench {

  @Param(Array("8", "16"))
  var numPatterns: Int = uninitialized

  @Param(Array("65536"))
  var haystackLen: Int = uninitialized

  private var haystack: Array[Byte] = uninitialized
  private var ac128: AhoCorasick = uninitialized
  private var ac256: AhoCorasick = uninitialized
  private var acPreferred: AhoCorasick = uninitialized

  @Setup
  def setup(): Unit = {
    val patterns = Fixtures.randomPatterns(numPatterns, 4, 10)
    haystack = Fixtures.seededHaystack(haystackLen, patterns, 0.01)
    val ps = patterns.map(_.getBytes).toIndexedSeq

    ac128 = AhoCorasick.builder.addAll(ps).teddySpecies(ByteVector.SPECIES_128).build()
    ac256 = AhoCorasick.builder.addAll(ps).teddySpecies(ByteVector.SPECIES_256).build()
    acPreferred = AhoCorasick.builder.addAll(ps).build()
  }

  @Benchmark
  def teddy_128(bh: Blackhole): Unit = {
    val it = ac128.findAll(haystack)
    while (it.hasNext) bh.consume(it.next())
  }

  @Benchmark
  def teddy_256(bh: Blackhole): Unit = {
    val it = ac256.findAll(haystack)
    while (it.hasNext) bh.consume(it.next())
  }

  @Benchmark
  def teddy_preferred(bh: Blackhole): Unit = {
    val it = acPreferred.findAll(haystack)
    while (it.hasNext) bh.consume(it.next())
  }
}
