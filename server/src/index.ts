import express from "express";
import os from "node:os";
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
