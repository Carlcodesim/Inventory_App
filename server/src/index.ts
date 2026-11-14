import express from "express";
import os from "node:os";
import { createHash, randomBytes, randomUUID } from "node:crypto";
import { databaseFilePath, db } from "./database.js";

const app = express();
const port = Number(process.env.PORT ?? 8787);
const host = process.env.HOST ?? "0.0.0.0";

app.disable("x-powered-by");
app.use(express.json({ limit: "256kb" }));

const pairingAttempts = new Map<string, { count: number; windowStartedAt: number }>();
const pairingWindowMs = 10 * 60_000;
const pairingMaxAttempts = 10;

function limitPairingAttempts(request: express.Request, response: express.Response, next: express.NextFunction) {
  const now = Date.now();
  const ip = request.ip || request.socket.remoteAddress || "unknown";

  for (const [key, value] of pairingAttempts) {
    if (now - value.windowStartedAt >= pairingWindowMs) pairingAttempts.delete(key);
  }

  const current = pairingAttempts.get(ip);
  if (!current || now - current.windowStartedAt >= pairingWindowMs) {
    pairingAttempts.set(ip, { count: 1, windowStartedAt: now });
    next();
    return;
  }

  if (current.count >= pairingMaxAttempts) {
    response.status(429).json({ error: "too_many_attempts", message: "Demasiados intentos. Espera diez minutos y vuelve a intentarlo." });
    return;
  }

  current.count += 1;
  next();
}

app.get("/api/v1/health", (_request, response) => {
  const database = db.prepare("SELECT 1 AS ok").get() as { ok: number };
  response.json({
    status: "ok",
    service: "inventory-app-server",
    apiVersion: "1.0.0",
    database: database.ok === 1 ? "connected" : "unavailable",
    timestamp: new Date().toISOString()
  });
});

app.get("/api/v1/server-info", (_request, response) => {
  const interfaces = Object.values(os.networkInterfaces()).flat()
    .filter((entry): entry is NonNullable<typeof entry> => Boolean(entry))
    .filter((entry) => entry.family === "IPv4" && !entry.internal)
    .map((entry) => entry.address);

  response.json({
    port,
    addresses: interfaces.map((address) => `http://${address}:${port}`),
    databaseConfigured: Boolean(databaseFilePath)
  });
});

app.post("/api/v1/pairing/exchange", limitPairingAttempts, (request, response) => {
  const { pairingCode, deviceId, deviceName } = request.body ?? {};
  const normalizedCode = typeof pairingCode === "string" ? pairingCode.trim().toLowerCase() : "";
  const normalizedDeviceId = typeof deviceId === "string" ? deviceId.trim().toLowerCase() : "";
  const normalizedDeviceName = typeof deviceName === "string" ? deviceName.trim() : "";

  if (!/^[a-f0-9]{20}$/.test(normalizedCode)
    || !/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/.test(normalizedDeviceId)
    || normalizedDeviceName.length < 1
    || normalizedDeviceName.length > 80) {
    response.status(400).json({ error: "invalid_request", message: "Datos de emparejamiento inválidos." });
    return;
  }

  const codeHash = createHash("sha256").update(normalizedCode).digest("hex");
  const token = randomBytes(32).toString("base64url");
  const tokenHash = createHash("sha256").update(token).digest("hex");
  const now = Date.now();

  const exchange = db.transaction(() => {
    const code = db.prepare(
      "SELECT code_hash FROM pairing_codes WHERE code_hash = ? AND consumed_at IS NULL AND expires_at > ?"
    ).get(codeHash, now);

    if (!code) return false;

    const consumed = db.prepare(
      "UPDATE pairing_codes SET consumed_at = ? WHERE code_hash = ? AND consumed_at IS NULL AND expires_at > ?"
    ).run(now, codeHash, now);

    if (consumed.changes !== 1) return false;

    db.prepare(`
      INSERT INTO devices (device_id, device_name, token_hash, created_at, last_seen_at, revoked_at)
      VALUES (?, ?, ?, ?, ?, NULL)
      ON CONFLICT(device_id) DO UPDATE SET
        device_name = excluded.device_name,
        token_hash = excluded.token_hash,
        last_seen_at = excluded.last_seen_at,
        revoked_at = NULL
    `).run(normalizedDeviceId, normalizedDeviceName, tokenHash, now, now);

    return true;
  });

  if (!exchange()) {
    response.status(401).json({ error: "pairing_code_rejected", message: "El código es inválido, venció o ya fue utilizado." });
    return;
  }

  response.json({ deviceId: normalizedDeviceId, accessToken: token, tokenType: "Bearer" });
});


type AuthenticatedRequest = express.Request & { deviceId?: string };

function requireDevice(request: AuthenticatedRequest, response: express.Response, next: express.NextFunction) {
  const authorization = request.header("authorization") ?? "";
  const match = /^Bearer ([A-Za-z0-9_-]{40,})$/.exec(authorization);
  if (!match) {
    response.status(401).json({ error: "unauthorized", message: "Se requiere un token de dispositivo válido." });
    return;
  }

  const tokenHash = createHash("sha256").update(match[1]).digest("hex");
  const device = db.prepare(
    "SELECT device_id FROM devices WHERE token_hash = ? AND revoked_at IS NULL"
  ).get(tokenHash) as { device_id: string } | undefined;

  if (!device) {
    response.status(401).json({ error: "invalid_token", message: "El token no es válido o fue revocado." });
    return;
  }

  request.deviceId = device.device_id;
  db.prepare("UPDATE devices SET last_seen_at = ? WHERE device_id = ?").run(Date.now(), device.device_id);
  next();
}

function isText(value: unknown, maxLength: number, minLength = 0): value is string {
  return typeof value === "string" && value.trim().length >= minLength && value.trim().length <= maxLength;
}

function isNonNegativeInteger(value: unknown): value is number {
  return Number.isSafeInteger(value) && Number(value) >= 0;
}

function logChange(entityType: string, syncId: string, operation: "upsert" | "delete", payload: unknown) {
  db.prepare(`
    INSERT INTO change_log (entity_type, entity_sync_id, operation, payload_json, created_at)
    VALUES (?, ?, ?, ?, ?)
  `).run(entityType, syncId, operation, JSON.stringify(payload), Date.now());
}

app.use("/api/v1", requireDevice);


app.get("/api/v1/bootstrap", (_request, response) => {
  const stores = db.prepare("SELECT sync_id AS syncId, name, logo_url AS logoUrl, address, phone, color_hex AS colorHex, version FROM stores WHERE deleted_at IS NULL ORDER BY name").all();
  const catalog = db.prepare("SELECT sync_id AS syncId, name, type, version FROM catalog_items WHERE deleted_at IS NULL ORDER BY type, name").all();
  const products = db.prepare(`SELECT sync_id AS syncId, store_sync_id AS storeSyncId, name, category, supplier,
    cost_price_minor AS costPriceMinor, sale_price_minor AS salePriceMinor, stock,
    min_stock_alert AS minStockAlert, photo_url AS photoUrl, sale_link AS saleLink, version
    FROM products WHERE deleted_at IS NULL ORDER BY name`).all();
  const cursor = db.prepare("SELECT COALESCE(MAX(cursor), 0) AS cursor FROM change_log").get() as { cursor: number };
  response.json({ stores, catalog, products, cursor: cursor.cursor });
});

app.get("/api/v1/sync/changes", (request, response) => {
  const cursorValue = request.query.cursor;
  const limitValue = request.query.limit ?? "200";
  const cursor = typeof cursorValue === "string" && /^\d+$/.test(cursorValue) ? Number(cursorValue) : NaN;
  const limit = typeof limitValue === "string" && /^\d+$/.test(limitValue) ? Number(limitValue) : NaN;
  if (!Number.isSafeInteger(cursor) || cursor < 0 || !Number.isSafeInteger(limit) || limit < 1 || limit > 500) {
    response.status(400).json({ error: "invalid_sync_cursor", message: "El cursor debe ser un entero no negativo y el límite debe estar entre 1 y 500." });
    return;
  }
  const changes = db.prepare(`
    SELECT cursor, entity_type AS entityType, entity_sync_id AS entitySyncId,
      operation, payload_json AS payloadJson, created_at AS createdAt
    FROM change_log WHERE cursor > ? ORDER BY cursor ASC LIMIT ?
  `).all(cursor, limit) as Array<{ cursor: number; entityType: string; entitySyncId: string; operation: string; payloadJson: string; createdAt: number }>;
  const items = changes.map(({ payloadJson, ...change }) => ({ ...change, payload: JSON.parse(payloadJson) as unknown }));
  const nextCursor = items.length ? items[items.length - 1].cursor : cursor;
  response.json({ items, nextCursor, hasMore: items.length === limit });
});

app.get("/api/v1/stores", (_request, response) => {
  const rows = db.prepare(
    "SELECT sync_id AS syncId, name, logo_url AS logoUrl, address, phone, color_hex AS colorHex, version, created_at AS createdAt, updated_at AS updatedAt FROM stores WHERE deleted_at IS NULL ORDER BY name"
  ).all();
  response.json({ items: rows });
});

app.post("/api/v1/stores", (request, response) => {
  const { name, logoUrl = "", address = "", phone = "", colorHex = "#0284C7" } = request.body ?? {};
  if (!isText(name, 120, 1) || !isText(logoUrl, 2048) || !isText(address, 240)
    || !isText(phone, 40) || !isText(colorHex, 16)) {
    response.status(400).json({ error: "invalid_store", message: "Revisa los campos de la sucursal." });
    return;
  }

  const now = Date.now();
  const syncId = randomUUID();
  db.prepare(`
    INSERT INTO stores (sync_id, name, logo_url, address, phone, color_hex, created_at, updated_at)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
  `).run(syncId, name.trim(), logoUrl.trim(), address.trim(), phone.trim(), colorHex.trim(), now, now);

  const item = db.prepare(
    "SELECT sync_id AS syncId, name, logo_url AS logoUrl, address, phone, color_hex AS colorHex, version, created_at AS createdAt, updated_at AS updatedAt FROM stores WHERE sync_id = ?"
  ).get(syncId);
  logChange("store", syncId, "upsert", item);
  response.status(201).json({ item });
});

app.get("/api/v1/catalog", (request, response) => {
  const type = request.query.type;
  if (type !== undefined && type !== "CATEGORY" && type !== "SUPPLIER") {
    response.status(400).json({ error: "invalid_type", message: "El tipo debe ser CATEGORY o SUPPLIER." });
    return;
  }
  const rows = type
    ? db.prepare("SELECT sync_id AS syncId, name, type, version, created_at AS createdAt, updated_at AS updatedAt FROM catalog_items WHERE deleted_at IS NULL AND type = ? ORDER BY name").all(type)
    : db.prepare("SELECT sync_id AS syncId, name, type, version, created_at AS createdAt, updated_at AS updatedAt FROM catalog_items WHERE deleted_at IS NULL ORDER BY type, name").all();
  response.json({ items: rows });
});

app.post("/api/v1/catalog", (request, response) => {
  const { name, type } = request.body ?? {};
  if (!isText(name, 100, 1) || (type !== "CATEGORY" && type !== "SUPPLIER")) {
    response.status(400).json({ error: "invalid_catalog_item", message: "Nombre o tipo de catálogo inválido." });
    return;
  }

  const normalizedName = name.trim();
  const existing = db.prepare(
    "SELECT sync_id FROM catalog_items WHERE type = ? AND name = ?"
  ).get(type, normalizedName) as { sync_id: string } | undefined;
  const now = Date.now();
  const syncId = existing?.sync_id ?? randomUUID();

  db.prepare(`
    INSERT INTO catalog_items (sync_id, name, type, created_at, updated_at, version, deleted_at)
    VALUES (?, ?, ?, ?, ?, 1, NULL)
    ON CONFLICT(type, name) DO UPDATE SET
      updated_at = excluded.updated_at,
      version = catalog_items.version + 1,
      deleted_at = NULL
  `).run(syncId, normalizedName, type, now, now);

  const item = db.prepare(
    "SELECT sync_id AS syncId, name, type, version, created_at AS createdAt, updated_at AS updatedAt FROM catalog_items WHERE sync_id = ?"
  ).get(syncId);
  logChange("catalog", syncId, "upsert", item);
  response.status(existing ? 200 : 201).json({ item });
});

app.get("/api/v1/products", (request, response) => {
  const storeSyncId = request.query.storeSyncId;
  const rows = storeSyncId
    ? db.prepare(`SELECT sync_id AS syncId, store_sync_id AS storeSyncId, name, category, supplier,
        cost_price_minor AS costPriceMinor, sale_price_minor AS salePriceMinor, stock,
        min_stock_alert AS minStockAlert, photo_url AS photoUrl, sale_link AS saleLink,
        version, created_at AS createdAt, updated_at AS updatedAt
      FROM products WHERE deleted_at IS NULL AND store_sync_id = ? ORDER BY name`).all(String(storeSyncId))
    : db.prepare(`SELECT sync_id AS syncId, store_sync_id AS storeSyncId, name, category, supplier,
        cost_price_minor AS costPriceMinor, sale_price_minor AS salePriceMinor, stock,
        min_stock_alert AS minStockAlert, photo_url AS photoUrl, sale_link AS saleLink,
        version, created_at AS createdAt, updated_at AS updatedAt
      FROM products WHERE deleted_at IS NULL ORDER BY name`).all();
  response.json({ items: rows });
});

app.post("/api/v1/products", (request, response) => {
  const body = request.body ?? {};
  const { storeSyncId, name, category, supplier = "", costPriceMinor, salePriceMinor,
    stock = 0, minStockAlert = 5, photoUrl = "", saleLink = "" } = body;

  if (!isText(storeSyncId, 64, 1) || !isText(name, 160, 1) || !isText(category, 100, 1)
    || !isText(supplier, 100) || !isNonNegativeInteger(costPriceMinor)
    || !isNonNegativeInteger(salePriceMinor) || !isNonNegativeInteger(stock)
    || !isNonNegativeInteger(minStockAlert) || !isText(photoUrl, 2048) || !isText(saleLink, 2048)) {
    response.status(400).json({ error: "invalid_product", message: "Revisa los datos del producto. Los importes deben enviarse en centavos enteros." });
    return;
  }

  const store = db.prepare("SELECT sync_id FROM stores WHERE sync_id = ? AND deleted_at IS NULL").get(storeSyncId);
  if (!store) {
    response.status(404).json({ error: "store_not_found", message: "La sucursal indicada no existe." });
    return;
  }

  const now = Date.now();
  const syncId = randomUUID();
  db.prepare(`
    INSERT INTO products (
      sync_id, store_sync_id, name, category, supplier, cost_price_minor, sale_price_minor,
      stock, min_stock_alert, photo_url, sale_link, created_at, updated_at
    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
  `).run(syncId, storeSyncId, name.trim(), category.trim(), supplier.trim(), costPriceMinor,
    salePriceMinor, stock, minStockAlert, photoUrl.trim(), saleLink.trim(), now, now);

  const item = db.prepare(`SELECT sync_id AS syncId, store_sync_id AS storeSyncId, name, category,
    supplier, cost_price_minor AS costPriceMinor, sale_price_minor AS salePriceMinor, stock,
    min_stock_alert AS minStockAlert, photo_url AS photoUrl, sale_link AS saleLink,
    version, created_at AS createdAt, updated_at AS updatedAt FROM products WHERE sync_id = ?`).get(syncId);
  logChange("product", syncId, "upsert", item);
  response.status(201).json({ item });
});

app.listen(port, host, () => {
  console.log(`Inventory_App API listening on http://localhost:${port}`);
  console.log("LAN access is enabled; use only on a trusted private network.");
});

function shutdown() {
  db.close();
  process.exit(0);
}

process.on("SIGINT", shutdown);
process.on("SIGTERM", shutdown);
