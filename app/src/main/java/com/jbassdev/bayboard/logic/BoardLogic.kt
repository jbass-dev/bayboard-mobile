package com.jbassdev.bayboard.logic

import com.jbassdev.bayboard.model.Bay
import com.jbassdev.bayboard.model.InventoryItem
import com.jbassdev.bayboard.model.PartUsed
import com.jbassdev.bayboard.model.ServiceType
import com.jbassdev.bayboard.model.StatusKind
import com.jbassdev.bayboard.model.Ticket
import com.jbassdev.bayboard.model.TicketStatus
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/*
 * Pure board logic, ported from the web app's ticket-logic.ts and
 * board-logic.ts. No Firestore, no clock — everything takes its inputs,
 * which is what makes it unit-testable.
 */

private val ALLOWED_TRANSITIONS: Map<StatusKind, Set<StatusKind>> = mapOf(
    StatusKind.WAITING to setOf(StatusKind.IN_BAY),
    // Back to waiting if a bay is needed urgently.
    StatusKind.IN_BAY to setOf(StatusKind.WAITING, StatusKind.COMPLETE),
    StatusKind.COMPLETE to emptySet(),
)

fun canTransition(from: StatusKind, to: StatusKind): Boolean =
    ALLOWED_TRANSITIONS.getValue(from).contains(to)

/** Next-service date: completion date plus the service interval, in shop-local calendar days. */
fun nextServiceDate(
    service: ServiceType,
    completedAt: Instant,
    zone: ZoneId = ZoneId.systemDefault(),
): Instant = completedAt.atZone(zone).plusDays(service.intervalDays).toInstant()

enum class Severity { NORMAL, WARN, OVER }

/** "WARN" once a service reaches its target time, "OVER" at 1.5x. */
fun elapsedSeverity(service: ServiceType, minutesElapsed: Long): Severity {
    val target = service.targetMinutes
    return when {
        minutesElapsed * 2 >= target * 3L -> Severity.OVER
        minutesElapsed >= target -> Severity.WARN
        else -> Severity.NORMAL
    }
}

/** How long a car can sit in the queue before it's flagged. */
const val WAIT_WARN_MINUTES = 10L
const val WAIT_OVER_MINUTES = 20L

fun waitSeverity(minutesWaiting: Long): Severity = when {
    minutesWaiting >= WAIT_OVER_MINUTES -> Severity.OVER
    minutesWaiting >= WAIT_WARN_MINUTES -> Severity.WARN
    else -> Severity.NORMAL
}

fun isLowStock(item: InventoryItem): Boolean = item.quantity <= item.lowStockThreshold

/** Items at or below threshold, most depleted (furthest below threshold) first. */
fun lowStockItems(items: List<InventoryItem>): List<InventoryItem> =
    items.filter(::isLowStock).sortedBy { it.quantity - it.lowStockThreshold }

/** Waiting tickets, oldest first — the next car up is at the top. */
fun waitingTickets(tickets: List<Ticket>): List<Ticket> =
    tickets.filter { it.status is TicketStatus.Waiting }
        .sortedBy { (it.status as TicketStatus.Waiting).since }

/** Tickets currently in the given bay (normally one, but the data can't enforce that). */
fun bayTickets(tickets: List<Ticket>, bayId: String): List<Ticket> =
    tickets.filter { (it.status as? TicketStatus.InBay)?.bayId == bayId }
        .sortedBy { (it.status as TicketStatus.InBay).startedAt }

fun sortBays(bays: List<Bay>): List<Bay> = bays.sortedBy { it.sortOrder }

/** When the ticket entered its current column. */
fun statusTimestamp(ticket: Ticket): Instant = when (val s = ticket.status) {
    is TicketStatus.Waiting -> s.since
    is TicketStatus.InBay -> s.startedAt
    is TicketStatus.Complete -> s.completedAt
}

/** Whole minutes since [from], never negative. */
fun elapsedMinutes(from: Instant, now: Instant): Long =
    Duration.between(from, now).toMinutes().coerceAtLeast(0)

/** "4m", "1h 05m" */
fun formatElapsed(from: Instant, now: Instant): String {
    val mins = elapsedMinutes(from, now)
    if (mins < 60) return "${mins}m"
    return "${mins / 60}h ${(mins % 60).toString().padStart(2, '0')}m"
}

/** Merge duplicate part lines and drop zero/negative quantities. */
fun collapseUsage(parts: List<PartUsed>): Map<String, Int> {
    val usage = linkedMapOf<String, Int>()
    for (part in parts) {
        if (part.quantity > 0) {
            usage[part.inventoryItemId] = (usage[part.inventoryItemId] ?: 0) + part.quantity
        }
    }
    return usage
}
