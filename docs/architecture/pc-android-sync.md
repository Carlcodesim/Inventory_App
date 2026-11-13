# Inventory_App — arquitectura PC + Android

## Objetivo

Añadir una aplicación web de escritorio servida desde la PC y conectarla con la aplicación Android existente. Ambos clientes trabajarán sobre el mismo inventario lógico. La PC alojará inicialmente la API y la base de datos; Android conservará Room como almacenamiento local y caché para permitir consultas y operaciones pendientes sin conexión.

## Estado actual verificado

- Android está implementado en Kotlin, Jetpack Compose y Room.
- Room contiene `stores`, `products`, `sales`, `stock_movements` y `catalog_items`.
- La app ya incluye Retrofit, Moshi y OkHttp como dependencias, pero todavía no consume una API propia.
- Las entidades usan IDs numéricos autogenerados localmente. No se deben sincronizar esos IDs como identidad global sin una estrategia de mapeo.
- La base de datos Room actual es versión 2.
- La aplicación no tiene todavía un backend ni un cliente web.

## Arquitectura objetivo (primera entrega LAN)

```text
                     misma red Wi-Fi / LAN
     ┌──────────────────────────┐
     │ PC Windows               │
     │                          │
     │  Navegador               │
     │  http://localhost:PORT   │
     │       │                  │
     │  Web UI                  │
     │       │ HTTP             │
     │  API local               │
     │       │                  │
     │  SQLite central          │
     └───────────┬──────────────┘
                 │ HTTP + token de dispositivo
                 │
     ┌───────────▼──────────────┐
     │ Android                  │
     │ Jetpack Compose          │
     │ Room (caché/outbox)      │
     │ Sync Worker              │
     └──────────────────────────┘
```

La API escuchará en la interfaz LAN de la PC para que Android pueda alcanzarla. La interfaz web se servirá desde la misma API. En el primer despliegue solo será accesible dentro de la red local; no se expondrá directamente a Internet.

## Decisiones técnicas

1. **Backend:** Node.js LTS + TypeScript + Express. Se mantiene una tecnología ligera y familiar con el ecosistema JavaScript del proyecto.
2. **Persistencia central:** SQLite en la PC, con migraciones versionadas. La base central será la autoridad para los IDs globales.
3. **Web:** React + TypeScript, empaquetada como recursos estáticos servidos por Express. Debe ser usable en Chrome y Edge.
4. **Android:** Kotlin + Room continúa como cliente móvil. Retrofit/Moshi ya están presentes y se reutilizarán.
5. **Transporte:** REST/JSON sobre HTTP dentro de LAN. HTTPS y acceso remoto se tratarán como una etapa posterior, no se fingirá que HTTP local ofrece seguridad de Internet.
6. **Identidad:** cada instalación Android tendrá un `deviceId` UUID. Los registros sincronizables tendrán un UUID global `syncId`, sin reemplazar de golpe los IDs locales usados por Room.
7. **Idempotencia:** cada operación enviada llevará un `operationId` UUID. Reintentar una operación no debe crear una segunda venta ni duplicar un movimiento.
8. **Conflictos:** cada entidad tendrá `updatedAt` y `version`. No se aplicará silenciosamente “última escritura gana” a ventas o movimientos. Las operaciones de inventario serán comandos transaccionales validados por el servidor.
9. **Offline:** Android guardará operaciones pendientes en una tabla outbox Room y las enviará al recuperar conexión. Los cambios confirmados por el servidor se aplicarán a Room.
10. **Respaldo:** la PC ofrecerá copia de seguridad de la base central. No se borrarán datos locales como parte de la activación de sincronización.

## Flujo de conexión inicial

1. El usuario abre el servidor en la PC y ve su dirección LAN y un código temporal de emparejamiento.
2. En Android selecciona “Conectar con PC” y escanea el QR o introduce el código.
3. El servidor valida el código, registra el dispositivo y emite un token revocable.
4. Android guarda la dirección del servidor y el token en almacenamiento seguro.
5. Android consulta el estado de sincronización y realiza la primera descarga.
6. Desde ese momento sincroniza cambios confirmados y operaciones pendientes.

No se debe usar un QR con el token permanente en texto visible ni guardar contraseñas en preferencias simples.

## API inicial (contrato v1)

- `GET /api/v1/health` — estado y versión del servidor.
- `POST /api/v1/pairing/exchange` — intercambiar código temporal por credenciales de dispositivo.
- `GET /api/v1/bootstrap` — obtener sucursales, productos, catálogos y cursor inicial.
- `GET /api/v1/sync/changes?cursor=...` — descargar cambios confirmados desde un cursor.
- `POST /api/v1/sync/operations` — enviar un lote de operaciones con IDs idempotentes.
- `GET /api/v1/stores`, `/products`, `/categories`, `/suppliers`, `/sales`, `/stock-movements` — lectura y administración autenticada para la web.

Todas las rutas salvo `health` y el intercambio de emparejamiento requieren token de dispositivo o sesión web. Los lotes tendrán límites de tamaño y validación estricta.

## Modelo de sincronización

- La PC asigna el ID central y conserva `syncId` único por registro.
- Android mantiene el ID Room y una tabla de mapeo `syncId <-> localId`.
- El servidor registra un log ordenado de cambios con cursor monotónico.
- Un comando de venta incluye `operationId`, `storeSyncId`, `productSyncId`, cantidad y nota. El servidor vuelve a validar sucursal, existencia y stock dentro de una transacción.
- El servidor genera la venta, descuenta existencias y crea el movimiento de Kardex de forma atómica.
- Una operación repetida devuelve el resultado original en lugar de ejecutarse dos veces.
- Si una edición de producto llega con una versión antigua, el servidor responde conflicto con el estado actual. No se sobrescribe a ciegas.
- Los borrados se representan mediante tombstones para que se propaguen a clientes desconectados.

## Seguridad y límites de la primera versión

- Emparejamiento mediante código de un solo uso y corta duración.
- Tokens por dispositivo, revocables y almacenados de forma segura en Android.
- Firewall de Windows: permitir el puerto solo en redes privadas.
- El servidor debe mostrar una advertencia si la red está configurada como pública.
- No abrir puertos del router ni publicar el servicio en Internet.
- La PC debe permanecer encendida y el servidor ejecutándose para que Android sincronice en tiempo real.
- Fuera de la LAN no habrá sincronización hasta diseñar acceso remoto seguro o un backend alojado.

## Fases de implementación

### Fase 0 — contrato y auditoría (actual)
- Documentar arquitectura, entidades, API y reglas de consistencia.
- Identificar campos/relaciones y riesgos de migración.
- Mantener la aplicación Android actual funcionando sin cambios.

### Fase 1 — backend local
- Crear proyecto TypeScript/Express.
- Configurar SQLite y migraciones.
- Implementar health, configuración, emparejamiento y autenticación.
- Añadir pruebas de API y persistencia.

### Fase 2 — panel web
- Login local, resumen, sucursales, inventario, catálogos y ventas.
- Servir web desde la API.
- Validaciones y accesibilidad.

### Fase 3 — Android conectado
- Añadir pantalla de conexión.
- Incorporar IDs de sincronización y outbox con migración Room no destructiva.
- Implementar bootstrap, pull y push idempotente.
- Mantener modo local si no hay servidor.

### Fase 4 — validación de extremo a extremo
- PC crea producto -> aparece en Android.
- Android crea producto -> aparece en PC.
- Venta desde ambos dispositivos -> stock y Kardex consistentes.
- Desconexión, reconexión, reintentos y duplicados.
- Respaldo y restauración.

### Fase 5 — distribución
- Instalador/paquete para Windows que arranque el servidor local.
- Acceso desde navegador y opción de iniciar con Windows.
- Guía de conexión del móvil.

## Criterios de aceptación

- No perder ni sobrescribir datos actuales de Room al actualizar.
- No duplicar ventas al reintentar una sincronización.
- No permitir stock negativo por concurrencia.
- PC y Android convergen al mismo estado tras reconectarse.
- Un fallo de red no bloquea el uso local ni descarta operaciones pendientes.
- Cada migración tiene prueba y no utiliza destructive migration.
