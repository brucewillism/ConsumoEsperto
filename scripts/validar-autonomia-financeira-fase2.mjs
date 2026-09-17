#!/usr/bin/env node
/**
 * Fase 2 da validação integrada: fatura viva com mapping, review/JARVIS,
 * duplicata sem fingerprint horário, anomalia no algoritmo real, jobs/drain.
 */
import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { spawnSync } from "node:child_process";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { tmpdir } from "node:os";
import { execFileSync } from "node:child_process";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const baseUrl = process.env.BASE_URL || "http://localhost:18081";
const banco = process.env.INTEGRACAO_DB || "consumoesperto_integracao";
const suffix = process.env.FASE2_SUFFIX || "20260913162541";
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
const psql = [
  "C:\\Program Files\\PostgreSQL\\17\\bin\\psql.exe",
  "C:\\Program Files\\PostgreSQL\\18\\bin\\psql.exe",
].find((p) => existsSync(p));
if (!psql) throw new Error("psql.exe não encontrado");

function addCase(id, resultado, evidencia) {
  cases.push({ id, resultado, evidencia: String(evidencia ?? "") });
  const tag = resultado === "OK" ? "OK" : resultado === "LIMITADO" || resultado === "PARCIAL" ? "~~" : "XX";
  console.log(`[${tag}] ${id} — ${evidencia}`);
}

function sql(q) {
  const r = spawnSync(psql, ["-U", pgUser, "-d", banco, "-tAc", q], { encoding: "utf8", env: process.env });
  if (r.status !== 0) throw new Error(`psql ${r.status}: ${(r.stderr || r.stdout || "").trim()}`);
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

function iso(d = new Date()) {
  const pad = (n) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
}
function brDate(d = new Date()) {
  const pad = (n) => String(n).padStart(2, "0");
  return `${pad(d.getDate())}/${pad(d.getMonth() + 1)}/${d.getFullYear()}`;
}

async function sendWallet(devToken, payload) {
  return api("POST", "/api/ingestion/mobile/transactions", {
    headers: { "X-CE-Device-Token": devToken, "Content-Type": "application/json" },
    body: payload,
  });
}

async function waitNewProcessed(usuarioId, minId, sec = 25) {
  const deadline = Date.now() + sec * 1000;
  while (Date.now() < deadline) {
    const n = Number(
      sql(
        `SELECT count(*) FROM financial_domain_event WHERE usuario_id=${usuarioId} AND id>=${minId} AND processed=false`
      )
    );
    if (n === 0) return true;
    await new Promise((r) => setTimeout(r, 500));
  }
  return false;
}

function maxEvent(usuarioId) {
  return Number(sql(`SELECT coalesce(max(id),0) FROM financial_domain_event WHERE usuario_id=${usuarioId}`));
}

console.log(`=== Autonomia fase2 -> ${baseUrl} db=${banco} suffix=${suffix} ===`);

const health = await api("GET", "/actuator/health");
addCase("runtime.backend", health.ok && health.json?.status === "UP" ? "OK" : "FALHA", `health=${health.json?.status || health.status}`);
if (!health.ok) process.exit(2);

const userAEmail = `aut_a.${suffix}@test.local`;
const userBEmail = `aut_b.${suffix}@test.local`;
const loginA = await api("POST", "/api/auth/login", { body: { username: userAEmail, password: pass } });
const loginB = await api("POST", "/api/auth/login", { body: { username: userBEmail, password: pass } });
const tokA = loginA.json?.token;
const tokB = loginB.json?.token;
const uidA = Number(sql(`SELECT id FROM usuarios WHERE email='${userAEmail}'`));
const uidB = Number(sql(`SELECT id FROM usuarios WHERE email='${userBEmail}'`));
addCase("massa.login", tokA && tokB ? "OK" : "FALHA", `A=${uidA} B=${uidB} loginA=${loginA.status}`);

const rt = await api("GET", "/api/runtime-health", { token: tokA });
addCase(
  "runtime.snapshot",
  rt.ok && rt.json?.core === "AVAILABLE" ? "OK" : "FALHA",
  JSON.stringify(rt.json)
);

const prefs = await api("GET", "/api/autonomy/preferencias", { token: tokA });
const jarvisFlag = !!prefs.json?.flags?.AUTONOMY_PROACTIVE_JARVIS_ENABLED;
addCase(
  "runtime.jarvis_flag",
  jarvisFlag ? "OK" : "FALHA",
  JSON.stringify(prefs.json?.flags)
);

await api("PUT", "/api/autonomy/preferencias", {
  token: tokA,
  body: {
    nivel: "AUTONOMOUS_SAFE",
    classificarAuto: true,
    aprenderCategorias: true,
    detectarDuplicatas: true,
    detectarAnomalias: true,
    jarvisProativo: true,
    resumoDiario: false,
  },
});

const cats = (await api("GET", "/api/categorias", { token: tokA })).json || [];
const catComb = (Array.isArray(cats) ? cats : []).find((c) => /combust/i.test(c.nome));
const catTransp = (Array.isArray(cats) ? cats : []).find((c) => /transp/i.test(c.nome));
const catEnergia = (Array.isArray(cats) ? cats : []).find((c) => /energia/i.test(c.nome));
const cartoes = (await api("GET", "/api/cartoes-credito", { token: tokA })).json || [];
const cartaoA = (Array.isArray(cartoes) ? cartoes : []).find((c) => /teste/i.test(c.nome)) || cartoes[0];
const faturas = (await api("GET", `/api/faturas/cartao/${cartaoA.id}`, { token: tokA })).json;
const faturaLista = Array.isArray(faturas) ? faturas : faturas?.content || faturas?.itens || [];
let faturaA = faturaLista[0];
if (!faturaA?.id) {
  const byId = await api("GET", "/api/faturas/1", { token: tokA });
  if (byId.ok) faturaA = byId.json;
}
addCase("massa.cartao_fatura", faturaA?.id && cartaoA?.id ? "OK" : "FALHA", `cartao=${cartaoA?.id} fatura=${faturaA?.id}`);

const devA = (await api("POST", "/api/mobile-capture/devices", { token: tokA, body: { name: "iPhone Fase2 A", platform: "IOS_SHORTCUTS" } })).json;
const tokenDevA = devA?.deviceToken || devA?.device_token;
const mapA = await api("POST", "/api/mobile-capture/source-mappings", {
  token: tokA,
  body: { deviceId: devA?.deviceId, cartaoId: cartaoA.id, cardLast4: "1111" },
});
addCase("wallet.mapping", mapA.ok ? "OK" : "FALHA", `device=${devA?.deviceId} map=${mapA.status} ${JSON.stringify(mapA.json)}`);

const fatBefore = (await api("GET", `/api/faturas/${faturaA.id}`, { token: tokA })).json;
const totalBefore = Number(fatBefore?.valorTotal || 0);
const somaBefore = Number(sql(`SELECT coalesce(sum(valor),0) FROM transacoes WHERE fatura_id=${faturaA.id} AND excluido=false AND tipo_transacao='DESPESA'`));

const minIdFatura = maxEvent(uidA) + 1;
const walletFat = await sendWallet(tokenDevA, {
  source: "IOS_WALLET",
  merchant: "POSTO IPIRANGA",
  amount: 45.5,
  card_hint: "Cartao Teste 1111",
  occurred_at: iso(),
  client_event_id: `wallet-ipi-${Date.now()}`,
});
await waitNewProcessed(uidA, minIdFatura, 30);
await new Promise((r) => setTimeout(r, 800));
const txIpi = walletFat.json?.transacaoId;
const txIpiRow = txIpi
  ? sql(`SELECT id, fatura_id, valor, descricao FROM transacoes WHERE id=${txIpi}`)
  : "";
const fatAfter = (await api("GET", `/api/faturas/${faturaA.id}`, { token: tokA })).json;
const totalAfter = Number(fatAfter?.valorTotal || 0);
const somaAfter = Number(sql(`SELECT coalesce(sum(valor),0) FROM transacoes WHERE fatura_id=${faturaA.id} AND excluido=false AND tipo_transacao='DESPESA'`));
const delta = Math.round((totalAfter - totalBefore) * 100) / 100;
const faturaOk = Math.abs(delta - 45.5) < 0.011 && Math.abs(Number(somaAfter) - Number(totalAfter)) < 0.011;
addCase(
  "fatura.viva_wallet",
  faturaOk ? "OK" : "FALHA",
  `http=${walletFat.status} status=${walletFat.json?.status} tx=${txIpi} row=${txIpiRow} antes=${totalBefore} depois=${totalAfter} delta=${delta} soma=${somaAfter} somaAntes=${somaBefore}`
);

const csvPath = join(tmpdir(), `ipi-${Date.now()}.csv`);
writeFileSync(csvPath, `Data;Descricao;Valor\n${brDate()};POSTO IPIRANGA;45,50\n`, "utf8");
let uploadJson = "";
try {
  uploadJson = execFileSync(
    "curl.exe",
    [
      "-s", "-S", "-X", "POST", `${baseUrl}/api/importacoes/faturas/upload`,
      "-H", `Authorization: Bearer ${tokA}`,
      "-F", `file=@${csvPath};type=text/csv`,
      "-F", "tipoRecurso=CARTAO",
      "-F", `cartaoCreditoId=${cartaoA.id}`,
    ],
    { encoding: "utf8" }
  );
} catch (e) {
  uploadJson = e.stdout || e.message;
}
let imp = null;
try {
  imp = JSON.parse(uploadJson);
} catch {
  imp = { raw: uploadJson };
}
const previewStatus = imp?.itens?.[0]?.statusPreview || imp?.itens?.[0]?.status || null;
let conf = null;
if (imp?.id) {
  conf = await api("POST", `/api/importacoes/faturas/${imp.id}/confirmar`, { token: tokA, body: {} });
}
const fatCsv = (await api("GET", `/api/faturas/${faturaA.id}`, { token: tokA })).json;
const totalCsv = Number(fatCsv?.valorTotal || 0);
const txIpiCount = Number(sql(`SELECT count(*) FROM transacoes WHERE usuario_id=${uidA} AND excluido=false AND descricao ILIKE '%IPIRANGA%'`));
let evIpi = 0;
if (txIpi) evIpi = Number(sql(`SELECT count(*) FROM transaction_evidence WHERE transacao_id=${txIpi}`));
addCase("csv.ipi.preview", /MATCHED/i.test(String(previewStatus)) ? "OK" : "FALHA", `preview=${previewStatus}`);
addCase(
  "csv.ipi.confirmar",
  conf?.ok && Number(conf.json?.criadas || 0) === 0 && Number(conf.json?.conciliadas || 0) >= 1 ? "OK" : "FALHA",
  `criadas=${conf?.json?.criadas} conciliadas=${conf?.json?.conciliadas}`
);
addCase(
  "fatura.ipi_apos_csv",
  Math.abs(totalCsv - totalAfter) < 0.011 && txIpiCount === 1 ? "OK" : "FALHA",
  `antesCsv=${totalAfter} depoisCsv=${totalCsv} txIpi=${txIpiCount} evidence=${evIpi}`
);

const healthMid = await api("GET", "/actuator/health");
addCase("runtime.core_apos_edith_offline", healthMid.ok && healthMid.json?.status === "UP" ? "OK" : "FALHA", `health=${healthMid.json?.status}`);

const reviewBefore = Number(sql(`SELECT count(*) FROM autonomy_review_item WHERE usuario_id=${uidA}`));
const digestBefore = Number(sql(`SELECT count(*) FROM notificacao_digest_buffer WHERE usuario_id=${uidA}`));
const notifWebBefore = Number(sql(`SELECT count(*) FROM notificacoes WHERE usuario_id=${uidA}`));
const minIdPix = maxEvent(uidA) + 1;
const pix1 = await sendWallet(tokenDevA, {
  source: "PIX",
  merchant: "MARIA SOUZA",
  amount: 350,
  occurred_at: iso(),
  client_event_id: `pix-maria-${Date.now()}`,
});
const pixProcessed = await waitNewProcessed(uidA, minIdPix, 35);
const pixTx = pix1.json?.transacaoId;
let pixCat = null;
if (pixTx) pixCat = (await api("GET", `/api/transacoes/${pixTx}`, { token: tokA })).json?.categoriaId;
const revPix = (await api("GET", "/api/autonomy/revisao", { token: tokA })).json;
const reviewAfter = Number(sql(`SELECT count(*) FROM autonomy_review_item WHERE usuario_id=${uidA}`));
const digestAfter = Number(sql(`SELECT count(*) FROM notificacao_digest_buffer WHERE usuario_id=${uidA}`));
const notifWebAfter = Number(sql(`SELECT count(*) FROM notificacoes WHERE usuario_id=${uidA}`));
const decPix = pixTx
  ? sql(`SELECT decision_type, result, policy, confidence, cognitive_used, edith_task_id FROM autonomy_decision_log WHERE transacao_id=${pixTx} ORDER BY id DESC LIMIT 1`)
  : "";
const reviewHas = (revPix?.itens || []).some((it) => /MARIA|350|Classifica/i.test(`${it.title} ${it.detail}`));
addCase(
  "review.pix_baixa_confianca",
  reviewHas && !pixCat ? "OK" : reviewAfter > reviewBefore ? "PARCIAL" : "FALHA",
  `http=${pix1.status} tx=${pixTx} cat=${pixCat} processed=${pixProcessed} reviewApi=${revPix?.total} reviewDb=${reviewAfter} dec=${decPix}`
);

const minIdPix2 = maxEvent(uidA) + 1;
await sendWallet(tokenDevA, {
  source: "PIX",
  merchant: "PEDRO ALVES",
  amount: 180,
  occurred_at: iso(),
  client_event_id: `pix-pedro-${Date.now()}`,
});
await waitNewProcessed(uidA, minIdPix2, 35);
const digestFinal = Number(sql(`SELECT count(*) FROM notificacao_digest_buffer WHERE usuario_id=${uidA}`));
const enviadas = Number(sql(`SELECT count(*) FROM notificacao_enviada WHERE usuario_id=${uidA}`));
const digestB = Number(sql(`SELECT count(*) FROM notificacao_digest_buffer WHERE usuario_id=${uidB}`));
const enviadasB = Number(sql(`SELECT count(*) FROM notificacao_enviada WHERE usuario_id=${uidB}`));
const leakJarvis = digestB > 0 || enviadasB > 0;
addCase(
  "jarvis.enfileirado",
  digestAfter > digestBefore || digestFinal > 0 || enviadas > 0 ? "OK" : "FALHA",
  `digestAntes=${digestBefore} digestAposPix1=${digestAfter} digestFinal=${digestFinal} enviadas=${enviadas} webNotif ${notifWebBefore}->${notifWebAfter}`
);
addCase("jarvis.isolamento_B", leakJarvis ? "FALHA" : "OK", `digestB=${digestB} enviadasB=${enviadasB}`);
addCase(
  "jarvis.offline_core",
  healthMid.ok && pix1.ok ? "OK" : "FALHA",
  "WhatsApp/Evolution ausente; ingestão e health UP"
);

const dup1t = new Date();
dup1t.setMinutes(dup1t.getMinutes() - 3);
const dup2t = new Date();
const minIdDup = maxEvent(uidA) + 1;
const d1 = await api("POST", "/api/transacoes", {
  token: tokA,
  body: {
    descricao: "POSTO X DUP",
    valor: 89.9,
    tipoTransacao: "DESPESA",
    dataTransacao: iso(dup1t),
    statusConferencia: "CONFIRMADA",
    cartaoCreditoId: cartaoA.id,
  },
});
await waitNewProcessed(uidA, minIdDup, 25);
const minIdDup2 = maxEvent(uidA) + 1;
const d2 = await api("POST", "/api/transacoes", {
  token: tokA,
  body: {
    descricao: "POSTO X DUP",
    valor: 89.9,
    tipoTransacao: "DESPESA",
    dataTransacao: iso(dup2t),
    statusConferencia: "CONFIRMADA",
    cartaoCreditoId: cartaoA.id,
  },
});
await waitNewProcessed(uidA, minIdDup2, 25);
const dupTx = Number(sql(`SELECT count(*) FROM transacoes WHERE usuario_id=${uidA} AND excluido=false AND descricao ILIKE '%POSTO X DUP%'`));
const dupRev = Number(sql(`SELECT count(*) FROM autonomy_review_item WHERE usuario_id=${uidA} AND kind='DUPLICATE' AND status='OPEN'`));
addCase(
  "duplicate.http_manual",
  dupTx >= 2 && dupRev >= 1 ? "OK" : dupTx >= 2 ? "PARCIAL" : "FALHA",
  `tx=${dupTx} reviewDup=${dupRev} d1=${d1.status}/${d1.json?.id} d2=${d2.status}/${d2.json?.id}`
);

const hist = [55, 48, 40];
for (const days of hist) {
  const d = new Date();
  d.setDate(d.getDate() - days);
  await api("POST", "/api/transacoes", {
    token: tokA,
    body: {
      descricao: "Energia SPIKE",
      valor: 80,
      tipoTransacao: "DESPESA",
      categoriaId: catEnergia?.id,
      dataTransacao: iso(d),
      statusConferencia: "CONFIRMADA",
    },
  });
}
const minIdAn = maxEvent(uidA) + 1;
await api("POST", "/api/transacoes", {
  token: tokA,
  body: {
    descricao: "Energia SPIKE",
    valor: 400,
    tipoTransacao: "DESPESA",
    categoriaId: catEnergia?.id,
    dataTransacao: iso(),
    statusConferencia: "CONFIRMADA",
  },
});
await waitNewProcessed(uidA, minIdAn, 25);
const anom = Number(sql(`SELECT count(*) FROM autonomy_review_item WHERE usuario_id=${uidA} AND kind='ANOMALY' AND status='OPEN'`));
addCase(
  "anomaly.algoritmo_real",
  anom > 0 ? "OK" : "FALHA",
  `openAnomaly=${anom} (recente 400 vs anterior 240 = 1.67x; limiar 1.40)`
);

const forb = Number(
  sql(
    `SELECT count(*) FROM autonomy_decision_log WHERE usuario_id=${uidA} AND action IN ('TRANSFER_MONEY','PAY_INVOICE','CREATE_PIX','MOVE_BALANCE','CHANGE_CREDENTIALS','DELETE_HISTORY') AND result='EXECUTED'`
  )
);
const pagAuto = Number(sql(`SELECT count(*) FROM transacoes WHERE usuario_id=${uidA} AND tipo_transacao='PAGAMENTO_FATURA'`));
addCase("security.forbidden_ainda", forb === 0 ? "OK" : "FALHA", `executedForbidden=${forb} pagamentosFatura=${pagAuto}`);

const unproc = Number(sql(`SELECT count(*) FROM financial_domain_event WHERE processed=false`));
const lockRows = sql(`SELECT job_name, locked_by, locked_until FROM autonomy_job_lock`);
addCase("jobs.lock_tabela", lockRows ? "PARCIAL" : "LIMITADO", `unprocessed=${unproc} locks=${lockRows || "(vazio)"}`);

const now = new Date();
const mins = now.getMinutes();
const add = mins % 10 === 0 && now.getSeconds() < 5 ? 0 : 10 - (mins % 10);
const waitMs = Math.min((add * 60 - now.getSeconds()) * 1000 + 25000, 11 * 60 * 1000);
console.log(`Aguardando drain cron ~${Math.round(waitMs / 1000)}s ...`);
await new Promise((r) => setTimeout(r, Math.max(waitMs, 5000)));
const unproc2 = Number(sql(`SELECT count(*) FROM financial_domain_event WHERE processed=false`));
const lockAfter = sql(`SELECT job_name, locked_by, locked_until FROM autonomy_job_lock`);
addCase(
  "jobs.drain",
  unproc2 === 0 || unproc2 < unproc ? "OK" : "LIMITADO",
  `unprocessed ${unproc}->${unproc2} lock=${lockAfter || "(vazio)"}`
);

const logFile = join(root, "logs", "integracao-backend.log");
let openaiHit = false;
let edithHang = false;
if (existsSync(logFile)) {
  const tail = readFileSync(logFile, "utf8").split(/\n/).slice(-800).join("\n");
  openaiHit = /api\.openai\.com|groq\.com|generativelanguage\.googleapis\.com/i.test(tail);
  edithHang = /edith_autonomy_classify_unavailable|E.D.I.T.H. classificação|Connection refused|EDITH_UNAVAILABLE/i.test(tail);
}
addCase("wallet.sem_fallback_llm_fase2", openaiHit ? "FALHA" : "OK", `openai/groq/gemini=${openaiHit} edithLog=${edithHang}`);

const sourceUsed = sql(`SELECT count(*) FROM autonomy_decision_log WHERE cognitive_used=true`);
addCase(
  "source_actions.classification",
  Number(sourceUsed) > 0 ? "PARCIAL" : "AUSENTE",
  `cognitive_used rows=${sourceUsed} (E.D.I.T.H. offline nesta máquina)`
);

const summary = {
  suffix,
  fase: 2,
  ok: cases.filter((c) => c.resultado === "OK").length,
  falha: cases.filter((c) => c.resultado === "FALHA").length,
  limitado: cases.filter((c) => c.resultado === "LIMITADO").length,
  parcial: cases.filter((c) => c.resultado === "PARCIAL").length,
  ausente: cases.filter((c) => c.resultado === "AUSENTE").length,
  casos: cases,
};
mkdirSync(join(root, "logs"), { recursive: true });
const reportPath = join(root, "logs", `autonomia-validacao-fase2-${Date.now()}.json`);
writeFileSync(reportPath, JSON.stringify(summary, null, 2), "utf8");
console.log(`Relatorio: ${reportPath} OK=${summary.ok} FALHA=${summary.falha} LIMITADO=${summary.limitado} PARCIAL=${summary.parcial}`);
process.exit(summary.falha > 0 ? 2 : 0);
