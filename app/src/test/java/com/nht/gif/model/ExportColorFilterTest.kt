package com.nht.gif.model

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Test

class ExportColorFilterTest {

  // T1.12
  @Test
  fun `NONE vfChain is null`() {
    assertNull(ExportColorFilter.NONE.vfChain)
  }

  // T1.13
  @Test
  fun `every non-None preset has a non-null non-blank vfChain`() {
    ExportColorFilter.entries
      .filter { it != ExportColorFilter.NONE }
      .forEach { filter ->
        assertNotNull("${filter.name} vfChain must not be null", filter.vfChain)
        assertFalse("${filter.name} vfChain must not be blank", filter.vfChain!!.isBlank())
      }
  }
}
