import express from "express";
import os from "node:os";
import { createHash, randomBytes } from "node:crypto";
import { databaseFilePath, db } from "./database.js";

const app = express();
const port = Number(process.env.PORT ?? 8787);
const host = process.env.HOST ?? "0.0.0.0";

app.disable("x-powered-by");
app.use(express.json({ limit: "256kb" }));

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

app.post("/api/v1/pairing/exchange", (request, response) => {
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
