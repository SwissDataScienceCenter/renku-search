package io.renku.search.query

import munit.FunSuite

class StringsSpec extends FunSuite {

  extension (s: String) def lowerFirst = Strings.lowerFirst(s)

  test("make lower") {
    assertEquals("Cap".lowerFirst, "cap")
    assertEquals("cup".lowerFirst, "cup")
    assertEquals("ProductId".lowerFirst, "productId")
  }
}
