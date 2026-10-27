package com.jbassdev.bayboard.ui.board

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jbassdev.bayboard.data.BayBoardRepository
import com.jbassdev.bayboard.data.ServiceLocator
import com.jbassdev.bayboard.logic.bayTickets
import com.jbassdev.bayboard.logic.lowStockItems
import com.jbassdev.bayboard.logic.sortBays
import com.jbassdev.bayboard.logic.waitingTickets
import com.jbassdev.bayboard.model.Bay
import com.jbassdev.bayboard.model.Customer
import com.jbassdev.bayboard.model.InventoryItem
import com.jbassdev.bayboard.model.PartUsed
import com.jbassdev.bayboard.model.ServiceType
import com.jbassdev.bayboard.model.Technician
import com.jbassdev.bayboard.model.Ticket
import com.jbassdev.bayboard.model.Vehicle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BayColumn(val bay: Bay, val tickets: List<Ticket>)

data class BoardUiState(
    val loading: Boolean = true,
    val waiting: List<Ticket> = emptyList(),
    val bays: List<BayColumn> = emptyList(),
    val technicians: List<Technician> = emptyList(),
    val inventory: List<InventoryItem> = emptyList(),
    val lowStock: List<InventoryItem> = emptyList(),
    val error: String? = null,
) {
    fun technicianName(id: String): String? = technicians.firstOrNull { it.id == id }?.name
}

/**
 * Shared by the Board and Inventory tabs. Four Firestore listeners are
 * combined into one immutable UI state; every write goes through the
 * repository and reports back as a one-shot snackbar message.
 */
class BoardViewModel(
    private val repo: BayBoardRepository = ServiceLocator.boardRepository,
) : ViewModel() {

    val state: StateFlow<BoardUiState> = combine(
        repo.activeTickets(),
        repo.bays(),
        repo.technicians(),
        repo.inventory(),
    ) { tickets, bays, technicians, inventory ->
        BoardUiState(
            loading = false,
            waiting = waitingTickets(tickets),
            bays = sortBays(bays).map { BayColumn(it, bayTickets(tickets, it.id)) },
            technicians = technicians.sortedBy { it.name },
            inventory = inventory.sortedWith(compareBy({ it.kind }, { it.name })),
            lowStock = lowStockItems(inventory),
        )
    }
        .catch { emit(BoardUiState(loading = false, error = it.message ?: "Couldn't load the board.")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BoardUiState())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun createTicket(vehicle: Vehicle, service: ServiceType, notes: String, customer: Customer?) =
        act("${vehicle.describe()} added to the queue") {
            repo.createTicket(vehicle, service, notes, customer)
        }

    fun assign(ticket: Ticket, bay: Bay, technician: Technician) =
        act("${ticket.vehicle.describe()} → ${bay.name} with ${technician.name}") {
            repo.assignToBay(ticket, bay.id, technician.id)
        }

    fun returnToWaiting(ticket: Ticket) =
        act("${ticket.vehicle.describe()} back to waiting") { repo.returnToWaiting(ticket) }

    fun complete(ticket: Ticket, parts: List<PartUsed>) =
        act("${ticket.vehicle.describe()} complete") { repo.completeTicket(ticket, parts) }

    fun setQuantity(item: InventoryItem, quantity: Int) =
        act(null) { repo.setQuantity(item.id, quantity) }

    private fun act(success: String?, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                success?.let { _messages.emit(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.emit(e.message ?: "Something went wrong.")
            }
        }
    }
}
