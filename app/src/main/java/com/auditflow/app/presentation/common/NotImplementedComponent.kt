package com.auditflow.app.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auditflow.app.presentation.theme.Amber50
import com.auditflow.app.presentation.theme.Amber500
import com.auditflow.app.presentation.theme.Amber600
import com.auditflow.app.presentation.theme.Navy900
import com.auditflow.app.presentation.theme.Slate600

/**
 * Visual badge and banner ensuring truthful representation of capability status.
 */
@Composable
fun NotImplementedBadge(
    modifier: Modifier = Modifier,
    text: String = "NOT IMPLEMENTED YET"
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Amber50)
            .border(1.dp, Amber500, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = Amber600,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
fun NotImplementedDialog(
    featureTitle: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = Amber500
                )
                Text(
                    text = "NOT IMPLEMENTED YET",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column {
                Text(
                    text = featureTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Navy900
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "This capability belongs to a future AuditFlow phase. In Phase 1A (Foundation & Build Infrastructure), only the core architecture, build system, and truthful empty state are active.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Slate600
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Navy900)
            ) {
                Text("Dismiss")
            }
        }
    )
}
