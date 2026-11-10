package com.example.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.Store

@Composable
fun StoreSelectorBar(
    stores: List<Store>,
    activeStoreId: Long?,
    onSelectStore: (Long?) -> Unit,
    onAddStoreClick: () -> Unit,
    onManageStoresClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // "Todas las tiendas" Chip
        FilterChip(
            selected = activeStoreId == null,
            onClick = { onSelectStore(null) },
            label = {
                Text(
                    text = "Todas las tiendas",
                    fontWeight = if (activeStoreId == null) FontWeight.Bold else FontWeight.Normal
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Storefront,
                    contentDescription = "Todas las tiendas",
                    modifier = Modifier.size(18.dp)
                )
            },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
            ),
            modifier = Modifier.testTag("store_chip_all")
        )

        // Store Chips
        stores.forEach { store ->
            val isSelected = activeStoreId == store.id
            FilterChip(
                selected = isSelected,
                onClick = { onSelectStore(store.id) },
                label = {
                    Text(
                        text = store.name,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Storefront,
                        contentDescription = store.name,
                        modifier = Modifier.size(18.dp),
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                modifier = Modifier.testTag("store_chip_${store.id}")
            )
        }

        // Add Store Action Button
        OutlinedButton(
            onClick = onAddStoreClick,
            modifier = Modifier.testTag("add_store_button")
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Agregar tienda",
                modifier = Modifier.size(16.dp)
            )
            Text(text = "Sucursal", modifier = Modifier.padding(start = 4.dp))
        }
    }
}
