package tineola

class HelloSuite extends munit.FunSuite {
  test("greet") {
    assertEquals(Hello.greet("world"), "Hello, world!")
  }
}
