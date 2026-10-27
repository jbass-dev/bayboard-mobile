package com.jbassdev.bayboard.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.jbassdev.bayboard.logic.Iso
import com.jbassdev.bayboard.logic.canTransition
import com.jbassdev.bayboard.logic.collapseUsage
import com.jbassdev.bayboard.logic.nextServiceDate
import com.jbassdev.bayboard.model.Bay
import com.jbassdev.bayboard.model.Customer
import com.jbassdev.bayboard.model.InventoryItem
import com.jbassdev.bayboard.model.PartUsed
import com.jbassdev.bayboard.model.ServiceType
import com.jbassdev.bayboard.model.StatusKind
import com.jbassdev.bayboard.model.Technician
import com.jbassdev.bayboard.model.Ticket
import com.jbassdev.bayboard.model.TicketStatus
import com.jbassdev.bayboard.model.Vehicle
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.Instant

/**
 * Firestore access for the board. Reads are real-time snapshot listeners
 * exposed as Flows, so a ticket moved on the web board shows up here
 * without a refresh (and vice versa).
 */
class BayBoardRepository(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {

    private fun <T> listen(
        query: Query,
        map: (String, Map<String, Any?>) -> T?,
    ): Flow<List<T>> = callbackFlow {
        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                trySend(snapshot.documents.mapNotNull { doc -> doc.data?.let { map(doc.id, it) } })
            }
        }
        awaitClose { registration.remove() }
    }

    /** Only open tickets — completed history isn't needed on the board. */
    fun activeTickets(): Flow<List<Ticket>> = listen(
        db.collection(TICKETS).whereIn("status.kind", listOf("waiting", "in-bay")),
        FirestoreMappers::ticket,
    )

    fun bays(): Flow<List<Bay>> = listen(db.collection(BAYS), FirestoreMappers::bay)

    fun technicians(): Flow<List<Technician>> =
        listen(db.collection(TECHNICIANS), FirestoreMappers::technician)

    fun inventory(): Flow<List<InventoryItem>> =
        listen(db.collection(INVENTORY), FirestoreMappers::inventoryItem)

    // --- Ticket actions ------------------------------------------------------

    suspend fun createTicket(
        vehicle: Vehicle,
        service: ServiceType,
        notes: String,
        customer: Customer?,
    ) {
        val now = Instant.now()
        val data = buildMap<String, Any> {
            put("vehicle", mapOf("year" to vehicle.year, "make" to vehicle.make, "model" to vehicle.model))
            put("service", service.id)
            put("status", FirestoreMappers.statusToMap(TicketStatus.Waiting(now)))
            put("partsUsed", emptyList<Any>())
            put("notes", notes)
            // Only persist a customer when an email was given — a reminder needs somewhere to send.
            if (customer != null && customer.email.isNotBlank()) {
                put("customer", mapOf("name" to customer.name, "email" to customer.email))
            }
            put("createdAt", Iso.format(now))
        }
        db.collection(TICKETS).add(data).await()
    }

    suspend fun assignToBay(ticket: Ticket, bayId: String, technicianId: String) {
        check(canTransition(ticket.status.kind, StatusKind.IN_BAY)) {
            "Can't move a ${ticket.status.kind.id} ticket into a bay."
        }
        val status = TicketStatus.InBay(bayId, technicianId, Instant.now())
        db.collection(TICKETS).document(ticket.id)
            .update("status", FirestoreMappers.statusToMap(status)).await()
    }

    suspend fun returnToWaiting(ticket: Ticket) {
        check(canTransition(ticket.status.kind, StatusKind.WAITING)) {
            "Can't return a ${ticket.status.kind.id} ticket to waiting."
        }
        val status = TicketStatus.Waiting(Instant.now())
        db.collection(TICKETS).document(ticket.id)
            .update("status", FirestoreMappers.statusToMap(status)).await()
    }

    /**
     * Finish an in-bay ticket. The completion write and the stock decrements
     * happen in one Firestore transaction — same guarantee as the web app: a
     * ticket can't be marked complete without its parts coming off the shelf,
     * and two bays closing at once can't double-count the same quantity.
     */
    suspend fun completeTicket(ticket: Ticket, partsUsed: List<PartUsed>) {
        val inBay = ticket.status as? TicketStatus.InBay
            ?: error("Can't complete a ${ticket.status.kind.id} ticket.")
        val completedAt = Instant.now()
        val complete = TicketStatus.Complete(inBay.bayId, inBay.technicianId, inBay.startedAt, completedAt)
        val ticketRef = db.collection(TICKETS).document(ticket.id)
        val ticketUpdate: Map<String, Any> = mapOf(
            "status" to FirestoreMappers.statusToMap(complete),
            "partsUsed" to FirestoreMappers.partsToList(partsUsed),
            "nextServiceDate" to Iso.format(nextServiceDate(ticket.service, completedAt)),
        )

        val usage = collapseUsage(partsUsed)
        if (usage.isEmpty()) {
            // Nothing consumed: a plain update keeps the common case cheap.
            ticketRef.update(ticketUpdate).await()
            return
        }

        db.runTransaction { tx ->
            // Firestore requires every read before any write in a transaction.
            val refs = usage.keys.map { db.collection(INVENTORY).document(it) }
            val snapshots = refs.map { tx.get(it) }

            tx.update(ticketRef, ticketUpdate)
            snapshots.forEachIndexed { i, snap ->
                // Item deleted mid-service: keep it on the ticket, skip the write.
                if (!snap.exists()) return@forEachIndexed
                val current = snap.getLong("quantity") ?: 0L
                val next = (current - usage.getValue(refs[i].id)).coerceAtLeast(0L)
                tx.update(refs[i], "quantity", next)
            }
            null
        }.await()
    }

    // --- Inventory -----------------------------------------------------------

    /** Restock or correct a count; clamped so stock can't go negative. */
    suspend fun setQuantity(itemId: String, quantity: Int) {
        db.collection(INVENTORY).document(itemId)
            .update("quantity", quantity.coerceAtLeast(0).toLong()).await()
    }

    private companion object {
        const val TICKETS = "tickets"
        const val BAYS = "bays"
        const val TECHNICIANS = "technicians"
        const val INVENTORY = "inventoryItems"
    }
}
