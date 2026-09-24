# tineola

simd-accelerated multi-pattern string matching for scala.

named after *tineola bisselliella*, the common clothes moth. small, fast, finds things in fabric.

## what it does

finds all occurrences of a set of patterns in a haystack. combines two engines:

- a **teddy simd prefilter** (via `jdk.incubator.vector`) for small pattern sets. scans 32 bytes at a time on avx2 using the pshufb nibble-lookup trick from hyperscan/rust's `aho-corasick`
- a **double-array trie aho-corasick** automaton as scalar fallback. works for any pattern count, handles tails and large pattern sets

teddy auto-enables when patterns spread to <=4 per bucket (greedy by fingerprint, 8 buckets, hard cap 64). haystack must also fill a simd lane. everything else routes to the automaton. results are identical either way.

teddy's speedup comes from skipping verification when simd finds no candidates. false positive rate grows with pattern count and with shared prefix bytes:

1. expect 15-20x over scalar implementation for <=16 diverse-prefix patterns 
2. patterns sharing first bytes causes bucket collisions and the benefit of the prefilter weakens
3. the heuristic will usually auto-disable and fall back for a configuration with ~32+ dense/random patterns

## usage

```scala
libraryDependencies += "io.github.jciech" %% "tineola" % "<version>"
```

```scala
import tineola.{AhoCorasick, MatchKind}

val ac = AhoCorasick("foo", "bar", "baz")

ac.findAll("foobarbaz").toList
// List(Match(0,0,3), Match(1,3,6), Match(2,6,9))

ac.findFirst("xxbarxx")                           // Some(Match(1,2,5))
ac.isMatch("xxbazxx")                             // true
ac.replaceAll("foo and bar")(m => "*" * m.length) // "*** and ***"

// every match, overlapping ones included
AhoCorasick("he", "she", "hers").findOverlapping("ushers").toList
// List(Match(1,1,4), Match(0,2,4), Match(2,2,6))

// longest match wins instead of the first-listed pattern
AhoCorasick(Seq("app", "apple"), AhoCorasick.Options(matchKind = MatchKind.LeftmostLongest))

// scalar engine only
AhoCorasick(Seq("foo", "bar"), AhoCorasick.Options(simd = false))

// bytes, optionally within [from, until)
AhoCorasick.fromBytes(Seq("needle".getBytes)).findAll(bytes, from, until)
```

### semantics

- `findAll` returns non-overlapping matches from left to right. with the default `MatchKind.LeftmostFirst` the leftmost match wins, and among matches starting at the same position the pattern listed first wins, like regex alternation. `MatchKind.LeftmostLongest` picks the longest instead. `findFirst` returns the first match `findAll` would.
- `findOverlapping` returns every match, ordered by start, then end, then pattern index.
- `Match(pattern, start, end)` is half-open. offsets are char indices for a `String` (so `substring` works) and byte offsets for an `Array[Byte]`.
- iterators are lazy. `findAll` keeps constant state; `findOverlapping` walks the haystack in 64 KiB blocks.
- an `AhoCorasick` is immutable and safe to share across threads. patterns are copied when it's built and must be non-empty.

### migrating from 0.4

- `findAll` used to return every overlapping match; that's `findOverlapping` now.
- `findFirst` returns the leftmost match rather than the one that ends first.
- `String` inputs report char offsets instead of UTF-8 byte offsets.
- `AhoCorasick.builder` is gone: pass `AhoCorasick.Options(...)`, where `enableTeddy(false)` becomes `simd = false`.
- range arguments are `(from, until)`.

## requirements

- jdk 21 or later
- `--add-modules jdk.incubator.vector` at runtime for the simd engine. without it tineola falls back to the scalar automaton

jdk 24+ is recommended. `selectFrom` got index-wrap semantics ([jep 489](https://openjdk.org/jeps/489)) which lets the jit emit raw `vpshufb` without bounds checks.

## benchmarks

ops/ms. 64kb random-lowercase haystack, ~1% match density. jdk 21, avx2, 5 wi / 5 i.

| impl | 8 patterns | 16 patterns | 32 patterns | 64 patterns |
|---|---|---|---|---|
| tineola (teddy auto, 256-bit) | 69.86 | 47.25 | 4.05 | 4.30 |
| tineola (teddy 128-bit forced) | 38.54 | 30.66 | — | — |
| tineola (dat forced) | 3.25 | 2.69 | 3.04 | 4.31 |
| hankcs/AhoCorasickDoubleArrayTrie | 3.43 | 2.57 | 2.58 | 3.34 |
| robert-bor/aho-corasick | 1.57 | 1.10 | 1.21 | 1.17 |

lane width is auto-detected (`SPECIES_PREFERRED` capped at 256). at 64+ patterns here teddy disabled itself/

```
sbt "bench/Jmh/run -i 10 -wi 10 -f 3"
```

## references

- [teddy algorithm writeup](https://github.com/BurntSushi/aho-corasick/blob/master/src/packed/teddy/README.md) (burntSushi/aho-corasick)
- [engineering faster double-array aho-corasick automata](https://arxiv.org/abs/2207.13870) (daachorse paper)
- [jdk vector api](https://openjdk.org/jeps/529)
