package com.jbassdev.bayboard.ui.board

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jbassdev.bayboard.model.Bay
import com.jbassdev.bayboard.model.Customer
import com.jbassdev.bayboard.model.InventoryItem
import com.jbassdev.bayboard.model.PartUsed
import com.jbassdev.bayboard.model.ServiceType
import com.jbassdev.bayboard.model.Technician
import com.jbassdev.bayboard.model.Ticket
import com.jbassdev.bayboard.model.Vehicle
import com.jbassdev.bayboard.ui.components.EmptyHint
import com.jbassdev.bayboard.ui.components.QuantityStepper
import com.jbassdev.bayboard.ui.components.RadioRow

@Composable
fun AssignDialog(
    ticket: Ticket,
    bays: List<BayColumn>,
    technicians: List<Technician>,
    onDismiss: () -> Unit,
    onAssign: (Bay, Technician) -> Unit,
) {
    // Default to the first open bay.
    var bayId by remember { mutableStateOf(bays.firstOrNull { it.tickets.isEmpty() }?.bay?.id) }
    var techId by remember { mutableStateOf<String?>(null) }
    val bay = bays.firstOrNull { it.bay.id == bayId }?.bay
    val tech = technicians.firstOrNull { it.id == techId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Assign ${ticket.vehicle.describe()}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Bay", style = MaterialTheme.typography.titleSmall)
                if (bays.isEmpty()) EmptyHint("No bays set up yet.")
                bays.forEach { column ->
                    val busy = if (column.tickets.isNotEmpty()) " — busy" else ""
                    RadioRow("${column.bay.name}$busy", bayId == column.bay.id) { bayId = column.bay.id }
                }
                Spacer(Modifier.height(12.dp))
                Text("Technician", style = MaterialTheme.typography.titleSmall)
                if (technicians.isEmpty()) EmptyHint("No technicians yet.")
                technicians.forEach { t ->
                    RadioRow(t.name, techId == t.id) { techId = t.id }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (bay != null && tech != null) onAssign(bay, tech) },
                enabled = bay != null && tech != null,
            ) { Text("Assign") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun CompleteDialog(
    ticket: Ticket,
    inventory: List<InventoryItem>,
    onDismiss: () -> Unit,
    onComplete: (List<PartUsed>) -> Unit,
) {
    val counts = remember { mutableStateMapOf<String, Int>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Complete ${ticket.vehicle.describe()}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Parts used come off the shelf when you complete.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (inventory.isEmpty()) EmptyHint("No inventory items yet.")
                inventory.forEach { item ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${item.quantity} on shelf",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        QuantityStepper(
                            value = counts[item.id] ?: 0,
                            onChange = { counts[item.id] = it.coerceIn(0, 99) },
                            itemLabel = item.name,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onComplete(counts.filterValues { it > 0 }.map { (id, qty) -> PartUsed(id, qty) })
            }) { Text("Complete") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun NewTicketDialog(
    onDismiss: () -> Unit,
    onCreate: (Vehicle, ServiceType, String, Customer?) -> Unit,
) {
    var year by rememberSaveable { mutableStateOf("") }
    var make by rememberSaveable { mutableStateOf("") }
    var model by rememberSaveable { mutableStateOf("") }
    var service by rememberSaveable { mutableStateOf(ServiceType.OIL_CHANGE) }
    var notes by rememberSaveable { mutableStateOf("") }
    var customerName by rememberSaveable { mutableStateOf("") }
    var customerEmail by rememberSaveable { mutableStateOf("") }

    val yearInt = year.toIntOrNull()
    val valid = yearInt != null && yearInt in 1950..2100 && make.isNotBlank() && model.isNotBlank()
    val words = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New ticket") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = year,
                    onValueChange = { year = it.filter(Char::isDigit).take(4) },
                    label = { Text("Year") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(make, { make = it }, label = { Text("Make") }, singleLine = true, keyboardOptions = words, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(model, { model = it }, label = { Text("Model") }, singleLine = true, keyboardOptions = words, modifier = Modifier.fillMaxWidth())

                Text("Service", style = MaterialTheme.typography.titleSmall)
                Column {
                    ServiceType.entries.forEach { option ->
                        RadioRow(option.label, service == option) { service = option }
                    }
                }

                OutlinedTextField(notes, { notes = it }, label = { Text("Notes (optional)") }, modifier = Modifier.fillMaxWidth())

                Text("Customer — optional, for next-service reminders", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(customerName, { customerName = it }, label = { Text("Name") }, singleLine = true, keyboardOptions = words, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = customerEmail,
                    onValueChange = { customerEmail = it },
                    label = { Text("Email") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    if (yearInt != null) {
                        val customer = customerEmail.trim().takeIf { it.isNotEmpty() }
                            ?.let { Customer(customerName.trim(), it) }
                        onCreate(Vehicle(yearInt, make.trim(), model.trim()), service, notes.trim(), customer)
                    }
                },
            ) { Text("Add to queue") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
