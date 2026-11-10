package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.Store

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreManagementDialog(
    stores: List<Store>,
    onDismiss: () -> Unit,
    onSaveStore: (Store) -> Unit,
    onDeleteStore: (Store) -> Unit,
    initialStoreToEdit: Store? = null
) {
    var isEditing by remember { mutableStateOf(initialStoreToEdit != null) }
    var currentStoreId by remember { mutableStateOf(initialStoreToEdit?.id ?: 0L) }
    var name by remember { mutableStateOf(initialStoreToEdit?.name ?: "") }
    var address by remember { mutableStateOf(initialStoreToEdit?.address ?: "") }
    var phone by remember { mutableStateOf(initialStoreToEdit?.phone ?: "") }
    var logoUrl by remember { mutableStateOf(initialStoreToEdit?.logoUrl ?: "") }
    var selectedColor by remember { mutableStateOf(initialStoreToEdit?.colorHex ?: "#0284C7") }
    var nameError by remember { mutableStateOf(false) }

    val presetColors = listOf("#0284C7", "#10B981", "#8B5CF6", "#F59E0B", "#EC4899", "#475569")

    fun resetForm() {
        isEditing = false
        currentStoreId = 0L
        name = ""
        address = ""
        phone = ""
        logoUrl = ""
        selectedColor = "#0284C7"
        nameError = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isEditing) "Editar Sucursal" else "Gestión de Sucursales",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Cerrar")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                if (isEditing) {
                    // Form fields
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            nameError = it.isBlank()
                        },
                        label = { Text("Nombre de la Tienda *") },
                        isError = nameError,
                        supportingText = { if (nameError) Text("El nombre es requerido") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("store_name_input"),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("Dirección") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("store_address_input"),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Teléfono / WhatsApp") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("store_phone_input"),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = logoUrl,
                        onValueChange = { logoUrl = it },
                        label = { Text("URL del Logo (Opcional)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("store_logo_input"),
                        singleLine = true
                    )

                    // Logo Preview
                    if (logoUrl.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Vista previa logo:", style = MaterialTheme.typography.bodySmall)
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(logoUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Logo",
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Color de Identificación:", style = MaterialTheme.typography.bodyMedium)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 6.dp)
                    ) {
                        presetColors.forEach { hex ->
                            val color = runCatching { Color(android.graphics.Color.parseColor(hex)) }
                                .getOrDefault(MaterialTheme.colorScheme.primary)
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .clickable { selectedColor = hex }
                                    .then(
                                        if (selectedColor == hex) {
                                            Modifier.background(Color.White.copy(alpha = 0.3f), CircleShape)
                                        } else Modifier
                                    )
                            )
                        }
                    }
                } else {
                    // Stores List & Add New Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${stores.size} Sucursales registradas",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = {
                                resetForm()
                                isEditing = true
                            },
                            modifier = Modifier.testTag("new_store_btn")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Nueva")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(stores, key = { it.id }) { store ->
                            val storeColor = runCatching {
                                Color(android.graphics.Color.parseColor(store.colorHex))
                            }.getOrDefault(MaterialTheme.colorScheme.primary)

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("store_item_${store.id}"),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(storeColor.copy(alpha = 0.15f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (store.logoUrl.isNotBlank()) {
                                                AsyncImage(
                                                    model = ImageRequest.Builder(LocalContext.current)
                                                        .data(store.logoUrl)
                                                        .crossfade(true)
                                                        .build(),
                                                    contentDescription = store.name,
                                                    modifier = Modifier.size(38.dp),
                                                    contentScale = ContentScale.Crop
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Default.Storefront,
                                                    contentDescription = null,
                                                    tint = storeColor,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column {
                                            Text(
                                                text = store.name,
                                                fontWeight = FontWeight.SemiBold,
                                                style = MaterialTheme.typography.bodyLarge
                                            )
                                            if (store.address.isNotBlank()) {
                                                Text(
                                                    text = store.address,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            if (store.phone.isNotBlank()) {
                                                Text(
                                                    text = store.phone,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }

                                    Row {
                                        IconButton(
                                            onClick = {
                                                currentStoreId = store.id
                                                name = store.name
                                                address = store.address
                                                phone = store.phone
                                                logoUrl = store.logoUrl
                                                selectedColor = store.colorHex
                                                isEditing = true
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Editar sucursal",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        if (stores.size > 1) {
                                            IconButton(
                                                onClick = { onDeleteStore(store) }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Eliminar sucursal",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (isEditing) {
                Button(
                    onClick = {
                        if (name.isBlank()) {
                            nameError = true
                            return@Button
                        }
                        onSaveStore(
                            Store(
                                id = currentStoreId,
                                name = name.trim(),
                                address = address.trim(),
                                phone = phone.trim(),
                                logoUrl = logoUrl.trim(),
                                colorHex = selectedColor
                            )
                        )
                        resetForm()
                    },
                    modifier = Modifier.testTag("save_store_btn")
                ) {
                    Text("Guardar")
                }
            } else {
                Button(onClick = onDismiss) {
                    Text("Listo")
                }
            }
        },
        dismissButton = {
            if (isEditing) {
                OutlinedButton(onClick = { resetForm() }) {
                    Text("Cancelar")
                }
            }
        }
    )
}
