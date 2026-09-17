#!/usr/bin/env node
/**
 * Multi-instance lock em PostgreSQL real (consumoesperto_integracao).
 * Dois UPDATEs concorrentes: só um assume. Lock expirado: outro assume.
 */
import { existsSync, readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { spawn, spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const banco = process.env.INTEGRACAO_DB || "consumoesperto_integracao";

function loadDotEnv(path) {
  const map = {};
  if (!existsSync(path)) return map;
  for (const line of readFileSync(path, "utf8").split(/\r?\n/)) {
    const t = line.trim();
    if (!t || t.startsWith("#") || !t.includes("=")) continue;
    const i = t.indexOf("=");
    map[t.slice(0, i).trim()] = t.slice(i + 1).trim();
  }
  return map;
}

const dot = loadDotEnv(join(root, ".env"));
const pgUser = dot.DATABASE_USERNAME || dot.POSTGRES_USER;
process.env.PGPASSWORD = dot.DATABASE_PASSWORD || dot.POSTGRES_PASSWORD;
const psql = [
  "C:\\Program Files\\PostgreSQL\\17\\bin\\psql.exe",
  "C:\\Program Files\\PostgreSQL\\18\\bin\\psql.exe",
].find((p) => existsSync(p));
if (!psql) throw new Error("psql.exe não encontrado");

function sql(q) {
  const r = spawnSync(psql, ["-U", pgUser, "-d", banco, "-tAc", q], {
    encoding: "utf8",
    env: process.env,
  });
  if (r.status !== 0) {
    throw new Error(`psql ${r.status}: ${(r.stderr || r.stdout || "").trim()}`);
  }
  return (r.stdout || "").trim();
}

function sqlAsync(q) {
  return new Promise((resolve, reject) => {
    const child = spawn(psql, ["-U", pgUser, "-d", banco, "-tAc", q], {
      env: process.env,
    });
    let out = "";
    let err = "";
    child.stdout.on("data", (d) => { out += d; });
    child.stderr.on("data", (d) => { err += d; });
    child.on("close", (code) => {
      if (code !== 0) reject(new Error(err || out || String(code)));
      else resolve(out.trim());
    });
  });
}

const job = `lock-it-${Date.now()}`;
sql(`INSERT INTO autonomy_job_lock (job_name, locked_until, locked_by) VALUES ('${job}', now() + interval '5 minutes', 'node-a')`);
const stealLive = Number(sql(
  `WITH u AS (
     UPDATE autonomy_job_lock SET locked_until = now() + interval '5 minutes', locked_by = 'node-b'
     WHERE job_name = '${job}' AND (locked_until IS NULL OR locked_until <= now())
     RETURNING 1
   ) SELECT count(*) FROM u`
));
const holderLive = sql(`SELECT locked_by FROM autonomy_job_lock WHERE job_name = '${job}'`);
const liveOk = stealLive === 0 && holderLive === "node-a";
console.log(`[${liveOk ? "OK" : "XX"}] lock.vigente — steal=${stealLive} holder=${holderLive} (esperado steal=0 holder=node-a)`);

sql(`UPDATE autonomy_job_lock SET locked_until = now() - interval '1 minute', locked_by = 'dead-node' WHERE job_name = '${job}'`);
const qA = `WITH u AS (
  UPDATE autonomy_job_lock SET locked_until = now() + interval '2 minutes', locked_by = 'node-a'
  WHERE job_name = '${job}' AND (locked_until IS NULL OR locked_until <= now())
  RETURNING 1
) SELECT count(*) FROM u`;
const qB = `WITH u AS (
  UPDATE autonomy_job_lock SET locked_until = now() + interval '2 minutes', locked_by = 'node-b'
  WHERE job_name = '${job}' AND (locked_until IS NULL OR locked_until <= now())
  RETURNING 1
) SELECT count(*) FROM u`;
const [a, b] = await Promise.all([sqlAsync(qA), sqlAsync(qB)]);
const wins = Number(a) + Number(b);
const holderExp = sql(`SELECT locked_by FROM autonomy_job_lock WHERE job_name = '${job}'`);
const distinct = Number(sql(`SELECT COUNT(DISTINCT locked_by) FROM autonomy_job_lock WHERE job_name = '${job}'`));
const expOk = wins === 1 && distinct === 1 && (holderExp === "node-a" || holderExp === "node-b");
console.log(`[${expOk ? "OK" : "XX"}] lock.expirado — a=${a} b=${b} wins=${wins} holder=${holderExp}`);
sql(`DELETE FROM autonomy_job_lock WHERE job_name = '${job}'`);
if (!liveOk || !expOk) process.exit(2);
