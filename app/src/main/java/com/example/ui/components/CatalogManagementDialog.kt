package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun CatalogManagementDialog(
    categories: List<String>,
    suppliers: List<String>,
    onAdd: (String, String) -> Unit,
    onDelete: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    var newName by remember { mutableStateOf("") }
    val type = if (selectedTab == 0) "CATEGORY" else "SUPPLIER"
    val entries = if (selectedTab == 0) categories else suppliers

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configuración de catálogos", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0; newName = "" }, text = { Text("Categorías") })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1; newName = "" }, text = { Text("Proveedores") })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        modifier = Modifier.weight(1f),
                        label = { Text(if (selectedTab == 0) "Nueva categoría" else "Nuevo proveedor") },
                        singleLine = true
                    )
                    Button(
                        onClick = {
                            val clean = newName.trim()
                            if (clean.isNotEmpty() && entries.none { it.equals(clean, true) }) {
                                onAdd(clean, type)
                                newName = ""
                            }
                        },
                        enabled = newName.isNotBlank()
                    ) { Text("Agregar") }
                }
                Text(
                    text = "Elementos registrados: ${entries.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (entries.isEmpty()) {
                    Text("Todavía no hay elementos. Agrega el primero arriba.")
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(entries, key = { it }) { entry ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(entry, modifier = Modifier.weight(1f))
                                IconButton(onClick = { onDelete(entry, type) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Eliminar $entry", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Listo") } }
    )
}
