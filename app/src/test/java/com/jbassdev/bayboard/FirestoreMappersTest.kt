package com.jbassdev.bayboard

import com.jbassdev.bayboard.data.FirestoreMappers
import com.jbassdev.bayboard.logic.Iso
import com.jbassdev.bayboard.model.ServiceType
import com.jbassdev.bayboard.model.TicketStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class FirestoreMappersTest {

    /** A ticket exactly as the web app writes it (numbers arrive from Firestore as Long). */
    private val webTicket: Map<String, Any?> = mapOf(
        "vehicle" to mapOf("year" to 2019L, "make" to "Honda", "model" to "Civic"),
        "service" to "oil-change",
        "status" to mapOf(
            "kind" to "in-bay",
            "bayId" to "bay1",
            "technicianId" to "tech1",
            "startedAt" to "2026-10-27T15:04:05.123Z",
        ),
        "partsUsed" to emptyList<Any>(),
        "notes" to "Customer waiting in lobby",
        "customer" to mapOf("name" to "Sam", "email" to "sam@example.com"),
        "createdAt" to "2026-10-27T14:50:00.000Z",
    )

    @Test
    fun `reads a ticket written by the web app`() {
        val ticket = FirestoreMappers.ticket("t1", webTicket)!!
        assertEquals("2019 Honda Civic", ticket.vehicle.describe())
        assertEquals(ServiceType.OIL_CHANGE, ticket.service)
        assertEquals(
            TicketStatus.InBay("bay1", "tech1", Instant.parse("2026-10-27T15:04:05.123Z")),
            ticket.status,
        )
        assertEquals("sam@example.com", ticket.customer?.email)
    }

    @Test
    fun `skips documents that don't fit the model`() {
        assertNull(FirestoreMappers.ticket("bad", webTicket + ("service" to "car-wash")))
        assertNull(
            FirestoreMappers.ticket("bad", webTicket + ("status" to mapOf("kind" to "in-bay", "bayId" to "b1"))),
        )
    }

    @Test
    fun `status survives a round trip through Firestore maps`() {
        val start = Instant.parse("2026-10-27T15:00:00.000Z")
        val statuses = listOf(
            TicketStatus.Waiting(start),
            TicketStatus.InBay("b1", "t1", start),
            TicketStatus.Complete("b1", "t1", start, start.plusSeconds(1200)),
        )
        statuses.forEach { assertEquals(it, FirestoreMappers.status(FirestoreMappers.statusToMap(it))) }
    }

    @Test
    fun `timestamps match JavaScript toISOString format`() {
        val instant = Instant.parse("2026-10-27T15:04:05.123456789Z")
        assertEquals("2026-10-27T15:04:05.123Z", Iso.format(instant))
        assertEquals("2026-10-27T15:04:05.000Z", Iso.format(Instant.parse("2026-10-27T15:04:05Z")))
    }
}
