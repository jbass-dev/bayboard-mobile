package com.jbassdev.bayboard.model

import java.time.Instant

/**
 * BayBoard core data model — mirrors the web app's `src/types/index.ts`
 * so both clients read and write the same Firestore documents.
 */
enum class ServiceType(
    /** The string stored in Firestore. */
    val id: String,
    val label: String,
    /** Days until the vehicle is due again. */
    val intervalDays: Long,
    /** Minutes before a running service counts as "running long". */
    val targetMinutes: Int,
) {
    OIL_CHANGE("oil-change", "Oil change", 90, 20),
    TIRE_ROTATION("tire-rotation", "Tire rotation", 180, 30),
    ENGINE_AIR_FILTER("engine-air-filter", "Engine air filter", 365, 15),
    CABIN_AIR_FILTER("cabin-air-filter", "Cabin air filter", 365, 15),
    COOLANT_FLUSH("coolant-flush", "Coolant flush", 730, 45);

    companion object {
        fun fromId(id: String?): ServiceType? = entries.firstOrNull { it.id == id }
    }
}

data class Vehicle(val year: Int, val make: String, val model: String) {
    /** "2019 Honda Civic" */
    fun describe(): String = "$year $make $model"
}

enum class StatusKind(val id: String) {
    WAITING("waiting"),
    IN_BAY("in-bay"),
    COMPLETE("complete"),
}

/**
 * Ticket status as a sealed hierarchy — the Kotlin twin of the web app's
 * discriminated union. A completed ticket with no bay can't be represented.
 */
sealed interface TicketStatus {
    val kind: StatusKind

    data class Waiting(val since: Instant) : TicketStatus {
        override val kind: StatusKind get() = StatusKind.WAITING
    }

    data class InBay(
        val bayId: String,
        val technicianId: String,
        val startedAt: Instant,
    ) : TicketStatus {
        override val kind: StatusKind get() = StatusKind.IN_BAY
    }

    data class Complete(
        val bayId: String,
        val technicianId: String,
        val startedAt: Instant,
        val completedAt: Instant,
    ) : TicketStatus {
        override val kind: StatusKind get() = StatusKind.COMPLETE
    }
}

data class PartUsed(val inventoryItemId: String, val quantity: Int)

data class Customer(val name: String, val email: String)

data class Ticket(
    val id: String,
    val vehicle: Vehicle,
    val service: ServiceType,
    val status: TicketStatus,
    val partsUsed: List<PartUsed> = emptyList(),
    val notes: String = "",
    val customer: Customer? = null,
    val nextServiceDate: Instant? = null,
    val createdAt: Instant,
)

data class Bay(val id: String, val name: String, val sortOrder: Int)

data class Technician(val id: String, val name: String)

enum class InventoryKind(val id: String, val label: String) {
    OIL("oil", "Oil"),
    FILTER("filter", "Filter");

    companion object {
        fun fromId(id: String?): InventoryKind = entries.firstOrNull { it.id == id } ?: FILTER
    }
}

data class InventoryItem(
    val id: String,
    val name: String,
    val kind: InventoryKind,
    val quantity: Int,
    val lowStockThreshold: Int,
)
