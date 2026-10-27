package com.jbassdev.bayboard.ui.board

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jbassdev.bayboard.model.InventoryItem
import com.jbassdev.bayboard.model.Ticket
import com.jbassdev.bayboard.model.TicketStatus
import com.jbassdev.bayboard.ui.components.CenteredMessage
import com.jbassdev.bayboard.ui.components.EmptyHint
import com.jbassdev.bayboard.ui.components.SectionHeader
import kotlinx.coroutines.delay
import java.time.Instant

@Composable
fun BoardScreen(vm: BoardViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    // Re-tick elapsed badges every 30 s without re-reading Firestore.
    val now by produceState(Instant.now()) {
        while (true) {
            delay(30_000)
            value = Instant.now()
        }
    }
    var assigning by remember { mutableStateOf<Ticket?>(null) }
    var completing by remember { mutableStateOf<Ticket?>(null) }

    when {
        state.loading -> CenteredMessage { CircularProgressIndicator() }
        state.error != null -> CenteredMessage { Text(state.error ?: "", color = MaterialTheme.colorScheme.error) }
        else -> LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.lowStock.isNotEmpty()) {
                item(key = "low-stock") { LowStockBanner(state.lowStock) }
            }

            item(key = "waiting-header") { SectionHeader("Waiting", state.waiting.size) }
            if (state.waiting.isEmpty()) item(key = "waiting-empty") { EmptyHint("No cars waiting.") }
            items(state.waiting, key = { it.id }) { ticket ->
                TicketCard(ticket, now, technicianName = null) {
                    Button(onClick = { assigning = ticket }) { Text("Assign to bay") }
                }
            }

            state.bays.forEach { column ->
                item(key = "bay-${column.bay.id}") { SectionHeader(column.bay.name, column.tickets.size) }
                if (column.tickets.isEmpty()) {
                    item(key = "bay-empty-${column.bay.id}") { EmptyHint("Open") }
                }
                items(column.tickets, key = { it.id }) { ticket ->
                    val techId = (ticket.status as? TicketStatus.InBay)?.technicianId
                    TicketCard(ticket, now, technicianName = techId?.let { state.technicianName(it) }) {
                        Button(onClick = { completing = ticket }) { Text("Complete") }
                        OutlinedButton(onClick = { vm.returnToWaiting(ticket) }) { Text("Back to waiting") }
                    }
                }
            }

            if (state.bays.isEmpty()) {
                item(key = "no-bays") {
                    EmptyHint("No bays yet. Sign in on bayboard.vercel.app and load demo data to set up the shop.")
                }
            }
        }
    }

    assigning?.let { ticket ->
        AssignDialog(
            ticket = ticket,
            bays = state.bays,
            technicians = state.technicians,
            onDismiss = { assigning = null },
            onAssign = { bay, tech ->
                vm.assign(ticket, bay, tech)
                assigning = null
            },
        )
    }
    completing?.let { ticket ->
        CompleteDialog(
            ticket = ticket,
            inventory = state.inventory,
            onDismiss = { completing = null },
            onComplete = { parts ->
                vm.complete(ticket, parts)
                completing = null
            },
        )
    }
}

/** Announced politely by TalkBack when it appears or changes. */
@Composable
private fun LowStockBanner(items: List<InventoryItem>) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Low stock", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(items.joinToString { "${it.name} (${it.quantity} left)" })
        }
    }
}
