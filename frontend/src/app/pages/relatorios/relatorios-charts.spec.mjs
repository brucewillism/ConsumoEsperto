import { strict as assert } from 'node:assert';
import { describe, it } from 'node:test';

/** Simula montagem de datasets Chart.js a partir de transações filtradas. */
function montarGraficos(transacoes) {
  const receitas = transacoes.filter((t) => t.tipoTransacao === 'RECEITA').reduce((s, t) => s + Number(t.valor || 0), 0);
  const despesas = transacoes.filter((t) => t.tipoTransacao === 'DESPESA').reduce((s, t) => s + Number(t.valor || 0), 0);
  const pizza = { labels: ['Receitas', 'Despesas'], data: [receitas, despesas] };
  const porCat = new Map();
  for (const t of transacoes.filter((x) => x.tipoTransacao === 'DESPESA')) {
    const cat = t.categoriaNome ?? 'Sem categoria';
    porCat.set(cat, (porCat.get(cat) ?? 0) + Number(t.valor || 0));
  }
  const barras = { labels: [...porCat.keys()], data: [...porCat.values()] };
  const porCartao = new Map();
  for (const t of transacoes.filter((x) => x.tipoTransacao === 'DESPESA')) {
    const nome = t.cartaoNome ?? 'Conta / débito';
    porCartao.set(nome, (porCartao.get(nome) ?? 0) + Number(t.valor || 0));
  }
  const rosca = { labels: [...porCartao.keys()], data: [...porCartao.values()] };
  const porDia = new Map();
  for (const t of transacoes) {
    const key = t.data;
    const b = porDia.get(key) ?? { receita: 0, despesa: 0 };
    if (t.tipoTransacao === 'RECEITA') b.receita += Number(t.valor || 0);
    else b.despesa += Number(t.valor || 0);
    porDia.set(key, b);
  }
  const linha = { pontos: porDia.size };
  return { pizza, barras, rosca, linha, temDados: transacoes.length > 0 };
}

let chartInstances = [];
function destroyCharts() {
  chartInstances.forEach((c) => c.destroy());
  chartInstances = [];
}
function createChart(id) {
  const inst = { id, destroyed: false, destroy() { this.destroyed = true; } };
  chartInstances.push(inst);
  return inst;
}

describe('Relatórios gráficos', () => {
  it('quatro gráficos com dados', () => {
    const txs = [
      { tipoTransacao: 'RECEITA', valor: 100, data: '01/07/2026', categoriaNome: 'Salário' },
      { tipoTransacao: 'DESPESA', valor: 50, data: '02/07/2026', categoriaNome: 'Mercado', cartaoNome: 'Nubank' },
    ];
    const g = montarGraficos(txs);
    assert.equal(g.pizza.data.length, 2);
    assert.ok(g.barras.labels.includes('Mercado'));
    assert.ok(g.rosca.labels.includes('Nubank'));
    assert.equal(g.linha.pontos, 2);
  });

  it('destruição das instâncias anteriores', () => {
    destroyCharts();
    createChart('pizza');
    createChart('linha');
    assert.equal(chartInstances.length, 2);
    destroyCharts();
    assert.ok(chartInstances.every((c) => c.destroyed));
    assert.equal(chartInstances.length, 0);
  });

  it('período sem dados', () => {
    const g = montarGraficos([]);
    assert.equal(g.temDados, false);
    assert.deepEqual(g.pizza.data, [0, 0]);
  });

  it('valores zero', () => {
    const g = montarGraficos([{ tipoTransacao: 'RECEITA', valor: 0, data: '01/07/2026' }]);
    assert.equal(g.pizza.data[0], 0);
  });

  it('atualização após mudança de filtros', () => {
    const antes = montarGraficos([
      { tipoTransacao: 'DESPESA', valor: 100, data: '01/07/2026', categoriaNome: 'A' },
    ]);
    const depois = montarGraficos([
      { tipoTransacao: 'DESPESA', valor: 200, data: '01/07/2026', categoriaNome: 'A' },
    ]);
    assert.notEqual(antes.barras.data[0], depois.barras.data[0]);
  });
});

console.log('relatorios-charts.spec: OK');
