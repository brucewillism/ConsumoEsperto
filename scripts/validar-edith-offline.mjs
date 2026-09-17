#!/usr/bin/env node
/**
 * Item 25: E.D.I.T.H. offline — core UP, transação criada, NEEDS_REVIEW,
 * sem fallback Groq/OpenAI/Gemini no caminho de classificação, sem task nova.
 */
import { existsSync, readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const baseUrl = process.env.BASE_URL || "http://localhost:18081";
const banco = process.env.INTEGRACAO_DB || "consumoesperto_integracao";
const suffix = new Date().toISOString().replace(/[-:TZ.]/g, "").slice(0, 14);
const pass = "SenhaTeste123!";
const cases = [];

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
const psqlCandidates = [
  "C:\\Program Files\\PostgreSQL\\17\\bin\\psql.exe",
  "C:\\Program Files\\PostgreSQL\\18\\bin\\psql.exe",
];
const psql = psqlCandidates.find((p) => existsSync(p));
if (!psql) throw new Error("psql.exe não encontrado");

function addCase(id, resultado, evidencia) {
  cases.push({ id, resultado, evidencia: String(evidencia ?? "") });
  const tag = resultado === "OK" ? "OK" : resultado === "LIMITADO" || resultado === "PARCIAL" ? "~~" : "XX";
  console.log(`[${tag}] ${id} — ${evidencia}`);
}

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

async function api(method, path, { token, body, headers } = {}) {
  const h = { ...(headers || {}) };
  if (token) h.Authorization = `Bearer ${token}`;
  if (body !== undefined) h["Content-Type"] = "application/json; charset=utf-8";
  const res = await fetch(`${baseUrl}${path}`, {
    method,
    headers: h,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  let json = null;
  try {
    json = text ? JSON.parse(text) : null;
  } catch {
    json = text;
  }
  return { status: res.status, ok: res.ok, json, text };
}

async function waitProcessed(usuarioId, sec = 45) {
  const deadline = Date.now() + sec * 1000;
  while (Date.now() < deadline) {
    const n = Number(sql(
      `SELECT count(*) FROM financial_domain_event WHERE usuario_id=${usuarioId}
       AND COALESCE(processing_status, CASE WHEN processed THEN 'PROCESSED' ELSE 'PENDING' END)
           NOT IN ('PROCESSED','FAILED_FINAL')`
    ));
    if (n === 0) return true;
    await new Promise((r) => setTimeout(r, 800));
  }
  return false;
}

function iso(d = new Date()) {
  const pad = (n) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
}

console.log(`=== E.D.I.T.H. offline -> ${baseUrl} suffix=${suffix} ===`);

const health = await api("GET", "/actuator/health");
addCase(
  "offline.core",
  health.ok && health.json?.status === "UP" ? "OK" : "FALHA",
  `health=${health.json?.status || health.status}`
);

const runtime = await api("GET", "/api/runtime-health");
let edithProbe = "sem probe";
try {
  await fetch("http://127.0.0.1:8000/api/v1/integrations/health", { signal: AbortSignal.timeout(2500) });
  edithProbe = "AINDA_NO_AR";
} catch (e) {
  edithProbe = `recusado:${e.cause?.code || e.message}`;
}
addCase(
  "offline.edith_down",
  runtime.json?.core === "AVAILABLE" && edithProbe.startsWith("recusado") ? "OK" : "FALHA",
  `core=${runtime.json?.core} edithRuntime=${runtime.json?.edith} probe=${edithProbe}`
);

const user = {
  username: `aut_off.${suffix}@test.local`,
  email: `aut_off.${suffix}@test.local`,
  password: pass,
  nome: `Autonomia Offline ${suffix}`,
};
const reg = await api("POST", "/api/auth/registro", { body: user });
const login = await api("POST", "/api/auth/login", { body: { username: user.email, password: pass } });
const tok = login.json?.token;
const uid = Number(reg.json?.id);
if (!tok || !uid) {
  addCase("offline.users", "FALHA", `uid=${uid} login=${login.status}`);
  process.exit(2);
}
addCase("offline.users", "OK", `uid=${uid}`);

await api("PUT", "/api/autonomy/preferencias", {
  token: tok,
  body: {
    nivel: "AUTONOMOUS_SAFE",
    registrarAuto: true,
    classificarAuto: true,
    aprenderCategorias: true,
    detectarDuplicatas: true,
    detectarAnomalias: true,
    jarvisProativo: true,
  },
});

const cat = (await api("POST", "/api/categorias", {
  token: tok,
  body: { nome: "Padaria", descricao: "offline", cor: "#aa5500", icone: "bread" },
})).json;
const conta = (await api("POST", "/api/contas-bancarias", {
  token: tok,
  body: { nome: "Conta Off", tipo: "CORRENTE", saldoAtual: 500, limiteChequeEspecial: 0, ativa: true, padrao: true },
})).json;
const cartao = (await api("POST", "/api/cartoes-credito", {
  token: tok,
  body: {
    nome: "Cartao Teste",
    banco: "Banco Teste",
    numeroCartao: "4111111111111111",
    limiteCredito: 5000,
    limiteDisponivel: 5000,
    diaVencimento: 28,
    ativo: true,
  },
})).json;
await api("POST", "/api/mobile-capture/source-mappings", {
  token: tok,
  body: { providerKey: "Cartao Teste", cartaoId: cartao.id },
});
const dev = (await api("POST", "/api/mobile-capture/devices", {
  token: tok,
  body: { name: "iPhone Offline", platform: "IOS_SHORTCUTS" },
})).json;
const deviceToken = dev?.deviceToken || dev?.device_token;

const tasksBefore = Number(sql(
  `SELECT count(*) FROM edith_task_link WHERE usuario_id=${uid} AND source_action='consumo.transaction_classification'`
));
const cogBefore = Number(sql(`SELECT count(*) FROM autonomy_decision_log WHERE usuario_id=${uid} AND cognitive_used=true`));

const logFile = join(root, "logs", "integracao-backend.log");
const logSizeBefore = existsSync(logFile) ? readFileSync(logFile, "utf8").length : 0;

const wallet = await api("POST", "/api/ingestion/mobile/transactions", {
  headers: { "X-CE-Device-Token": deviceToken, "Content-Type": "application/json" },
  body: {
    source: "IOS_WALLET",
    merchant: `PADARIA DESCONHECIDA ${suffix}`,
    amount: 42.5,
    card_hint: "Cartao Teste",
    occurred_at: iso(),
    client_event_id: `off-padaria-${suffix}`,
  },
});
addCase(
  "offline.wallet",
  wallet.status >= 200 && wallet.status < 300 && wallet.json?.transacaoId ? "OK" : "FALHA",
  `HTTP ${wallet.status} status=${wallet.json?.status} tx=${wallet.json?.transacaoId}`
);

const processed = await waitProcessed(uid, 50);
const txId = wallet.json?.transacaoId ? Number(wallet.json.transacaoId) : null;
let catId = null;
let sugId = null;
if (txId) {
  const tx = (await api("GET", `/api/transacoes/${txId}`, { token: tok })).json;
  catId = tx?.categoriaId ?? null;
  sugId = tx?.categoriaSugeridaId ?? null;
}
const review = (await api("GET", "/api/autonomy/revisao", { token: tok })).json;
const reviewHas = (review?.itens || []).some((it) => it.transacaoId === txId || /PADARIA|Classifica|NEEDS_REVIEW|revis/i.test(`${it.kind} ${it.title} ${it.detail}`));
const reviewDb = Number(sql(`SELECT count(*) FROM autonomy_review_item WHERE usuario_id=${uid} AND status='OPEN'`));
const dec = txId
  ? sql(`SELECT decision_type, result, policy, cognitive_used, edith_task_id, reason FROM autonomy_decision_log WHERE transacao_id=${txId} ORDER BY id DESC LIMIT 1`)
  : "";
const tasksAfter = Number(sql(
  `SELECT count(*) FROM edith_task_link WHERE usuario_id=${uid} AND source_action='consumo.transaction_classification'`
));
const cogAfter = Number(sql(`SELECT count(*) FROM autonomy_decision_log WHERE usuario_id=${uid} AND cognitive_used=true`));

addCase(
  "offline.transacao_sem_categoria",
  txId && !catId ? "OK" : "FALHA",
  `tx=${txId} cat=${catId} sugerida=${sugId} processed=${processed} catPadaria=${cat?.id}`
);
addCase(
  "offline.needs_review",
  reviewHas || reviewDb > 0 || /REVIEW|SUGGESTED|NEEDS/i.test(dec) ? "OK" : "FALHA",
  `reviewApi=${review?.total} reviewDb=${reviewDb} hasItem=${reviewHas} dec=${dec}`
);
addCase(
  "offline.sem_task_edith",
  tasksAfter === tasksBefore && cogAfter === cogBefore ? "OK" : "FALHA",
  `tasks ${tasksBefore}->${tasksAfter} cognitive ${cogBefore}->${cogAfter}`
);

let llmHit = false;
if (existsSync(logFile)) {
  const all = readFileSync(logFile, "utf8");
  const tail = all.slice(Math.max(logSizeBefore - 200, 0));
  llmHit = /transaction_classification/.test(tail)
    && /LegacyCognitiveGateway|AiRouterService/.test(tail)
    && /c\.c\.a\.(EdithAutonomyCognitiveAdapter|MerchantLearningService|FinancialAutonomyEngine)/.test(tail);
  const groqOpen = /api\.openai\.com|groq\.com|generativelanguage\.googleapis\.com/.test(tail)
    && /EdithAutonomyCognitiveAdapter|FinancialAutonomyEngine/.test(tail);
  if (groqOpen) llmHit = true;
}
addCase("offline.sem_fallback_llm", llmHit ? "FALHA" : "OK", `fallbackClassificacao=${llmHit}`);

const healthAfter = await api("GET", "/actuator/health");
addCase(
  "offline.core_ainda_up",
  healthAfter.ok && healthAfter.json?.status === "UP" ? "OK" : "FALHA",
  `health=${healthAfter.json?.status}`
);

const falha = cases.filter((c) => c.resultado === "FALHA").length;
console.log(`Relatorio offline OK=${cases.filter((c) => c.resultado === "OK").length} FALHA=${falha}`);
process.exit(falha > 0 ? 2 : 0);
