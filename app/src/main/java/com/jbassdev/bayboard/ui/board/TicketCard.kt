package com.jbassdev.bayboard.ui.board

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jbassdev.bayboard.logic.Severity
import com.jbassdev.bayboard.logic.elapsedMinutes
import com.jbassdev.bayboard.logic.elapsedSeverity
import com.jbassdev.bayboard.logic.formatElapsed
import com.jbassdev.bayboard.logic.statusTimestamp
import com.jbassdev.bayboard.logic.waitSeverity
import com.jbassdev.bayboard.model.Ticket
import com.jbassdev.bayboard.model.TicketStatus
import com.jbassdev.bayboard.ui.theme.warnColors
import java.time.Instant

@Composable
fun TicketCard(
    ticket: Ticket,
    now: Instant,
    technicianName: String?,
    actions: @Composable RowScope.() -> Unit,
) {
    val since = statusTimestamp(ticket)
    val minutes = elapsedMinutes(since, now)
    val waiting = ticket.status is TicketStatus.Waiting
    val severity = if (waiting) waitSeverity(minutes) else elapsedSeverity(ticket.service, minutes)

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        ticket.vehicle.describe(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        ticket.service.label + (technicianName?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ElapsedBadge(formatElapsed(since, now), severity, waiting)
            }
            if (ticket.notes.isNotBlank()) {
                Text(ticket.notes, style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
        }
    }
}

private data class BadgeStyle(val container: Color, val content: Color, val marker: String, val spoken: String)

/**
 * Elapsed time badge. Severity is carried by colour, a shape marker and a
 * spoken label — never colour alone (same rule as the web board).
 */
@Composable
fun ElapsedBadge(text: String, severity: Severity, waiting: Boolean) {
    val warn = warnColors()
    val scheme = MaterialTheme.colorScheme
    val style = when (severity) {
        Severity.NORMAL -> BadgeStyle(
            scheme.secondaryContainer, scheme.onSecondaryContainer, "●",
            if (waiting) "waiting" else "on track",
        )
        Severity.WARN -> BadgeStyle(
            warn.container, warn.content, "▲",
            if (waiting) "waiting a while" else "running long",
        )
        Severity.OVER -> BadgeStyle(
            scheme.errorContainer, scheme.onErrorContainer, "■",
            if (waiting) "waiting too long" else "well over target",
        )
    }
    Surface(
        color = style.container,
        contentColor = style.content,
        shape = RoundedCornerShape(50),
        modifier = Modifier.clearAndSetSemantics { contentDescription = "$text, ${style.spoken}" },
    ) {
        Text(
            "${style.marker} $text",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
