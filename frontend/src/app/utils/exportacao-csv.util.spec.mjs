import { strict as assert } from 'node:assert';
import { describe, it } from 'node:test';
import {
  montarParamsCsv,
  validarIntervaloDatas,
  extrairNomeArquivoContentDisposition,
  nomeArquivoCsvFallback,
  resolverNomeArquivoCsv,
  blobCsvVazio,
  baixarBlobComRevogacao,
} from './exportacao-csv.util.ts';

describe('montarParamsCsv', () => {
  it('nenhum filtro', () => {
    assert.deepEqual(montarParamsCsv({}), {});
  });

  it('apenas período', () => {
    assert.deepEqual(montarParamsCsv({ dataInicio: '2026-01-01', dataFim: '2026-01-31' }), {
      dataInicio: '2026-01-01',
      dataFim: '2026-01-31',
    });
  });

  it('conta', () => {
    assert.deepEqual(montarParamsCsv({ contaId: 5 }), { contaId: '5' });
  });

  it('cartão', () => {
    assert.deepEqual(montarParamsCsv({ cartaoId: 12 }), { cartaoId: '12' });
  });

  it('categoria', () => {
    assert.deepEqual(montarParamsCsv({ categoriaId: 3 }), { categoriaId: '3' });
  });

  it('tipo', () => {
    assert.deepEqual(montarParamsCsv({ tipoTransacao: 'DESPESA' }), { tipoTransacao: 'DESPESA' });
  });

  it('status', () => {
    assert.deepEqual(montarParamsCsv({ statusConferencia: 'PENDENTE' }), { statusConferencia: 'PENDENTE' });
  });

  it('combinação de todos', () => {
    const p = montarParamsCsv({
      dataInicio: '2026-02-01',
      dataFim: '2026-02-28',
      contaId: 1,
      cartaoId: 2,
      categoriaId: 3,
      tipoTransacao: 'RECEITA',
      statusConferencia: 'CONFIRMADA',
      descricaoContem: 'mercado',
    });
    assert.equal(Object.keys(p).length, 8);
    assert.equal(p.descricaoContem, 'mercado');
  });

  it('ignora undefined, null e strings inválidas', () => {
    assert.deepEqual(
      montarParamsCsv({
        contaId: 'undefined',
        cartaoId: 'null',
        tipoTransacao: '',
        descricaoContem: '  ',
      }),
      {}
    );
  });
});

describe('validarIntervaloDatas', () => {
  it('data inicial maior que final', () => {
    assert.equal(validarIntervaloDatas('2026-03-01', '2026-02-01'), 'A data inicial não pode ser posterior à data final.');
  });

  it('intervalo válido', () => {
    assert.equal(validarIntervaloDatas('2026-01-01', '2026-01-31'), null);
  });
});

describe('Content-Disposition', () => {
  it('nome recebido pelo header UTF-8', () => {
    const nome = extrairNomeArquivoContentDisposition("attachment; filename*=UTF-8''transa%C3%A7%C3%B5es.csv");
    assert.equal(nome, 'transações.csv');
  });

  it('nome quoted', () => {
    assert.equal(extrairNomeArquivoContentDisposition('attachment; filename="export.csv"'), 'export.csv');
  });

  it('fallback de nome', () => {
    const ref = new Date(2026, 6, 28);
    assert.equal(nomeArquivoCsvFallback(ref), 'transacoes-2026-07-28.csv');
    assert.equal(resolverNomeArquivoCsv(null, ref), 'transacoes-2026-07-28.csv');
  });
});

describe('blobCsvVazio', () => {
  it('blob vazio', () => {
    assert.equal(blobCsvVazio(new Blob([])), true);
    assert.equal(blobCsvVazio(new Blob(['a'])), false);
  });
});

describe('baixarBlobComRevogacao', () => {
  it('download bem-sucedido revoga URL', () => {
    let revoked = false;
    let clicked = false;
    baixarBlobComRevogacao(new Blob(['x']), 'test.csv', {
      createObjectURL: () => 'blob:mock',
      revokeObjectURL: () => { revoked = true; },
      document: {
        createElement: () => ({ click: () => { clicked = true; } }),
      },
    });
    assert.equal(clicked, true);
    assert.equal(revoked, true);
  });
});

console.log('exportacao-csv.util.spec: OK');
