package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.MovementType
import com.example.data.model.StockMovement
import com.example.data.model.Store
import com.example.ui.components.StoreSelectorBar
import com.example.ui.theme.DangerRed
import com.example.ui.theme.DangerRedContainer
import com.example.ui.theme.InfoBlue
import com.example.ui.theme.InfoBlueContainer
import com.example.ui.theme.ProfitGreen
import com.example.ui.theme.ProfitGreenContainer
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WarningAmberContainer
import com.example.ui.util.Formatters

@Composable
fun KardexScreen(
    stores: List<Store>,
    activeStoreId: Long?,
    movements: List<StockMovement>,
    selectedTypeFilter: MovementType?,
    onSelectStore: (Long?) -> Unit,
    onAddStoreClick: () -> Unit,
    onManageStoresClick: () -> Unit,
    onTypeFilterChange: (MovementType?) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("kardex_screen")
    ) {
        // Multi-Store dynamic selector
        StoreSelectorBar(
            stores = stores,
            activeStoreId = activeStoreId,
            onSelectStore = onSelectStore,
            onAddStoreClick = onAddStoreClick,
            onManageStoresClick = onManageStoresClick
        )

        // Kardex Type Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = selectedTypeFilter == null,
                onClick = { onTypeFilterChange(null) },
                label = { Text("Todos los tipos", style = MaterialTheme.typography.bodySmall) }
            )

            MovementType.values().forEach { type ->
                FilterChip(
                    selected = selectedTypeFilter == type,
                    onClick = {
                        onTypeFilterChange(if (selectedTypeFilter == type) null else type)
                    },
                    label = { Text(type.displayName, style = MaterialTheme.typography.bodySmall) }
                )
            }
        }

        // Summary Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Movimientos de Inventario",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "${movements.size} registros",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Movements List
        if (movements.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No hay movimientos registrados",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Los movimientos de entrada, venta o ajuste se registrarán aquí automáticamente.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(movements, key = { it.id }) { movement ->
                    KardexItemCard(movement = movement)
                }
            }
        }
    }
}

@Composable
fun KardexItemCard(
    movement: StockMovement,
    modifier: Modifier = Modifier
) {
    val isPositive = movement.movementType.isPositive
    val badgeBg = when (movement.movementType) {
        MovementType.ENTRADA -> ProfitGreenContainer
        MovementType.VENTA -> InfoBlueContainer
        MovementType.AJUSTE_POSITIVO -> ProfitGreenContainer
        MovementType.AJUSTE_NEGATIVO -> DangerRedContainer
        MovementType.TRASPASO -> WarningAmberContainer
    }
    val badgeTint = when (movement.movementType) {
        MovementType.ENTRADA -> ProfitGreen
        MovementType.VENTA -> InfoBlue
        MovementType.AJUSTE_POSITIVO -> ProfitGreen
        MovementType.AJUSTE_NEGATIVO -> DangerRed
        MovementType.TRASPASO -> WarningAmber
    }
    val icon = when (movement.movementType) {
        MovementType.ENTRADA -> Icons.Default.ArrowUpward
        MovementType.VENTA -> Icons.Default.PointOfSale
        MovementType.AJUSTE_POSITIVO -> Icons.Default.Add
        MovementType.AJUSTE_NEGATIVO -> Icons.Default.Remove
        MovementType.TRASPASO -> Icons.Default.SwapHoriz
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("kardex_item_${movement.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Movement Icon badge
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(badgeBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = movement.movementType.displayName,
                    tint = badgeTint,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = movement.productName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Icon(
                        Icons.Default.Storefront,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = movement.storeName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(start = 2.dp)
                    )
                    Text(" • ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    Text(
                        text = movement.movementType.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = badgeTint
                    )
                }

                if (movement.reason.isNotBlank()) {
                    Text(
                        text = movement.reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                Text(
                    text = Formatters.dateTime(movement.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            // Quantity and Stock transition
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${if (isPositive) "+" else "-"}${movement.quantity} uds.",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isPositive) ProfitGreen else DangerRed
                )
                Text(
                    text = "Stock: ${movement.previousStock} → ${movement.newStock}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
