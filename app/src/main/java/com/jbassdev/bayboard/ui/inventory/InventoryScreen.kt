package com.jbassdev.bayboard.ui.inventory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jbassdev.bayboard.logic.isLowStock
import com.jbassdev.bayboard.model.InventoryItem
import com.jbassdev.bayboard.ui.board.BoardViewModel
import com.jbassdev.bayboard.ui.components.CenteredMessage
import com.jbassdev.bayboard.ui.components.QuantityStepper

@Composable
fun InventoryScreen(vm: BoardViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()

    when {
        state.loading -> CenteredMessage { CircularProgressIndicator() }
        state.inventory.isEmpty() -> CenteredMessage { Text("No inventory items yet.") }
        else -> LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.inventory, key = { it.id }) { item ->
                InventoryRow(item, onQuantityChange = { vm.setQuantity(item, it) })
            }
        }
    }
}

@Composable
private fun InventoryRow(item: InventoryItem, onQuantityChange: (Int) -> Unit) {
    val low = isLowStock(item)
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "${item.kind.label} · low at ${item.lowStockThreshold}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (low) {
                    // Text label, not just colour.
                    Text("Low stock", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
                }
            }
            QuantityStepper(value = item.quantity, onChange = onQuantityChange, itemLabel = item.name)
        }
    }
}
