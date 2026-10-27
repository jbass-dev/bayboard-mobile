package com.jbassdev.bayboard

import com.jbassdev.bayboard.logic.Severity
import com.jbassdev.bayboard.logic.bayTickets
import com.jbassdev.bayboard.logic.canTransition
import com.jbassdev.bayboard.logic.collapseUsage
import com.jbassdev.bayboard.logic.elapsedSeverity
import com.jbassdev.bayboard.logic.formatElapsed
import com.jbassdev.bayboard.logic.lowStockItems
import com.jbassdev.bayboard.logic.nextServiceDate
import com.jbassdev.bayboard.logic.waitSeverity
import com.jbassdev.bayboard.logic.waitingTickets
import com.jbassdev.bayboard.model.InventoryItem
import com.jbassdev.bayboard.model.InventoryKind
import com.jbassdev.bayboard.model.PartUsed
import com.jbassdev.bayboard.model.ServiceType
import com.jbassdev.bayboard.model.StatusKind
import com.jbassdev.bayboard.model.Ticket
import com.jbassdev.bayboard.model.TicketStatus
import com.jbassdev.bayboard.model.Vehicle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class BoardLogicTest {

    private val t0: Instant = Instant.parse("2026-10-27T15:00:00.000Z")

    private fun ticket(id: String, status: TicketStatus) = Ticket(
        id = id,
        vehicle = Vehicle(2019, "Honda", "Civic"),
        service = ServiceType.OIL_CHANGE,
        status = status,
        createdAt = t0,
    )

    private fun item(id: String, qty: Int, threshold: Int) =
        InventoryItem(id, id, InventoryKind.OIL, qty, threshold)

    @Test
    fun `tickets only move along allowed transitions`() {
        assertTrue(canTransition(StatusKind.WAITING, StatusKind.IN_BAY))
        assertTrue(canTransition(StatusKind.IN_BAY, StatusKind.COMPLETE))
        assertTrue(canTransition(StatusKind.IN_BAY, StatusKind.WAITING))
        assertFalse(canTransition(StatusKind.WAITING, StatusKind.COMPLETE))
        assertFalse(canTransition(StatusKind.COMPLETE, StatusKind.WAITING))
        assertFalse(canTransition(StatusKind.COMPLETE, StatusKind.IN_BAY))
    }

    @Test
    fun `service severity flips at target and at one and a half times target`() {
        // Oil change target is 20 minutes.
        assertEquals(Severity.NORMAL, elapsedSeverity(ServiceType.OIL_CHANGE, 19))
        assertEquals(Severity.WARN, elapsedSeverity(ServiceType.OIL_CHANGE, 20))
        assertEquals(Severity.WARN, elapsedSeverity(ServiceType.OIL_CHANGE, 29))
        assertEquals(Severity.OVER, elapsedSeverity(ServiceType.OIL_CHANGE, 30))
        // Engine air filter target is 15: 1.5x is 22.5, so 22 is still WARN.
        assertEquals(Severity.WARN, elapsedSeverity(ServiceType.ENGINE_AIR_FILTER, 22))
        assertEquals(Severity.OVER, elapsedSeverity(ServiceType.ENGINE_AIR_FILTER, 23))
    }

    @Test
    fun `queue wait severity uses its own thresholds`() {
        assertEquals(Severity.NORMAL, waitSeverity(9))
        assertEquals(Severity.WARN, waitSeverity(10))
        assertEquals(Severity.OVER, waitSeverity(20))
    }

    @Test
    fun `elapsed time formats minutes and hours and never goes negative`() {
        assertEquals("4m", formatElapsed(t0, t0.plusSeconds(4 * 60 + 59)))
        assertEquals("1h 05m", formatElapsed(t0, t0.plusSeconds(65 * 60)))
        assertEquals("0m", formatElapsed(t0, t0.minusSeconds(300)))
    }

    @Test
    fun `next service date adds the interval in calendar days`() {
        val next = nextServiceDate(ServiceType.OIL_CHANGE, t0, ZoneOffset.UTC)
        assertEquals(Instant.parse("2027-01-25T15:00:00.000Z"), next)
    }

    @Test
    fun `low stock includes items at threshold, most depleted first`() {
        val items = listOf(item("a", 10, 12), item("b", 20, 12), item("c", 12, 12), item("d", 1, 6))
        assertEquals(listOf("d", "a", "c"), lowStockItems(items).map { it.id })
    }

    @Test
    fun `waiting queue is oldest first and bays only hold their own tickets`() {
        val tickets = listOf(
            ticket("late", TicketStatus.Waiting(t0.plusSeconds(600))),
            ticket("early", TicketStatus.Waiting(t0)),
            ticket("bay1", TicketStatus.InBay("b1", "tech", t0)),
            ticket("bay2", TicketStatus.InBay("b2", "tech", t0)),
        )
        assertEquals(listOf("early", "late"), waitingTickets(tickets).map { it.id })
        assertEquals(listOf("bay1"), bayTickets(tickets, "b1").map { it.id })
    }

    @Test
    fun `part usage merges duplicates and drops empty lines`() {
        val usage = collapseUsage(
            listOf(PartUsed("oil", 5), PartUsed("filter", 1), PartUsed("oil", 1), PartUsed("air", 0)),
        )
        assertEquals(mapOf("oil" to 6, "filter" to 1), usage)
    }
}
