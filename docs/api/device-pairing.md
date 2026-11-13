# Emparejamiento LAN de dispositivos

Esta guía describe el flujo inicial de emparejamiento. El panel web todavía no genera códigos; mientras se construye, el operador los crea desde la terminal de la PC.

## Requisitos

- Node.js 22.12 o superior.
- Dependencias del servidor instaladas con `npm install`.
- El servidor y la terminal deben utilizar el mismo directorio de datos (`INVENTORY_DATA_DIR`, si se configuró).

## Generar un código

Desde la carpeta `server`:

```powershell
npm run pairing-code
```

El comando crea un código criptográficamente aleatorio de 10 bytes (20 caracteres hexadecimales), almacena únicamente su hash SHA-256 y muestra el código una sola vez. El código vence a los cinco minutos. Los códigos vencidos o consumidos se limpian al generar uno nuevo.

No publiques el código ni lo compartas fuera de tu red privada de confianza.

## Canjear desde Android

Android enviará una petición JSON a:

`POST http://<IP-DE-LA-PC>:8787/api/v1/pairing/exchange`

Cuerpo:

```json
{
  "pairingCode": "codigo-de-20-caracteres-hex",
  "deviceId": "UUID-de-esta-instalacion",
  "deviceName": "Mi Android"
}
```

Respuesta exitosa:

```json
{
  "deviceId": "UUID-de-esta-instalacion",
  "accessToken": "token-opaco",
  "tokenType": "Bearer"
}
```

El token se devuelve una sola vez. Android debe guardarlo en almacenamiento seguro del dispositivo y enviarlo posteriormente como `Authorization: Bearer <token>`. No debe guardarlo en Room ni registrarlo en logs.

## Reglas de seguridad

- El código se consume dentro de una transacción SQLite; dos canjes concurrentes no pueden utilizarlo dos veces.
- La base de datos almacena el hash del código y el hash del token.
- Cada canje exitoso crea o renueva la autorización del `deviceId`.
- La revocación de dispositivos y el middleware Bearer para rutas privadas quedan pendientes antes de exponer endpoints de datos.
- El servidor está pensado para una red Wi-Fi privada; no debe publicarse directamente en Internet.
- No se debe implementar el flujo de pairing desde HTTP público sin límites de intentos y protección adicional.

## Estado

Implementado: generación del código y endpoint de canje.

Pendiente: interfaz de administración en PC, limitación de intentos, autenticación de endpoints privados, almacenamiento seguro Android y prueba real PC–Android.
