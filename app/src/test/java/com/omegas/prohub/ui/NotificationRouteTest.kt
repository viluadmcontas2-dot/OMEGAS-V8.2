package com.omegas.prohub.ui

import org.junit.Assert.*
import org.junit.Test
class NotificationRouteTest {
    @Test fun `only refinement route is accepted from notification`() {
        assertEquals("refino",NotificationRoute.fromExtra("refino"))
        for(value in listOf(null,"","curve","javascript:alert(1)","refino?write=true"))assertNull(NotificationRoute.fromExtra(value))
    }
}
