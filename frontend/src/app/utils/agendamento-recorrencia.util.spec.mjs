import { strict as assert } from 'node:assert';
import { describe, it } from 'node:test';
import { descricaoRecorrencia, diaEfetivoNoMes, labelRecorrencia } from './agendamento-recorrencia.util.ts';

describe('agendamento recorrencia', () => {
  it('recorrência mensal dia 31', () => {
    assert.equal(diaEfetivoNoMes(31, 28), 28);
    assert.equal(diaEfetivoNoMes(31, 30), 30);
  });

  it('descrição mensal dia 29+', () => {
    const d = descricaoRecorrencia('MENSAL', 31);
    assert.ok(d.includes('último dia válido'));
  });

  it('labels humanos', () => {
    assert.equal(labelRecorrencia('UNICA'), 'Uma vez');
    assert.equal(labelRecorrencia('ANUAL'), 'Anualmente');
  });
});

console.log('agendamento-recorrencia.util.spec: OK');
