package com.jbassdev.bayboard.data

import com.jbassdev.bayboard.logic.Iso
import com.jbassdev.bayboard.model.Bay
import com.jbassdev.bayboard.model.Customer
import com.jbassdev.bayboard.model.InventoryItem
import com.jbassdev.bayboard.model.InventoryKind
import com.jbassdev.bayboard.model.PartUsed
import com.jbassdev.bayboard.model.ServiceType
import com.jbassdev.bayboard.model.Technician
import com.jbassdev.bayboard.model.Ticket
import com.jbassdev.bayboard.model.TicketStatus
import com.jbassdev.bayboard.model.Vehicle

/**
 * Converts raw Firestore maps to typed models and back. These take plain
 * maps rather than DocumentSnapshots so they're testable on the JVM.
 * A document that doesn't fit the model returns null and is skipped,
 * rather than crashing the board.
 */
object FirestoreMappers {

    fun ticket(id: String, data: Map<String, Any?>): Ticket? {
        val vehicle = vehicle(data["vehicle"] as? Map<*, *>) ?: return null
        val service = ServiceType.fromId(data["service"] as? String) ?: return null
        val status = status(data["status"] as? Map<*, *>) ?: return null
        val parts = (data["partsUsed"] as? List<*>).orEmpty().mapNotNull { raw ->
            val m = raw as? Map<*, *> ?: return@mapNotNull null
            val itemId = m["inventoryItemId"] as? String ?: return@mapNotNull null
            PartUsed(itemId, (m["quantity"] as? Number)?.toInt() ?: 0)
        }
        val customer = (data["customer"] as? Map<*, *>)?.let { c ->
            val email = c["email"] as? String ?: return@let null
            Customer(name = c["name"] as? String ?: "", email = email)
        }
        return Ticket(
            id = id,
            vehicle = vehicle,
            service = service,
            status = status,
            partsUsed = parts,
            notes = data["notes"] as? String ?: "",
            customer = customer,
            nextServiceDate = Iso.parse(data["nextServiceDate"] as? String),
            createdAt = Iso.parse(data["createdAt"] as? String) ?: statusInstant(status),
        )
    }

    private fun vehicle(m: Map<*, *>?): Vehicle? {
        if (m == null) return null
        return Vehicle(
            year = (m["year"] as? Number)?.toInt() ?: return null,
            make = m["make"] as? String ?: "",
            model = m["model"] as? String ?: "",
        )
    }

    fun status(m: Map<*, *>?): TicketStatus? {
        if (m == null) return null
        val bayId = m["bayId"] as? String
        val techId = m["technicianId"] as? String
        val startedAt = Iso.parse(m["startedAt"] as? String)
        return when (m["kind"]) {
            "waiting" -> Iso.parse(m["since"] as? String)?.let { TicketStatus.Waiting(it) }
            "in-bay" ->
                if (bayId != null && techId != null && startedAt != null) {
                    TicketStatus.InBay(bayId, techId, startedAt)
                } else null
            "complete" -> {
                val completedAt = Iso.parse(m["completedAt"] as? String)
                if (bayId != null && techId != null && startedAt != null && completedAt != null) {
                    TicketStatus.Complete(bayId, techId, startedAt, completedAt)
                } else null
            }
            else -> null
        }
    }

    fun statusToMap(status: TicketStatus): Map<String, Any> = when (status) {
        is TicketStatus.Waiting -> mapOf(
            "kind" to status.kind.id,
            "since" to Iso.format(status.since),
        )
        is TicketStatus.InBay -> mapOf(
            "kind" to status.kind.id,
            "bayId" to status.bayId,
            "technicianId" to status.technicianId,
            "startedAt" to Iso.format(status.startedAt),
        )
        is TicketStatus.Complete -> mapOf(
            "kind" to status.kind.id,
            "bayId" to status.bayId,
            "technicianId" to status.technicianId,
            "startedAt" to Iso.format(status.startedAt),
            "completedAt" to Iso.format(status.completedAt),
        )
    }

    fun partsToList(parts: List<PartUsed>): List<Map<String, Any>> =
        parts.map { mapOf("inventoryItemId" to it.inventoryItemId, "quantity" to it.quantity) }

    fun bay(id: String, data: Map<String, Any?>): Bay? {
        val name = data["name"] as? String ?: return null
        return Bay(id, name, (data["sortOrder"] as? Number)?.toInt() ?: Int.MAX_VALUE)
    }

    fun technician(id: String, data: Map<String, Any?>): Technician? {
        val name = data["name"] as? String ?: return null
        return Technician(id, name)
    }

    fun inventoryItem(id: String, data: Map<String, Any?>): InventoryItem? {
        val name = data["name"] as? String ?: return null
        return InventoryItem(
            id = id,
            name = name,
            kind = InventoryKind.fromId(data["kind"] as? String),
            quantity = (data["quantity"] as? Number)?.toInt() ?: 0,
            lowStockThreshold = (data["lowStockThreshold"] as? Number)?.toInt() ?: 0,
        )
    }

    private fun statusInstant(status: TicketStatus) = when (status) {
        is TicketStatus.Waiting -> status.since
        is TicketStatus.InBay -> status.startedAt
        is TicketStatus.Complete -> status.startedAt
    }
}
