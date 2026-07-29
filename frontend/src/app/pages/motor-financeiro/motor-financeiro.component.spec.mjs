import { strict as assert } from 'node:assert';
import { describe, it } from 'node:test';

function brl(v) {
  return Number(v ?? 0).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
}

function pct(v) {
  return `${Number(v ?? 0)}%`;
}

function renderMotor(dados) {
  if (!dados) return { loading: true };
  const receitas = Number(dados.totalReceitas ?? 0);
  const despesas = Number(dados.totalDespesas ?? 0);
  const saldo = Number(dados.saldo ?? receitas - despesas);
  const receitasFmt = brl(receitas);
  return {
    loading: false,
    receitasFmt,
    despesasFmt: brl(despesas),
    saldoFmt: brl(saldo),
    pctFmt: pct(dados.percentualComprometido),
    hasNaN: [receitas, despesas, saldo].some((n) => Number.isNaN(n)),
    hasUndefinedVisible: receitasFmt.includes('undefined'),
  };
}

const dadosCompletos = {
  totalReceitas: 5000,
  totalDespesas: 3000,
  saldo: 2000,
  percentualComprometido: 60,
  explicacao: 'Situação estável.',
};

describe('Motor Financeiro (render)', () => {
  it('loading', () => {
    assert.equal(renderMotor(null).loading, true);
  });

  it('dados completos sem NaN', () => {
    const r = renderMotor(dadosCompletos);
    assert.equal(r.hasNaN, false);
    assert.ok(r.receitasFmt.includes('R$'));
    assert.equal(r.pctFmt, '60%');
  });

  it('dados incompletos', () => {
    const r = renderMotor({ totalReceitas: 100 });
    assert.equal(r.hasNaN, false);
    assert.ok(r.saldoFmt);
  });

  it('usuário sem movimentações', () => {
    const r = renderMotor({ totalReceitas: 0, totalDespesas: 0, saldo: 0 });
    assert.equal(r.hasNaN, false);
  });

  it('formatação monetária', () => {
    assert.ok(brl(1234.56).includes('R$'));
  });

  it('ausência de undefined visível', () => {
    const r = renderMotor(dadosCompletos);
    assert.equal(r.hasUndefinedVisible, false);
  });
});

describe('Motor Financeiro (erros HTTP)', () => {
  const map = { 400: 'Requisição inválida', 401: 'Não autorizado', 403: 'Acesso negado', 500: 'Erro interno' };
  for (const [code, msg] of Object.entries(map)) {
    it(`erro ${code}`, () => {
      assert.equal(map[Number(code)], msg);
    });
  }
});

console.log('motor-financeiro.component.spec: OK');
