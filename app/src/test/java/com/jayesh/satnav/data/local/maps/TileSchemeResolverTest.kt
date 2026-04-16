package com.jayesh.satnav.data.local.maps

import com.jayesh.satnav.domain.model.OfflineTileScheme
import org.junit.Assert.assertEquals
import org.junit.Test

class TileSchemeResolverTest {

    @Test
    fun `xyz prefers raw row then flipped row`() {
        assertEquals(
            listOf(2, 1),
            TileSchemeResolver.candidateRows(z = 2, y = 2, declaredScheme = OfflineTileScheme.XYZ),
        )
    }

    @Test
    fun `tms prefers flipped row then raw row`() {
        assertEquals(
            listOf(1, 2),
            TileSchemeResolver.candidateRows(z = 2, y = 2, declaredScheme = OfflineTileScheme.TMS),
        )
    }
}
