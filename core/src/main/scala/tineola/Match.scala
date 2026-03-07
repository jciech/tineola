package tineola

final case class Match(pattern: Int, start: Int, end: Int) {
  def length: Int = end - start
}
