import test from "node:test";
import assert from "node:assert/strict";
import { spawn, spawnSync } from "node:child_process";
import { mkdtemp, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";
import { setTimeout as delay } from "node:timers/promises";

test("LAN API pairing, authorization, CRUD and change feed", async (t) => {
  const dataDir = await mkdtemp(path.join(tmpdir(), "inventory-api-"));
  const port = 20000 + Math.floor(Math.random() * 20000);
  const baseUrl = `http://127.0.0.1:${port}`;
  const env = { ...process.env, PORT: String(port), HOST: "127.0.0.1", INVENTORY_DATA_DIR: dataDir };
  const server = spawn(process.execPath, ["--import", "tsx", "src/index.ts"], {
    cwd: process.cwd(), env, stdio: ["ignore", "pipe", "pipe"]
  });
  let serverOutput = "";
  server.stdout.setEncoding("utf8").on("data", (chunk) => { serverOutput += chunk; });
  server.stderr.setEncoding("utf8").on("data", (chunk) => { serverOutput += chunk; });

  t.after(async () => {
    server.kill("SIGTERM");
    await Promise.race([
      new Promise((resolve) => server.once("exit", resolve)),
      delay(3000)
    ]);
    await rm(dataDir, { recursive: true, force: true });
  });

  let ready = false;
  for (let attempt = 0; attempt < 60; attempt += 1) {
    try {
      const response = await fetch(`${baseUrl}/api/v1/health`);
      if (response.ok) { ready = true; break; }
    } catch { /* server is still starting */ }
    await delay(250);
  }
  assert.equal(ready, true, `Server did not start. Output: ${serverOutput}`);

  const health = await fetch(`${baseUrl}/api/v1/health`);
  assert.equal(health.status, 200);
  assert.equal((await health.json()).database, "connected");

  const unauthorized = await fetch(`${baseUrl}/api/v1/stores`);
  assert.equal(unauthorized.status, 401);

  const generated = spawnSync(process.execPath, ["--import", "tsx", "src/create-pairing-code.ts"], {
    cwd: process.cwd(), env, encoding: "utf8"
  });
  assert.equal(generated.status, 0, generated.stderr);
  const pairingCode = generated.stdout.match(/[a-f0-9]{20}/)?.[0];
  assert.ok(pairingCode, "pairing code should be printed once");

  const deviceId = "a4f3c2d1-8b7e-4a6f-9c0d-1e2f3a4b5c6d";
  const exchange = await fetch(`${baseUrl}/api/v1/pairing/exchange`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ pairingCode, deviceId, deviceName: "CI Android" })
  });
  assert.equal(exchange.status, 200);
  const { accessToken } = await exchange.json();
  assert.equal(typeof accessToken, "string");

  const headers = { authorization: `Bearer ${accessToken}`, "content-type": "application/json" };
  const replay = await fetch(`${baseUrl}/api/v1/pairing/exchange`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ pairingCode, deviceId, deviceName: "CI Android" })
  });
  assert.equal(replay.status, 401);

  const storeResponse = await fetch(`${baseUrl}/api/v1/stores`, {
    method: "POST", headers,
    body: JSON.stringify({ name: "Sucursal de prueba", address: "LAN" })
  });
  assert.equal(storeResponse.status, 201);
  const { item: store } = await storeResponse.json();
  assert.match(store.syncId, /^[0-9a-f-]{36}$/);

  const categoryResponse = await fetch(`${baseUrl}/api/v1/catalog`, {
    method: "POST", headers,
    body: JSON.stringify({ name: "Accesorios", type: "CATEGORY" })
  });
  assert.equal(categoryResponse.status, 201);

  const productResponse = await fetch(`${baseUrl}/api/v1/products`, {
    method: "POST", headers,
    body: JSON.stringify({
      storeSyncId: store.syncId, name: "Cable USB", category: "Accesorios",
      supplier: "Proveedor CI", costPriceMinor: 1250, salePriceMinor: 2195,
      stock: 8, minStockAlert: 2
    })
  });
  assert.equal(productResponse.status, 201);
  const { item: product } = await productResponse.json();
  assert.equal(product.salePriceMinor, 2195);

  const bootstrapResponse = await fetch(`${baseUrl}/api/v1/bootstrap`, { headers });
  assert.equal(bootstrapResponse.status, 200);
  const bootstrap = await bootstrapResponse.json();
  assert.equal(bootstrap.stores.length, 1);
  assert.equal(bootstrap.products.length, 1);
  assert.ok(bootstrap.cursor >= 3);

  const changesResponse = await fetch(`${baseUrl}/api/v1/sync/changes?cursor=0&limit=20`, { headers });
  assert.equal(changesResponse.status, 200);
  const changes = await changesResponse.json();
  assert.equal(changes.items.length, 3);
  assert.equal(changes.hasMore, false);
  assert.equal(changes.nextCursor, bootstrap.cursor);
});
