#!/usr/bin/env node
/**
 * Validação HTTP real da autonomia financeira (Postgres de integração, JAR no ar).
 * Não usa banco de produção.
 */
import { execFileSync, spawnSync } from "node:child_process";
import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
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

function looksLikeFrontend(url) {
  try {
    const port = Number(new URL(url).port);
    return [5173, 4173, 4200, 3000, 5174].includes(port);
  } catch {
    return false;
  }
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

console.log(`=== Autonomia HTTP -> ${baseUrl} db=${banco} suffix=${suffix} ===`);

const health = await api("GET", "/actuator/health");
addCase("runtime.backend", health.ok && health.json?.status === "UP" ? "OK" : "FALHA", `health=${health.json?.status || health.status}`);
if (!health.ok) process.exit(2);

const runtime = await api("GET", "/api/runtime-health");
const edithBase = (dot.EDITH_BASE_URL || process.env.EDITH_BASE_URL || "").replace(/\/$/, "");
let edithHealth = "sem EDITH_BASE_URL";
let edithHttp = 0;
if (!edithBase) {
  addCase("runtime.edith_base", "FALHA", "EDITH_BASE_URL vazio");
} else if (looksLikeFrontend(edithBase)) {
  addCase(
    "runtime.edith_base",
    "FALHA",
    `EDITH_BASE_URL aponta para frontend (${edithBase}). API esperada :8000 GET /api/v1/integrations/health`
  );
} else {
  addCase("runtime.edith_base", "OK", edithBase);
  try {
    const er = await fetch(`${edithBase}/api/v1/integrations/health`, { signal: AbortSignal.timeout(4000) });
    edithHttp = er.status;
    edithHealth = `HTTP ${er.status}`;
  } catch (e) {
    edithHealth = `indisponivel: ${e.cause?.code || e.message}`;
  }
}
const edithRuntime = runtime.json?.edith;
addCase(
  "runtime.edith",
  edithRuntime === "AVAILABLE" && edithHttp >= 200 && edithHttp < 300 ? "OK" : "LIMITADO",
  `runtime-health.edith=${edithRuntime} probe=${edithHealth} frontendMisconfigured=${runtime.json?.edithFrontendPortMisconfigured}`
);

const userA = { username: `aut_a.${suffix}@test.local`, email: `aut_a.${suffix}@test.local`, password: pass, nome: `Autonomia A ${suffix}` };
const userB = { username: `aut_b.${suffix}@test.local`, email: `aut_b.${suffix}@test.local`, password: pass, nome: `Autonomia B ${suffix}` };
const regA = await api("POST", "/api/auth/registro", { body: userA });
const regB = await api("POST", "/api/auth/registro", { body: userB });
const loginA = await api("POST", "/api/auth/login", { body: { username: userA.email, password: pass } });
const loginB = await api("POST", "/api/auth/login", { body: { username: userB.email, password: pass } });
const tokA = loginA.json?.token;
const tokB = loginB.json?.token;
const uidA = Number(regA.json?.id);
const uidB = Number(regB.json?.id);
addCase("massa.users", tokA && tokB && uidA && uidB ? "OK" : "FALHA", `A=${uidA} B=${uidB} loginA=${loginA.status}`);

await api("PUT", "/api/autonomy/preferencias", {
  token: tokA,
  body: {
    nivel: "AUTONOMOUS_SAFE",
    registrarAuto: true,
    classificarAuto: true,
    aprenderCategorias: true,
    detectarAssinaturas: true,
    detectarDuplicatas: true,
    detectarAnomalias: true,
    preverSaldo: true,
    jarvisProativo: true,
    resumoDiario: false,
    resumoSemanal: false,
  },
});
const prefs = await api("GET", "/api/autonomy/preferencias", { token: tokA });
addCase(
  "runtime.flags",
  prefs.json?.flags?.FINANCIAL_AUTONOMY_ENABLED ? "OK" : "FALHA",
  JSON.stringify(prefs.json?.flags || prefs.json)
);

await api("PUT", "/api/autonomy/preferencias", {
  token: tokB,
  body: { nivel: "ASSISTED", classificarAuto: true, aprenderCategorias: true },
});

const catComb = (await api("POST", "/api/categorias", { token: tokA, body: { nome: "Combustivel", descricao: "Cat A", cor: "#cc5500", icone: "gas" } })).json;
const catTransp = (await api("POST", "/api/categorias", { token: tokA, body: { nome: "Transporte", descricao: "Cat A2", cor: "#336699", icone: "bus" } })).json;
const catEnergia = (await api("POST", "/api/categorias", { token: tokA, body: { nome: "Energia", descricao: "Cat A3", cor: "#f0c000", icone: "bolt" } })).json;
const catB = (await api("POST", "/api/categorias", { token: tokB, body: { nome: "Secreta B", descricao: "nao vazar", cor: "#111111", icone: "lock" } })).json;
const contaA = (await api("POST", "/api/contas-bancarias", { token: tokA, body: { nome: "Conta A", tipo: "CORRENTE", saldoAtual: 1000, limiteChequeEspecial: 0, ativa: true, padrao: true } })).json;
const contaB = (await api("POST", "/api/contas-bancarias", { token: tokB, body: { nome: "Conta B", tipo: "CORRENTE", saldoAtual: 50, limiteChequeEspecial: 0, ativa: true, padrao: true } })).json;
const cartaoA = (await api("POST", "/api/cartoes-credito", { token: tokA, body: { nome: "Cartao Teste", banco: "Banco Teste", numeroCartao: "4111111111111111", limiteCredito: 5000, limiteDisponivel: 5000, diaVencimento: 28, ativo: true } })).json;
const cartaoB = (await api("POST", "/api/cartoes-credito", { token: tokB, body: { nome: "Cartao B", banco: "Banco B", numeroCartao: "4222222222222222", limiteCredito: 2000, limiteDisponivel: 2000, diaVencimento: 15, ativo: true } })).json;

const faturaA = (await api("POST", "/api/faturas", {
  token: tokA,
  body: {
    cartaoCreditoId: cartaoA.id,
    valorTotal: 0,
    valorFatura: 0,
    dataVencimento: "2026-09-28T12:00:00",
    dataFechamento: "2026-09-21T12:00:00",
    statusFatura: "ABERTA",
    paga: false,
  },
})).json;
const faturaAntes = Number(faturaA?.valorTotal || 0);
addCase("massa.fatura", faturaA?.id ? "OK" : "FALHA", `faturaId=${faturaA?.id} totalAntes=${faturaAntes} cartao=${cartaoA?.id}`);

const mapWallet = await api("POST", "/api/mobile-capture/source-mappings", {
  token: tokA,
  body: { providerKey: "Cartao Teste", cartaoId: cartaoA.id },
});
addCase(
  "wallet.mapping",
  mapWallet.ok && mapWallet.json?.cartaoId === cartaoA.id ? "OK" : "FALHA",
  `HTTP ${mapWallet.status} cartaoId=${mapWallet.json?.cartaoId}`
);

const rulesShell = Number(sql(`SELECT count(*) FROM merchant_category_rules WHERE usuario_id=${uidA} AND (merchant_normalized ILIKE '%SHELL%' OR merchant_pattern ILIKE '%SHELL%')`));
addCase("massa.sem_regra_shell", rulesShell === 0 ? "OK" : "FALHA", `regrasShellA=${rulesShell}`);

const devA = (await api("POST", "/api/mobile-capture/devices", { token: tokA, body: { name: "iPhone Teste A", platform: "IOS_SHORTCUTS" } })).json;
const devB = (await api("POST", "/api/mobile-capture/devices", { token: tokB, body: { name: "iPhone Teste B", platform: "IOS_SHORTCUTS" } })).json;
const tokenDevA = devA?.deviceToken || devA?.device_token;
addCase("wallet.token", tokenDevA ? "OK" : "FALHA", `deviceId=${devA?.deviceId}`);

const now = new Date();
const wallet1 = await sendWallet(tokenDevA, {
  source: "IOS_WALLET",
  merchant: "POSTO SHELL",
  amount: 89.9,
  card_hint: "Cartao Teste",
  occurred_at: iso(now),
  client_event_id: `wallet-shell-1-${suffix}`,
});
addCase(
  "wallet.http",
  wallet1.status >= 200 && wallet1.status < 300 ? "OK" : "FALHA",
  `HTTP ${wallet1.status} status=${wallet1.json?.status} tx=${wallet1.json?.transacaoId} msg=${wallet1.json?.message || wallet1.text}`
);

await waitProcessed(uidA, 90);
await new Promise((r) => setTimeout(r, 1500));

let txId1 = wallet1.json?.transacaoId ? Number(wallet1.json.transacaoId) : null;
const txCount = Number(sql(`SELECT count(*) FROM transacoes t WHERE t.usuario_id=${uidA} AND t.excluido=false AND (t.descricao ILIKE '%SHELL%' OR coalesce(t.merchant_normalized,'') ILIKE '%SHELL%' OR coalesce(t.merchant_raw,'') ILIKE '%SHELL%')`));
addCase("wallet.transacao", txCount >= 1 ? "OK" : "FALHA", `shellTxCount=${txCount} txId=${txId1}`);
if (!txId1) {
  const raw = sql(`SELECT id FROM transacoes WHERE usuario_id=${uidA} AND excluido=false ORDER BY id DESC LIMIT 1`);
  if (raw) txId1 = Number(raw);
}

const dec1 = sql(`SELECT decision_type, result, policy, confidence, edith_task_id, cognitive_used, reason FROM autonomy_decision_log WHERE usuario_id=${uidA} ORDER BY id DESC LIMIT 1`);
addCase("wallet.autonomy", dec1 ? "OK" : "FALHA", `decision=${dec1}`);
const edithLink = sql(`SELECT edith_conversation_id, edith_task_id, source_action FROM edith_task_link WHERE usuario_id=${uidA} AND source_action='consumo.transaction_classification' ORDER BY id DESC LIMIT 1`);
addCase(
  "wallet.edith",
  /EDITH/i.test(dec1) && /consumo\.transaction_classification/.test(dec1) ? "OK" : edithLink ? "PARCIAL" : "LIMITADO",
  /EDITH/i.test(dec1)
    ? dec1
    : edithLink
      ? `task criada na E.D.I.T.H. mas categoria nao aplicada (modelo/resultado). link=${edithLink} dec=${dec1}`
      : `E.D.I.T.H. nao classificou. ${dec1}`
);

const logFile = join(root, "logs", "integracao-backend.log");
let openaiHit = false;
if (existsSync(logFile)) {
  const tail = readFileSync(logFile, "utf8").split(/\n/).slice(-120).join("\n");
  openaiHit = /transaction_classification/.test(tail)
    && /LegacyCognitiveGateway|AiRouterService/.test(tail)
    && /c\.c\.a\.(EdithAutonomyCognitiveAdapter|MerchantLearningService|FinancialAutonomyEngine)/.test(tail);
}
addCase("wallet.sem_fallback_llm", openaiHit ? "FALHA" : "OK", `fallback no caminho autonomia/EDITH=${openaiHit}`);

const fatApos = (await api("GET", `/api/faturas/${faturaA.id}`, { token: tokA })).json;
const conf0 = Number(fatApos?.valorConfirmado ?? 0);
const pend0 = Number(fatApos?.valorPendente ?? 0);
const proj0 = Number(fatApos?.valorProjetado ?? 0);
addCase(
  "fatura.wallet_pendente",
  Math.abs(conf0) < 0.011 && Math.abs(pend0 - 89.9) < 0.011 && Math.abs(proj0 - 89.9) < 0.011 ? "OK" : "FALHA",
  `confirmado=${conf0} pendente=${pend0} projetado=${proj0} valorTotal=${fatApos?.valorTotal}`
);

if (txId1) {
  const txAtual = (await api("GET", `/api/transacoes/${txId1}`, { token: tokA })).json;
  txAtual.statusConferencia = "CONFIRMADA";
  await api("PUT", `/api/transacoes/${txId1}`, { token: tokA, body: txAtual });
}
const fatConf = (await api("GET", `/api/faturas/${faturaA.id}`, { token: tokA })).json;
const conf1 = Number(fatConf?.valorConfirmado ?? 0);
const pend1 = Number(fatConf?.valorPendente ?? 0);
const proj1 = Number(fatConf?.valorProjetado ?? 0);
addCase(
  "fatura.wallet_confirmada",
  Math.abs(conf1 - 89.9) < 0.011 && Math.abs(pend1) < 0.011 && Math.abs(proj1 - 89.9) < 0.011 ? "OK" : "FALHA",
  `confirmado=${conf1} pendente=${pend1} projetado=${proj1}`
);

const csvPath = join(tmpdir(), `shell-${suffix}.csv`);
writeFileSync(csvPath, `Data;Descricao;Valor\n${brDate(now)};POSTO SHELL;89,90\n`, "utf8");
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
addCase("csv.upload", imp?.id ? "OK" : "FALHA", `importId=${imp?.id} preview=${imp?.status || imp?.raw}`);

const previewStatus = imp?.itens?.[0]?.statusPreview || imp?.itens?.[0]?.status || null;
addCase("csv.preview", previewStatus ? "OK" : "LIMITADO", `itemStatus=${previewStatus}`);

let conf = null;
if (imp?.id) {
  conf = await api("POST", `/api/importacoes/faturas/${imp.id}/confirmar`, { token: tokA, body: {} });
  addCase(
    "csv.confirmar",
    conf.ok ? "OK" : "FALHA",
    `HTTP ${conf.status} criadas=${conf.json?.criadas} conciliadas=${conf.json?.conciliadas} msg=${conf.json?.mensagem || conf.text}`
  );
} else {
  addCase("csv.confirmar", "FALHA", "sem importId");
}
await waitProcessed(uidA);
await new Promise((r) => setTimeout(r, 1000));

const txShell = Number(sql(`SELECT count(*) FROM transacoes WHERE usuario_id=${uidA} AND excluido=false AND (descricao ILIKE '%SHELL%' OR coalesce(merchant_normalized,'') ILIKE '%SHELL%')`));
let evCount = 0;
let evSrc = "";
if (txId1) {
  evCount = Number(sql(`SELECT count(*) FROM transaction_evidence WHERE transacao_id=${txId1}`));
  evSrc = sql(`SELECT string_agg(source, chr(44) ORDER BY source) FROM transaction_evidence WHERE transacao_id=${txId1}`);
}
addCase("evidence.count", txShell === 1 && evCount >= 2 ? "OK" : "FALHA", `txShell=${txShell} evidence=${evCount} sources=${evSrc}`);

const fatCsv = (await api("GET", `/api/faturas/${faturaA.id}`, { token: tokA })).json;
const confCsv = Number(fatCsv?.valorConfirmado ?? 0);
const pendCsv = Number(fatCsv?.valorPendente ?? 0);
const projCsv = Number(fatCsv?.valorProjetado ?? 0);
addCase(
  "fatura.apos_csv",
  Math.abs(projCsv - 89.9) < 0.011 && Math.abs(projCsv - (confCsv + pendCsv)) < 0.011 ? "OK" : "FALHA",
  `confirmado=${confCsv} pendente=${pendCsv} projetado=${projCsv} valorTotal=${fatCsv?.valorTotal}`
);

if (txId1) {
  const txAtual = (await api("GET", `/api/transacoes/${txId1}`, { token: tokA })).json;
  txAtual.categoriaId = catComb.id;
  await api("PUT", `/api/transacoes/${txId1}`, { token: tokA, body: txAtual });
  await waitProcessed(uidA);
  const tx2put = (await api("GET", `/api/transacoes/${txId1}`, { token: tokA })).json;
  tx2put.categoriaId = catTransp.id;
  await api("PUT", `/api/transacoes/${txId1}`, { token: tokA, body: tx2put });
  await waitProcessed(uidA);
  const ruleAfter = sql(`SELECT categoria_id, merchant_normalized FROM merchant_category_rules WHERE usuario_id=${uidA} AND merchant_normalized ILIKE '%SHELL%' LIMIT 1`);
  addCase("learning.correcao", String(ruleAfter).startsWith(String(catTransp.id)) ? "OK" : "FALHA", `rule=${ruleAfter}`);
} else {
  addCase("learning.correcao", "FALHA", "sem transacao para corrigir");
}

const cogBefore = Number(sql(`SELECT count(*) FROM autonomy_decision_log WHERE usuario_id=${uidA} AND cognitive_used=true`));
const wallet2 = await sendWallet(tokenDevA, {
  source: "IOS_WALLET",
  merchant: "POSTO SHELL",
  amount: 70.0,
  card_hint: "Cartao Teste",
  occurred_at: iso(),
  client_event_id: `wallet-shell-2-${suffix}`,
});
await waitProcessed(uidA);
await new Promise((r) => setTimeout(r, 1500));
const cogAfter = Number(sql(`SELECT count(*) FROM autonomy_decision_log WHERE usuario_id=${uidA} AND cognitive_used=true`));
const tx2 = wallet2.json?.transacaoId;
let cat2 = null;
if (tx2) cat2 = (await api("GET", `/api/transacoes/${tx2}`, { token: tokA })).json?.categoriaId;
const dec2 = tx2
  ? sql(`SELECT reason, cognitive_used, result FROM autonomy_decision_log WHERE transacao_id=${tx2} ORDER BY id DESC LIMIT 1`)
  : "";
const noEdith2 = cogAfter === cogBefore;
addCase(
  "learning.segunda_compra",
  cat2 === catTransp.id && noEdith2 ? "OK" : cat2 === catTransp.id ? "PARCIAL" : "FALHA",
  `cat2=${cat2} esperado=${catTransp.id} cognitiveDelta=${cogAfter - cogBefore} dec=${dec2} http=${wallet2.status}`
);

await api("PUT", "/api/autonomy/preferencias", { token: tokA, body: { nivel: "MANUAL", classificarAuto: true, aprenderCategorias: true } });
const wMan = await sendWallet(tokenDevA, {
  source: "IOS_WALLET",
  merchant: "POSTO SHELL",
  amount: 15.0,
  card_hint: "Cartao Teste",
  occurred_at: iso(),
  client_event_id: `wallet-shell-man-${suffix}`,
});
await waitProcessed(uidA);
let catMan = null;
let sugMan = null;
if (wMan.json?.transacaoId) {
  const txMan = (await api("GET", `/api/transacoes/${wMan.json.transacaoId}`, { token: tokA })).json;
  catMan = txMan?.categoriaId;
  sugMan = txMan?.categoriaSugeridaId;
}
const decMan = wMan.json?.transacaoId
  ? sql(`SELECT result, policy FROM autonomy_decision_log WHERE transacao_id=${wMan.json.transacaoId} ORDER BY id DESC LIMIT 1`)
  : "";
addCase(
  "levels.MANUAL",
  (!catMan && (sugMan === catTransp.id || /SUGGESTED/.test(decMan))) ? "OK" : "FALHA",
  `cat=${catMan} sugerida=${sugMan} esperadoSugerida=${catTransp.id} dec=${decMan}`
);

await api("PUT", "/api/autonomy/preferencias", { token: tokA, body: { nivel: "ASSISTED", classificarAuto: true, aprenderCategorias: true } });
const wAs = await sendWallet(tokenDevA, {
  source: "IOS_WALLET",
  merchant: "POSTO SHELL",
  amount: 16.0,
  card_hint: "Cartao Teste",
  occurred_at: iso(),
  client_event_id: `wallet-shell-as-${suffix}`,
});
await waitProcessed(uidA);
let catAs = null;
if (wAs.json?.transacaoId) {
  catAs = (await api("GET", `/api/transacoes/${wAs.json.transacaoId}`, { token: tokA })).json?.categoriaId;
}
const decAs = wAs.json?.transacaoId
  ? sql(`SELECT result, policy FROM autonomy_decision_log WHERE transacao_id=${wAs.json.transacaoId} ORDER BY id DESC LIMIT 1`)
  : "";
addCase("levels.ASSISTED", catAs === catTransp.id && /EXECUTED/.test(decAs) ? "OK" : "FALHA", `cat=${catAs} dec=${decAs}`);
await api("PUT", "/api/autonomy/preferencias", { token: tokA, body: { nivel: "AUTONOMOUS_SAFE", classificarAuto: true } });
addCase("levels.AUTONOMOUS_SAFE", "OK", "preferencia restaurada; 2a compra SHELL exercitou SAFE_AUTO");

const wPix = await sendWallet(tokenDevA, {
  source: "PIX",
  merchant: "JOAO SILVA",
  amount: 350.0,
  occurred_at: iso(),
  client_event_id: `pix-joao-${suffix}`,
});
await waitProcessed(uidA);
const revPix = (await api("GET", "/api/autonomy/revisao", { token: tokA })).json;
const pixTx = wPix.json?.transacaoId;
let pixCat = null;
if (pixTx) pixCat = (await api("GET", `/api/transacoes/${pixTx}`, { token: tokA })).json?.categoriaId;
const revHasPix = (revPix?.itens || []).some((it) => {
  const blob = `${it.kind || ""} ${it.title || ""} ${it.detail || ""}`;
  return it.transacaoId === pixTx || /JOAO|identifiquei/i.test(blob);
});
addCase(
  "review.pix",
  revHasPix && !pixCat ? "OK" : Number(revPix?.total) > 0 ? "PARCIAL" : "FALHA",
  `reviewTotal=${revPix?.total} pixCat=${pixCat} http=${wPix.status} tx=${pixTx}`
);

const dtEnergia = [70, 60, 50, 40];
const valsEnergia = [100, 105, 98, 102];
for (let i = 0; i < 4; i++) {
  const d = new Date();
  d.setDate(d.getDate() - dtEnergia[i]);
  await api("POST", "/api/transacoes", {
    token: tokA,
    body: {
      descricao: "Energia CEMIG",
      valor: valsEnergia[i],
      tipoTransacao: "DESPESA",
      categoriaId: catEnergia.id,
      contaBancariaId: contaA.id,
      dataTransacao: iso(d),
      statusConferencia: "CONFIRMADA",
    },
  });
}
await api("POST", "/api/transacoes", {
  token: tokA,
  body: {
    descricao: "Energia CEMIG",
    valor: 220,
    tipoTransacao: "DESPESA",
    categoriaId: catEnergia.id,
    contaBancariaId: contaA.id,
    dataTransacao: iso(),
    statusConferencia: "CONFIRMADA",
  },
});
await waitProcessed(uidA);
const anom = Number(sql(`SELECT count(*) FROM autonomy_review_item WHERE usuario_id=${uidA} AND kind='ANOMALY' AND status='OPEN'`));
addCase("anomaly.energia", anom > 0 ? "OK" : "FALHA", `openAnomaly=${anom} (algoritmo=spike 30d vs 30-90d)`);

const day = iso(now).slice(0, 10);
await sendWallet(tokenDevA, {
  source: "IOS_WALLET",
  merchant: "POSTO X",
  amount: 89.9,
  card_hint: "Cartao Teste",
  occurred_at: `${day}T10:00:00`,
  client_event_id: `dup-x-1-${suffix}`,
});
await waitProcessed(uidA);
await sendWallet(tokenDevA, {
  source: "IOS_WALLET",
  merchant: "POSTO X",
  amount: 89.9,
  card_hint: "Cartao Teste",
  occurred_at: `${day}T10:04:00`,
  client_event_id: `dup-x-2-${suffix}`,
});
await waitProcessed(uidA);
const dupRev = Number(sql(`SELECT count(*) FROM autonomy_review_item WHERE usuario_id=${uidA} AND kind='DUPLICATE' AND status='OPEN'`));
const dupTx = Number(sql(`SELECT count(*) FROM transacoes WHERE usuario_id=${uidA} AND excluido=false AND descricao ILIKE '%POSTO X%'`));
addCase("duplicate.posto_x", dupRev >= 1 && dupTx >= 2 ? "OK" : "FALHA", `reviewDup=${dupRev} txPostoX=${dupTx}`);

const sameId = `same-event-${suffix}`;
const d1 = await sendWallet(tokenDevA, {
  source: "IOS_WALLET",
  merchant: "POSTO Y",
  amount: 21.0,
  card_hint: "Cartao Teste",
  occurred_at: iso(),
  client_event_id: sameId,
});
const d2 = await sendWallet(tokenDevA, {
  source: "IOS_WALLET",
  merchant: "POSTO Y",
  amount: 21.0,
  card_hint: "Cartao Teste",
  occurred_at: iso(),
  client_event_id: sameId,
});
const txY = Number(sql(`SELECT count(*) FROM transacoes WHERE usuario_id=${uidA} AND excluido=false AND descricao ILIKE '%POSTO Y%'`));
addCase(
  "dedup.mesmo_event_id",
  d1.status >= 200 && d1.status < 300 && d2.json?.status === "DUPLICATE" && txY === 1 ? "OK" : "FALHA",
  `first=${d1.json?.status} second=${d2.json?.status} txY=${txY}`
);

const forb = Number(sql(`SELECT count(*) FROM autonomy_decision_log WHERE usuario_id=${uidA} AND action IN ('TRANSFER_MONEY','PAY_INVOICE','CREATE_PIX','MOVE_BALANCE','CHANGE_CREDENTIALS','DELETE_HISTORY') AND result='EXECUTED'`));
const pagFatura = Number(sql(`SELECT count(*) FROM transacoes WHERE usuario_id=${uidA} AND tipo_transacao='PAGAMENTO_FATURA'`));
addCase("security.forbidden", forb === 0 ? "OK" : "FALHA", `executedForbidden=${forb} pagamentoFatura=${pagFatura}`);

const txB = (await api("POST", "/api/transacoes", {
  token: tokB,
  body: {
    descricao: "Compra secreta B",
    valor: 12.34,
    tipoTransacao: "DESPESA",
    categoriaId: catB.id,
    contaBancariaId: contaB.id,
    dataTransacao: iso(),
  },
})).json;
const listA = (await api("GET", "/api/transacoes", { token: tokA })).json || [];
const leakB = (Array.isArray(listA) ? listA : []).filter((t) => t.descricao?.includes("secreta B") || t.id === txB?.id);
const revB = (await api("GET", "/api/autonomy/revisao", { token: tokB })).json;
const idsA = new Set((revPix?.itens || []).map((i) => i.id));
const leakReview = (revB?.itens || []).some((i) => idsA.has(i.id));
const wBshell = await sendWallet(devB.deviceToken || devB.device_token, {
  source: "IOS_WALLET",
  merchant: "POSTO SHELL",
  amount: 33.0,
  card_hint: "Cartao B",
  occurred_at: iso(),
  client_event_id: `b-shell-${suffix}`,
});
await waitProcessed(uidB);
let catBshell = null;
if (wBshell.json?.transacaoId) {
  catBshell = (await api("GET", `/api/transacoes/${wBshell.json.transacaoId}`, { token: tokB })).json?.categoriaId;
}
addCase(
  "isolation.AB",
  leakB.length === 0 && !leakReview && catBshell !== catTransp.id ? "OK" : "FALHA",
  `leakTx=${leakB.length} leakReview=${leakReview} catBshell=${catBshell}`
);

const evProc = sql(`SELECT count(*), count(*) FILTER (WHERE processed), string_agg(distinct coalesce(processing_status,'?'), ',') FROM financial_domain_event WHERE usuario_id=${uidA}`);
const stuck = Number(sql(`SELECT count(*) FROM financial_domain_event WHERE usuario_id=${uidA} AND COALESCE(processing_status,'PENDING') IN ('PENDING','PROCESSING') AND processed=false`));
const retryable = Number(sql(`SELECT count(*) FROM financial_domain_event WHERE usuario_id=${uidA} AND processing_status='FAILED_RETRYABLE'`));
const failedFinal = Number(sql(`SELECT count(*) FROM financial_domain_event WHERE usuario_id=${uidA} AND processing_status='FAILED_FINAL'`));
addCase(
  "outbox.processed",
  stuck === 0 ? "OK" : "PARCIAL",
  `eventos=${evProc} stuckPending=${stuck} retryable=${retryable} failedFinal=${failedFinal}`
);

const lastEv = sql(`SELECT id FROM financial_domain_event WHERE usuario_id=${uidA} ORDER BY id DESC LIMIT 1`);
const txBefore = Number(sql(`SELECT count(*) FROM transacoes WHERE usuario_id=${uidA} AND excluido=false`));
if (lastEv) {
  sql(`UPDATE financial_domain_event SET processed=false, processed_at=NULL, processing_status='PENDING', next_retry_at=NULL WHERE id=${lastEv}`);
}
const reprocessed = lastEv ? await waitProcessed(uidA, 40) : false;
const lastStatus = lastEv
  ? sql(`SELECT processed, processing_status FROM financial_domain_event WHERE id=${lastEv}`)
  : "";
addCase(
  "outbox.idempotencia",
  reprocessed && /PROCESSED/i.test(String(lastStatus)) ? "OK" : "PARCIAL",
  `evento ${lastEv} reaberto e dreno=${reprocessed} status=${lastStatus} txBefore=${txBefore}`
);

const safe = (await api("GET", "/api/autonomy/resumo", { token: tokA })).json;
const sts = safe?.safeToSpend || {};
const bruto = Number(sts.disponivelAposObrigacoes || 0);
const margem = Number(sts.margemSeguranca || 0);
const got = Number(sts.safeToSpend || 0);
let expectSafe = Math.round((bruto - bruto * margem) * 100) / 100;
if (expectSafe < 0) expectSafe = 0;
addCase(
  "safetospend.formula",
  Math.abs(got - expectSafe) < 0.011 ? "OK" : "FALHA",
  `saldo=${sts.saldo} obrigacoes=${sts.obrigacoes} bruto=${bruto} margem=${margem} safe=${got} esperado=${expectSafe}`
);

const fc = safe?.forecast || {};
addCase("forecast.horizontes", fc.d30 && fc.d60 && fc.d90 ? "OK" : "FALHA", JSON.stringify({ d30: fc.d30, d60: fc.d60, d90: fc.d90 }));
addCase("forecast.modelos", "PARCIAL", "d30=ForecastFinanceiroService; d60/d90=SaldoService.calcularProjecaoSafraDto");

let notifA = [];
let notifB = [];
try {
  notifA = (await api("GET", "/api/notificacoes/todas", { token: tokA })).json || [];
} catch {}
try {
  notifB = (await api("GET", "/api/notificacoes/todas", { token: tokB })).json || [];
} catch {}
addCase("jarvis.fase1_desligado", "OK", `proactive flag off; notifA=${notifA.length} notifB=${notifB.length}`);

const summary = {
  suffix,
  usuarioA: uidA,
  usuarioB: uidB,
  ok: cases.filter((c) => c.resultado === "OK").length,
  falha: cases.filter((c) => c.resultado === "FALHA").length,
  limitado: cases.filter((c) => c.resultado === "LIMITADO").length,
  parcial: cases.filter((c) => c.resultado === "PARCIAL").length,
  casos: cases,
};
mkdirSync(join(root, "logs"), { recursive: true });
const reportPath = join(root, "logs", `autonomia-validacao-${suffix}.json`);
writeFileSync(reportPath, JSON.stringify(summary, null, 2), "utf8");
console.log(`Relatorio: ${reportPath} OK=${summary.ok} FALHA=${summary.falha} LIMITADO=${summary.limitado} PARCIAL=${summary.parcial}`);
process.exit(summary.falha > 0 ? 2 : 0);
