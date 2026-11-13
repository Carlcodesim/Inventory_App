# Formato de importes y reglas de identidad

## Importes monetarios

El servidor almacena dinero como **enteros en unidades menores**, nunca como números de punto flotante.

- En la República Dominicana, los importes se expresan en centavos de peso dominicano (DOP).
- Ejemplo: RD$125.50 se transmite y almacena como `12550`.
- Ejemplo: RD$21.95 se transmite y almacena como `2195`.
- Los campos monetarios de la base central terminan en `_minor`: `cost_price_minor`, `sale_price_minor`, `unit_cost_minor`, `unit_price_minor`, `total_amount_minor` y `net_profit_minor`.
- La API debe validar que estos campos sean enteros seguros y no negativos, salvo el beneficio neto, que puede ser negativo si una operación lo permite.
- La interfaz convierte a formato decimal solo para mostrar; nunca debe hacer cálculos financieros con `float`/punto flotante.

Para mostrar un valor DOP, dividir el entero entre 100 únicamente en la capa de presentación y formatearlo con `Intl.NumberFormat`. Al enviar una operación, convertir el importe decimal validado a centavos antes de serializarlo.

## Identificadores

- `id`: clave local interna de SQLite; no se sincroniza entre dispositivos.
- `sync_id`: UUID estable asignado por el servidor a cada entidad sincronizable.
- `device_id`: UUID único de cada instalación/dispositivo.
- `operation_id`: UUID idempotente generado por el cliente para cada operación pendiente; los reintentos conservan el mismo valor.
- `cursor`: entero monotónico del registro de cambios del servidor.

## Reglas de consistencia

- Ventas, ajustes de stock y movimientos relacionados se procesan en una transacción SQLite.
- El servidor verifica stock suficiente y rechaza operaciones que produzcan inventario negativo.
- Los reintentos con el mismo `operation_id` devuelven el recibo original y no repiten efectos.
- Las ventas y movimientos no se resuelven con política de «última escritura gana».
