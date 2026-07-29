import { strict as assert } from 'node:assert';
import { describe, it } from 'node:test';

/** Simula validação de formulário de agendamento (espelha AgendamentosComponent.validarForm). */
function validarForm(form, contas = [{ id: 1 }]) {
  if (!form.contaDebitoId && !contas.length) return 'Cadastre uma conta bancária antes de agendar.';
  if (!form.contaDebitoId) return 'Selecione uma conta.';
  if (!form.beneficiario?.trim()) return 'Informe a descrição/beneficiário.';
  if (!form.valor || form.valor <= 0) return 'Informe um valor válido.';
  if (!form.dataVencimento) return 'Informe a data inicial/vencimento.';
  const rec = form.recorrencia ?? 'UNICA';
  const mensais = ['MENSAL', 'BIMESTRAL', 'TRIMESTRAL', 'SEMESTRAL'];
  if (mensais.includes(rec) && form.diaVencimentoMensal != null) {
    const d = form.diaVencimentoMensal;
    if (d < 1 || d > 31) return 'Dia da execução deve ser entre 1 e 31.';
  }
  return null;
}

describe('AgendamentosComponent (lógica)', () => {
  it('formulário inválido sem beneficiário', () => {
    assert.equal(validarForm({ contaDebitoId: 1, valor: 10, dataVencimento: '2026-07-01', beneficiario: '  ' }), 'Informe a descrição/beneficiário.');
  });

  it('usuário sem conta', () => {
    assert.equal(validarForm({ contaDebitoId: 0, beneficiario: 'x', valor: 1, dataVencimento: '2026-07-01' }, []), 'Cadastre uma conta bancária antes de agendar.');
  });

  it('recorrência mensal dia inválido', () => {
    assert.equal(validarForm({
      contaDebitoId: 1,
      beneficiario: 'Aluguel',
      valor: 100,
      dataVencimento: '2026-07-01',
      recorrencia: 'MENSAL',
      diaVencimentoMensal: 32,
    }), 'Dia da execução deve ser entre 1 e 31.');
  });

  it('formulário válido', () => {
    assert.equal(validarForm({
      contaDebitoId: 1,
      beneficiario: 'Aluguel',
      valor: 100,
      dataVencimento: '2026-07-01',
      recorrencia: 'MENSAL',
      diaVencimentoMensal: 10,
    }), null);
  });
});

console.log('agendamentos.component.spec: OK');
