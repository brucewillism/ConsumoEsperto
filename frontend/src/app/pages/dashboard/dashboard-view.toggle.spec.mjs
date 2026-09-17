import { describe, it } from 'node:test';
import assert from 'node:assert/strict';

const KEY = 'ce.dashboard.viewMode';

function ler(storage) {
  const raw = storage.getItem(KEY);
  return raw === 'GENERAL' ? 'GENERAL' : 'MONTHLY';
}

function gravar(mode, storage) {
  storage.setItem(KEY, mode);
}

function cardsDaVisao(mode) {
  if (mode === 'MONTHLY') {
    return ['Receitas do mês', 'Despesas do mês', 'Resultado do mês', 'Projeção do mês', 'Fatura do mês', 'Parcela do mês', 'Safe-to-spend do mês'];
  }
  return ['Ativos (contas + investimentos)', 'Total em contas', 'Total investido', 'Passivos (saldo devedor)', 'Patrimônio líquido', 'Dívida total', 'Reservas (liquidez imediata)', 'Folga patrimonial', 'Score'];
}

function recarregaBackend(modeAnterior, modeNovo) {
  if (modeAnterior === modeNovo) {
    return null;
  }
  return `/api/dashboard?view=${modeNovo}`;
}

describe('alternância das visões do dashboard', () => {
  it('padrão Mensal e persiste Geral', () => {
    const mem = {};
    const storage = {
      getItem: (k) => mem[k] ?? null,
      setItem: (k, v) => { mem[k] = v; },
    };
    assert.equal(ler(storage), 'MONTHLY');
    gravar('GENERAL', storage);
    assert.equal(ler(storage), 'GENERAL');
    assert.equal(mem[KEY], 'GENERAL');
  });

  it('Mensal não lista saldo devedor nem dívida total', () => {
    const titulos = cardsDaVisao('MONTHLY');
    assert.ok(titulos.includes('Parcela do mês'));
    assert.ok(titulos.includes('Fatura do mês'));
    assert.ok(titulos.includes('Safe-to-spend do mês'));
    assert.equal(titulos.includes('Saldo devedor'), false);
    assert.equal(titulos.includes('Dívida total'), false);
    assert.equal(titulos.includes('Folga patrimonial'), false);
  });

  it('Geral não lista parcela do mês nem safe-to-spend', () => {
    const titulos = cardsDaVisao('GENERAL');
    assert.ok(titulos.includes('Saldo devedor') || titulos.includes('Passivos (saldo devedor)'));
    assert.ok(titulos.includes('Dívida total'));
    assert.ok(titulos.includes('Folga patrimonial'));
    assert.equal(titulos.includes('Parcela do mês'), false);
    assert.equal(titulos.includes('Safe-to-spend do mês'), false);
  });

  it('troca de visão dispara novo GET no backend', () => {
    assert.equal(recarregaBackend('MONTHLY', 'MONTHLY'), null);
    assert.equal(recarregaBackend('MONTHLY', 'GENERAL'), '/api/dashboard?view=GENERAL');
    assert.equal(recarregaBackend('GENERAL', 'MONTHLY'), '/api/dashboard?view=MONTHLY');
  });
});
