package com.nht.gif

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CropParamsTest {

  private val crop = CropParams(outW = 200, outH = 100, x = 50, y = 20)

  @Test
  fun `scaledTo is identity when frame matches video size`() =
    assertEquals(crop, crop.scaledTo(1000, 500, 1000, 500))

  @Test
  fun `scaledTo scales the rect proportionally for a smaller frame`() =
    assertEquals(CropParams(outW = 100, outH = 50, x = 25, y = 10), crop.scaledTo(500, 250, 1000, 500))

  @Test
  fun `scaledTo never exceeds the frame bounds when the frame is narrower than the crop`() {
    val r = crop.scaledTo(frameW = 120, frameH = 500, srcW = 1000, srcH = 500)
    assertInsideFrame(r, 120, 500)
  }

  @Test
  fun `scaledTo clamps a rect lying entirely outside the frame`() {
    val r = CropParams(outW = 100, outH = 100, x = 900, y = 900).scaledTo(100, 100, 1000, 1000)
    assertInsideFrame(r, 100, 100)
  }

  private fun assertInsideFrame(r: CropParams, frameW: Int, frameH: Int) {
    assertTrue("origin must be non-negative: $r", r.x >= 0 && r.y >= 0)
    assertTrue("size must be positive: $r", r.outW >= 1 && r.outH >= 1)
    assertTrue("must fit the frame: $r", r.x + r.outW <= frameW && r.y + r.outH <= frameH)
  }
}
