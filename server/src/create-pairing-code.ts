import { createHash, randomBytes } from "node:crypto";
import { db } from "./database.js";

const code = randomBytes(10).toString("hex");
const codeHash = createHash("sha256").update(code).digest("hex");
const lifetimeMinutes = 5;
const expiresAt = Date.now() + lifetimeMinutes * 60_000;

db.prepare("DELETE FROM pairing_codes WHERE expires_at <= ? OR consumed_at IS NOT NULL").run(Date.now());
db.prepare("INSERT INTO pairing_codes (code_hash, expires_at) VALUES (?, ?)").run(codeHash, expiresAt);

console.log("\nCódigo de emparejamiento (válido por 5 minutos):");
console.log(code);
console.log("\nCompártelo únicamente con tu dispositivo Android de confianza.\n");
db.close();
