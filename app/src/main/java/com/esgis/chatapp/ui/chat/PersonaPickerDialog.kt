package com.esgis.chatapp.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.esgis.chatapp.data.AiPersona

/**
 * Sélecteur de personnage IA (généraliste / juridique / technique).
 * Chaque persona correspond à un system prompt différent.
 */
@Composable
fun PersonaPickerDialog(
    onDismiss: () -> Unit,
    onPersonaSelected: (AiPersona) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choisir un assistant IA") },
        text = {
            Column {
                AiPersona.entries.forEach { persona ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPersonaSelected(persona) }
                            .padding(vertical = 12.dp)
                    ) {
                        Text(
                            text = persona.label,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = persona.systemPrompt,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}
