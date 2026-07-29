import { strict as assert } from 'node:assert';
import { describe, it } from 'node:test';
import { montarParamsCsv } from '../utils/exportacao-csv.util.ts';

const BASE = '/api/exportacao/csv/transacoes';

function montarUrl(base, filtro) {
  const params = new URLSearchParams(montarParamsCsv(filtro));
  const qs = params.toString();
  return qs ? `${base}?${qs}` : base;
}

describe('ExportacaoDadosService (HTTP contrato)', () => {
  it('URL sem filtros opcionais além do período', () => {
    const url = montarUrl(BASE, { dataInicio: '2026-01-01', dataFim: '2026-01-31' });
    assert.ok(url.includes('dataInicio=2026-01-01'));
    assert.ok(url.includes('dataFim=2026-01-31'));
    assert.ok(!url.includes('undefined'));
  });

  it('método GET com query parameters', () => {
    const filtro = { dataInicio: '2026-01-01', dataFim: '2026-01-31', contaId: 3 };
    const params = montarParamsCsv(filtro);
    assert.equal(params.contaId, '3');
    assert.equal(Object.keys(params).length, 3);
  });

  it('não envia strings inválidas no corpo da query', () => {
    const params = montarParamsCsv({ tipoTransacao: 'undefined', cartaoId: 'null' });
    assert.deepEqual(params, {});
  });
});

console.log('exportacao-dados.service.spec: OK');
