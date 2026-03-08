package tineola.bench

import java.util.concurrent.TimeUnit

import scala.compiletime.uninitialized

import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole

import tineola.AhoCorasick

@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(value = 1, jvmArgsAppend = Array("--add-modules=jdk.incubator.vector"))
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
class VirtualBench {

  private var haystack: Array[Byte] = uninitialized
  private var acN1: AhoCorasick = uninitialized
  private var acN2: AhoCorasick = uninitialized
  private var acN3: AhoCorasick = uninitialized

  @Setup
  def setup(): Unit = {
    haystack = Fixtures.randomHaystack(65536)
    acN1 = AhoCorasick(Seq("Z"))
    acN2 = AhoCorasick(Seq("ZZ"))
    acN3 = AhoCorasick(Seq("ZZZ", "abcde", "fghij", "klmno", "pqrst", "uvwxy", "zabcd", "efghi"))
  }

  @Benchmark
  def mono_n3(bh: Blackhole): Unit = {
    val it = acN3.findAll(haystack)
    while (it.hasNext) bh.consume(it.next())
  }

  @Benchmark
  def poly_n123(bh: Blackhole): Unit = {
    var it = acN1.findAll(haystack)
    while (it.hasNext) bh.consume(it.next())
    it = acN2.findAll(haystack)
    while (it.hasNext) bh.consume(it.next())
    it = acN3.findAll(haystack)
    while (it.hasNext) bh.consume(it.next())
  }

  @Benchmark
  def mono_n3_x3(bh: Blackhole): Unit = {
    var it = acN3.findAll(haystack)
    while (it.hasNext) bh.consume(it.next())
    it = acN3.findAll(haystack)
    while (it.hasNext) bh.consume(it.next())
    it = acN3.findAll(haystack)
    while (it.hasNext) bh.consume(it.next())
  }
}
