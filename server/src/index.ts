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
  const rawToken = match?.[1];
  if (!rawToken) {
    response.status(401).json({ error: "unauthorized", message: "Se requiere un token de dispositivo válido." });
    return;
  }

  const tokenHash = createHash("sha256").update(rawToken).digest("hex");
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
  const nextCursor = items.at(-1)?.cursor ?? cursor;
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



function getStore(syncId: string) {
  return db.prepare("SELECT sync_id AS syncId, name, logo_url AS logoUrl, address, phone, color_hex AS colorHex, version, created_at AS createdAt, updated_at AS updatedAt FROM stores WHERE sync_id = ? AND deleted_at IS NULL").get(syncId) as Record<string, unknown> | undefined;
}

function getCatalogItem(syncId: string) {
  return db.prepare("SELECT sync_id AS syncId, name, type, version, created_at AS createdAt, updated_at AS updatedAt FROM catalog_items WHERE sync_id = ? AND deleted_at IS NULL").get(syncId) as Record<string, unknown> | undefined;
}

function getProduct(syncId: string) {
  return db.prepare("SELECT sync_id AS syncId, store_sync_id AS storeSyncId, name, category, supplier, cost_price_minor AS costPriceMinor, sale_price_minor AS salePriceMinor, stock, min_stock_alert AS minStockAlert, photo_url AS photoUrl, sale_link AS saleLink, version, created_at AS createdAt, updated_at AS updatedAt FROM products WHERE sync_id = ? AND deleted_at IS NULL").get(syncId) as Record<string, unknown> | undefined;
}

app.patch("/api/v1/stores/:syncId", (request, response) => {
  const syncId = request.params.syncId;
  if (!isUuid(syncId)) {
    response.status(400).json({ error: "invalid_sync_id", message: "El syncId no es válido." });
    return;
  }
  const current = getStore(syncId);
  if (!current) {
    response.status(404).json({ error: "store_not_found", message: "La sucursal no existe o fue eliminada." });
    return;
  }
  const body = request.body ?? {};
  const fields = [
    ["name", "name", 120, true],
    ["logoUrl", "logo_url", 2048, false],
    ["address", "address", 240, false],
    ["phone", "phone", 40, false],
    ["colorHex", "color_hex", 16, false]
  ] as const;
  const updates: string[] = [];
  const values: unknown[] = [];
  for (const [input, column, max, required] of fields) {
    if (Object.hasOwn(body, input)) {
      if (!isText(body[input], max, required ? 1 : 0)) {
        response.status(400).json({ error: "invalid_store", message: "Uno o más campos de la sucursal son inválidos." });
        return;
      }
      updates.push(column + " = ?");
      values.push(body[input].trim());
    }
  }
  if (updates.length === 0) {
    response.status(400).json({ error: "empty_update", message: "Indica al menos un campo para actualizar." });
    return;
  }
  const now = Date.now();
  updates.push("updated_at = ?", "version = version + 1");
  values.push(now, syncId);
  db.prepare("UPDATE stores SET " + updates.join(", ") + " WHERE sync_id = ? AND deleted_at IS NULL").run(...values);
  const item = getStore(syncId);
  logChange("store", syncId, "upsert", item);
  response.json({ item });
});

app.delete("/api/v1/stores/:syncId", (request, response) => {
  const syncId = request.params.syncId;
  if (!isUuid(syncId)) {
    response.status(400).json({ error: "invalid_sync_id", message: "El syncId no es válido." });
    return;
  }
  const current = getStore(syncId);
  if (!current) {
    response.status(404).json({ error: "store_not_found", message: "La sucursal no existe o ya fue eliminada." });
    return;
  }
  const activeProducts = db.prepare("SELECT COUNT(*) AS total FROM products WHERE store_sync_id = ? AND deleted_at IS NULL").get(syncId) as { total: number };
  if (activeProducts.total > 0) {
    response.status(409).json({ error: "store_has_products", message: "Primero elimina o traslada los productos activos de esta sucursal." });
    return;
  }
  const now = Date.now();
  db.prepare("UPDATE stores SET deleted_at = ?, updated_at = ?, version = version + 1 WHERE sync_id = ? AND deleted_at IS NULL").run(now, now, syncId);
  logChange("store", syncId, "delete", { syncId, deletedAt: now, version: Number(current.version) + 1 });
  response.json({ deleted: true, syncId, deletedAt: now });
});

app.patch("/api/v1/catalog/:syncId", (request, response) => {
  const syncId = request.params.syncId;
  if (!isUuid(syncId)) {
    response.status(400).json({ error: "invalid_sync_id", message: "El syncId no es válido." });
    return;
  }
  const current = getCatalogItem(syncId) as { syncId: string; name: string; type: string; version: number } | undefined;
  if (!current) {
    response.status(404).json({ error: "catalog_item_not_found", message: "El elemento no existe o fue eliminado." });
    return;
  }
  const name = request.body?.name;
  if (!isText(name, 100, 1)) {
    response.status(400).json({ error: "invalid_catalog_item", message: "El nombre debe contener entre 1 y 100 caracteres." });
    return;
  }
  const normalizedName = name.trim();
  const conflict = db.prepare("SELECT sync_id FROM catalog_items WHERE type = ? AND name = ? AND sync_id <> ?").get(current.type, normalizedName, syncId);
  if (conflict) {
    response.status(409).json({ error: "catalog_name_conflict", message: "Ya existe un elemento con ese nombre y tipo." });
    return;
  }
  const now = Date.now();
  db.prepare("UPDATE catalog_items SET name = ?, updated_at = ?, version = version + 1 WHERE sync_id = ? AND deleted_at IS NULL").run(normalizedName, now, syncId);
  const item = getCatalogItem(syncId);
  logChange("catalog", syncId, "upsert", item);
  response.json({ item });
});

app.delete("/api/v1/catalog/:syncId", (request, response) => {
  const syncId = request.params.syncId;
  if (!isUuid(syncId)) {
    response.status(400).json({ error: "invalid_sync_id", message: "El syncId no es válido." });
    return;
  }
  const current = getCatalogItem(syncId) as { syncId: string; name: string; type: string; version: number } | undefined;
  if (!current) {
    response.status(404).json({ error: "catalog_item_not_found", message: "El elemento no existe o ya fue eliminado." });
    return;
  }
  const column = current.type === "CATEGORY" ? "category" : "supplier";
  const references = db.prepare("SELECT COUNT(*) AS total FROM products WHERE deleted_at IS NULL AND " + column + " = ?").get(current.name) as { total: number };
  if (references.total > 0) {
    response.status(409).json({ error: "catalog_item_in_use", message: "No se puede eliminar: hay productos activos que utilizan este elemento." });
    return;
  }
  const now = Date.now();
  db.prepare("UPDATE catalog_items SET deleted_at = ?, updated_at = ?, version = version + 1 WHERE sync_id = ? AND deleted_at IS NULL").run(now, now, syncId);
  logChange("catalog", syncId, "delete", { syncId, type: current.type, deletedAt: now, version: current.version + 1 });
  response.json({ deleted: true, syncId, deletedAt: now });
});

app.patch("/api/v1/products/:syncId", (request, response) => {
  const syncId = request.params.syncId;
  if (!isUuid(syncId)) {
    response.status(400).json({ error: "invalid_sync_id", message: "El syncId no es válido." });
    return;
  }
  const current = getProduct(syncId);
  if (!current) {
    response.status(404).json({ error: "product_not_found", message: "El producto no existe o fue eliminado." });
    return;
  }
  const body = request.body ?? {};
  const fields = [
    ["name", "name", 160, true],
    ["category", "category", 100, true],
    ["supplier", "supplier", 100, false],
    ["costPriceMinor", "cost_price_minor", 0, false],
    ["salePriceMinor", "sale_price_minor", 0, false],
    ["minStockAlert", "min_stock_alert", 0, false],
    ["photoUrl", "photo_url", 2048, false],
    ["saleLink", "sale_link", 2048, false]
  ] as const;
  const updates: string[] = [];
  const values: unknown[] = [];
  for (const [input, column, max, required] of fields) {
    if (Object.hasOwn(body, input)) {
      const value = body[input];
      if (input === "costPriceMinor" || input === "salePriceMinor" || input === "minStockAlert") {
        if (!isNonNegativeInteger(value)) {
          response.status(400).json({ error: "invalid_product", message: "Los importes y alertas deben ser enteros no negativos." });
          return;
        }
        updates.push(column + " = ?");
        values.push(value);
      } else {
        if (!isText(value, max, required ? 1 : 0)) {
          response.status(400).json({ error: "invalid_product", message: "Uno o más campos del producto son inválidos." });
          return;
        }
        updates.push(column + " = ?");
        values.push(value.trim());
      }
    }
  }
  if (updates.length === 0) {
    response.status(400).json({ error: "empty_update", message: "Indica al menos un campo para actualizar." });
    return;
  }
  const now = Date.now();
  updates.push("updated_at = ?", "version = version + 1");
  values.push(now, syncId);
  db.prepare("UPDATE products SET " + updates.join(", ") + " WHERE sync_id = ? AND deleted_at IS NULL").run(...values);
  const item = getProduct(syncId);
  logChange("product", syncId, "upsert", item);
  response.json({ item });
});

app.delete("/api/v1/products/:syncId", (request, response) => {
  const syncId = request.params.syncId;
  if (!isUuid(syncId)) {
    response.status(400).json({ error: "invalid_sync_id", message: "El syncId no es válido." });
    return;
  }
  const current = getProduct(syncId) as { syncId: string; stock: number; version: number } | undefined;
  if (!current) {
    response.status(404).json({ error: "product_not_found", message: "El producto no existe o ya fue eliminado." });
    return;
  }
  if (current.stock !== 0) {
    response.status(409).json({ error: "product_has_stock", message: "No se puede eliminar un producto con existencias. Ajusta el inventario a cero primero." });
    return;
  }
  const now = Date.now();
  db.prepare("UPDATE products SET deleted_at = ?, updated_at = ?, version = version + 1 WHERE sync_id = ? AND deleted_at IS NULL").run(now, now, syncId);
  logChange("product", syncId, "delete", { syncId, deletedAt: now, version: current.version + 1 });
  response.json({ deleted: true, syncId, deletedAt: now });
});


type InventoryOperation = {
  operationId: string;
  type: "sale.create" | "stock.adjust";
  payload: Record<string, unknown>;
};

class OperationError extends Error {
  constructor(public status: number, public code: string, message: string) {
    super(message);
  }
}

function isUuid(value: unknown): value is string {
  return typeof value === "string"
    && /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value);
}

function processInventoryOperation(deviceId: string, operation: InventoryOperation) {
  const execute = db.transaction(() => {
    const prior = db.prepare(
      "SELECT device_id AS deviceId, response_json AS responseJson FROM operation_receipts WHERE operation_id = ?"
    ).get(operation.operationId) as { deviceId: string; responseJson: string } | undefined;

    if (prior) {
      if (prior.deviceId !== deviceId) {
        throw new OperationError(409, "operation_id_conflict", "Este operationId ya pertenece a otro dispositivo.");
      }
      return { ...(JSON.parse(prior.responseJson) as Record<string, unknown>), replayed: true };
    }

    const payload = operation.payload;
    const storeSyncId = payload.storeSyncId;
    const productSyncId = payload.productSyncId;
    if (!isUuid(storeSyncId) || !isUuid(productSyncId)) {
      throw new OperationError(400, "invalid_operation_payload", "La sucursal o el producto no tienen un syncId válido.");
    }

    const product = db.prepare(`
      SELECT p.sync_id AS syncId, p.store_sync_id AS storeSyncId, p.name,
        p.cost_price_minor AS costPriceMinor, p.sale_price_minor AS salePriceMinor,
        p.stock, p.version, s.name AS storeName
      FROM products p JOIN stores s ON s.sync_id = p.store_sync_id
      WHERE p.sync_id = ? AND p.store_sync_id = ? AND p.deleted_at IS NULL AND s.deleted_at IS NULL
    `).get(productSyncId, storeSyncId) as {
      syncId: string; storeSyncId: string; name: string; costPriceMinor: number;
      salePriceMinor: number; stock: number; version: number; storeName: string;
    } | undefined;

    if (!product) throw new OperationError(404, "product_not_found", "El producto no existe en la sucursal indicada.");

    const now = Date.now();
    let result: Record<string, unknown>;

    if (operation.type === "sale.create") {
      const quantity = payload.quantity;
      const customerNote = payload.customerNote ?? "";
      if (!Number.isSafeInteger(quantity) || Number(quantity) <= 0
        || !isText(customerNote, 240)) {
        throw new OperationError(400, "invalid_sale", "La cantidad debe ser positiva y la nota no puede superar 240 caracteres.");
      }
      const qty = Number(quantity);
      if (product.stock < qty) {
        throw new OperationError(409, "insufficient_stock", "No hay existencia suficiente para completar la venta.");
      }

      const totalAmountMinor = product.salePriceMinor * qty;
      const netProfitMinor = (product.salePriceMinor - product.costPriceMinor) * qty;
      if (!Number.isSafeInteger(totalAmountMinor) || !Number.isSafeInteger(netProfitMinor)) {
        throw new OperationError(400, "amount_overflow", "El importe de la operación supera el límite permitido.");
      }

      const newStock = product.stock - qty;
      const saleSyncId = randomUUID();
      const movementSyncId = randomUUID();
      db.prepare(`
        INSERT INTO sales (sync_id, operation_id, store_sync_id, product_sync_id, product_name,
          quantity, unit_cost_minor, unit_price_minor, total_amount_minor, net_profit_minor, customer_note, timestamp)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      `).run(saleSyncId, operation.operationId, storeSyncId, productSyncId, product.name,
        qty, product.costPriceMinor, product.salePriceMinor, totalAmountMinor, netProfitMinor,
        String(customerNote).trim(), now);

      db.prepare("UPDATE products SET stock = ?, updated_at = ?, version = version + 1 WHERE sync_id = ?")
        .run(newStock, now, productSyncId);
      db.prepare(`
        INSERT INTO stock_movements (sync_id, operation_id, store_sync_id, store_name, product_sync_id,
          product_name, movement_type, quantity, previous_stock, new_stock, reason, timestamp)
        VALUES (?, ?, ?, ?, ?, ?, 'VENTA', ?, ?, ?, ?, ?)
      `).run(movementSyncId, operation.operationId, storeSyncId, product.storeName,
        productSyncId, product.name, qty, product.stock, newStock, "Venta", now);

      const updatedProduct = db.prepare(`
        SELECT sync_id AS syncId, store_sync_id AS storeSyncId, name, category, supplier,
          cost_price_minor AS costPriceMinor, sale_price_minor AS salePriceMinor, stock,
          min_stock_alert AS minStockAlert, photo_url AS photoUrl, sale_link AS saleLink,
          version, created_at AS createdAt, updated_at AS updatedAt
        FROM products WHERE sync_id = ?
      `).get(productSyncId);
      logChange("product", productSyncId, "upsert", updatedProduct);
      result = { operationId: operation.operationId, type: operation.type, status: "applied",
        saleSyncId, movementSyncId, totalAmountMinor, netProfitMinor, stock: newStock };
    } else {
      const delta = payload.delta;
      const reason = payload.reason ?? "Ajuste manual";
      if (!Number.isSafeInteger(delta) || Number(delta) === 0 || !isText(reason, 240, 1)) {
        throw new OperationError(400, "invalid_stock_adjustment", "El ajuste debe ser un entero distinto de cero e incluir un motivo.");
      }
      const change = Number(delta);
      const newStock = product.stock + change;
      if (!Number.isSafeInteger(newStock) || newStock < 0) {
        throw new OperationError(409, "insufficient_stock", "El ajuste no puede dejar el inventario en negativo.");
      }
      const movementSyncId = randomUUID();
      db.prepare("UPDATE products SET stock = ?, updated_at = ?, version = version + 1 WHERE sync_id = ?")
        .run(newStock, now, productSyncId);
      db.prepare(`
        INSERT INTO stock_movements (sync_id, operation_id, store_sync_id, store_name, product_sync_id,
          product_name, movement_type, quantity, previous_stock, new_stock, reason, timestamp)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      `).run(movementSyncId, operation.operationId, storeSyncId, product.storeName, productSyncId,
        product.name, change > 0 ? "AJUSTE_POSITIVO" : "AJUSTE_NEGATIVO", Math.abs(change),
        product.stock, newStock, String(reason).trim(), now);

      const updatedProduct = db.prepare(`
        SELECT sync_id AS syncId, store_sync_id AS storeSyncId, name, category, supplier,
          cost_price_minor AS costPriceMinor, sale_price_minor AS salePriceMinor, stock,
          min_stock_alert AS minStockAlert, photo_url AS photoUrl, sale_link AS saleLink,
          version, created_at AS createdAt, updated_at AS updatedAt
        FROM products WHERE sync_id = ?
      `).get(productSyncId);
      logChange("product", productSyncId, "upsert", updatedProduct);
      result = { operationId: operation.operationId, type: operation.type, status: "applied",
        movementSyncId, previousStock: product.stock, stock: newStock };
    }

    db.prepare("INSERT INTO operation_receipts (operation_id, device_id, response_json, created_at) VALUES (?, ?, ?, ?)")
      .run(operation.operationId, deviceId, JSON.stringify(result), now);
    return result;
  });

  return execute();
}

app.post("/api/v1/sync/operations", (request: AuthenticatedRequest, response) => {
  const operations = request.body?.operations;
  if (!Array.isArray(operations) || operations.length < 1 || operations.length > 100) {
    response.status(400).json({ error: "invalid_operations", message: "Envía entre 1 y 100 operaciones por solicitud." });
    return;
  }

  const results = operations.map((candidate: unknown) => {
    if (!candidate || typeof candidate !== "object") {
      return { status: "rejected", error: "invalid_operation", message: "La operación debe ser un objeto." };
    }
    const operation = candidate as Record<string, unknown>;
    if (!isUuid(operation.operationId)
      || (operation.type !== "sale.create" && operation.type !== "stock.adjust")
      || !operation.payload || typeof operation.payload !== "object" || Array.isArray(operation.payload)) {
      return { operationId: operation.operationId, status: "rejected", error: "invalid_operation", message: "Tipo, operationId o payload inválido." };
    }

    try {
      return processInventoryOperation(request.deviceId!, operation as InventoryOperation);
    } catch (error) {
      if (error instanceof OperationError) {
        return { operationId: operation.operationId, status: "rejected", error: error.code, message: error.message, httpStatus: error.status };
      }
      throw error;
    }
  });

  response.status(200).json({ items: results });
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
