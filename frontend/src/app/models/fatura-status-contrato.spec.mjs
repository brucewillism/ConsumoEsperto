// Teste de contrato: o enum StatusFatura do frontend deve ser idêntico ao do backend
// (Fatura.StatusFatura). Impede nova divergência como a antiga FECHADA/PENDENTE.
import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

const here = dirname(fileURLToPath(import.meta.url));

// Lista canônica compartilhada — sincronizada com o backend.
const STATUS_CANONICOS = ['ABERTA', 'VENCIDA', 'PAGA', 'PARCIAL', 'PREVISTA', 'CANCELADA'];

function extrairValoresEnumFrontend() {
  const src = readFileSync(resolve(here, 'fatura.model.ts'), 'utf8');
  const bloco = src.match(/export enum StatusFatura\s*\{([\s\S]*?)\}/);
  assert.ok(bloco, 'enum StatusFatura não encontrado em fatura.model.ts');
  return [...bloco[1].matchAll(/(\w+)\s*=\s*'(\w+)'/g)].map((m) => m[2]);
}

function extrairValoresEnumBackend() {
  // here = frontend/src/app/models → 4 níveis acima = raiz do repositório
  const caminho = resolve(
    here,
    '../../../..',
    'backend/src/main/java/com/consumoesperto/model/Fatura.java'
  );
  if (!existsSync(caminho)) {
    return null; // frontend isolado (sem monorepo) — valida contra lista canônica
  }
  const src = readFileSync(caminho, 'utf8');
  const bloco = src.match(/public enum StatusFatura\s*\{([\s\S]*?)\n\s*\}/);
  assert.ok(bloco, 'enum StatusFatura não encontrado em Fatura.java');
  const corpo = bloco[1].split(';')[0]; // somente as constantes, antes de métodos
  return [...corpo.matchAll(/\b([A-Z][A-Z_]+)\b/g)].map((m) => m[1]);
}

test('StatusFatura do frontend é idêntico à lista canônica', () => {
  assert.deepEqual(extrairValoresEnumFrontend().sort(), [...STATUS_CANONICOS].sort());
});

test('StatusFatura do backend é idêntico à lista canônica (quando monorepo disponível)', (t) => {
  const backend = extrairValoresEnumBackend();
  if (backend === null) {
    t.skip('backend não disponível neste checkout');
    return;
  }
  assert.deepEqual(backend.sort(), [...STATUS_CANONICOS].sort());
});

test('frontend e backend compartilham exatamente os mesmos status', (t) => {
  const backend = extrairValoresEnumBackend();
  if (backend === null) {
    t.skip('backend não disponível neste checkout');
    return;
  }
  assert.deepEqual(extrairValoresEnumFrontend().sort(), backend.sort());
});
