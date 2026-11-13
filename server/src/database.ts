import Database from "better-sqlite3";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const currentDir = path.dirname(fileURLToPath(import.meta.url));
const dataDirectory = process.env.INVENTORY_DATA_DIR
  ? path.resolve(process.env.INVENTORY_DATA_DIR)
  : path.resolve(process.cwd(), "data");

fs.mkdirSync(dataDirectory, { recursive: true });

const databasePath = path.join(dataDirectory, "inventory-app.sqlite");
export const db = new Database(databasePath);

db.pragma("journal_mode = WAL");
db.pragma("foreign_keys = ON");
db.pragma("busy_timeout = 5000");

const migrationsDirectory = path.resolve(currentDir, "../migrations");
db.exec(`
  CREATE TABLE IF NOT EXISTS schema_migrations (
    version INTEGER PRIMARY KEY,
    applied_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
  );
`);

const migrationFiles = fs.readdirSync(migrationsDirectory)
  .filter((file) => /^\d+.*\.sql$/.test(file))
  .sort();

for (const file of migrationFiles) {
  const version = Number(file.split("_", 1)[0]);
  const alreadyApplied = db.prepare(
    "SELECT 1 FROM schema_migrations WHERE version = ?"
  ).get(version);

  if (alreadyApplied) continue;

  const sql = fs.readFileSync(path.join(migrationsDirectory, file), "utf8");
  const applyMigration = db.transaction(() => {
    db.exec(sql);
    db.prepare("INSERT INTO schema_migrations (version) VALUES (?)").run(version);
  });
  applyMigration();
}

export const databaseFilePath = databasePath;
