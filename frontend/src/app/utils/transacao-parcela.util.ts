import { Transacao } from '../models/transacao.model';

const SUFIXO_PARCELA = /\(\d+\/\d+\)\s*$/;

/**
 * Descrição com sufixo (k/n) quando {@code totalParcelas > 1}, sem duplicar se a API já enviou.
 */
export function descricaoComIndicadorParcela(
  t: Pick<Transacao, 'descricao' | 'parcelaAtual' | 'totalParcelas'>
): string {
  const d = (t.descricao || '').trim();
  const n = t.totalParcelas;
  const k = t.parcelaAtual;
  if (n != null && n > 1 && k != null) {
    if (SUFIXO_PARCELA.test(d)) {
      return d;
    }
    return `${d} (${k}/${n})`;
  }
  return d || '—';
}

function temJurosMetadadoOuCalculo(t: Transacao): boolean {
  const vj = t.valorComJuros != null ? Number(t.valorComJuros) : 0;
  if (vj > 0.005) {
    return true;
  }
  const vr = t.valorReal != null ? Number(t.valorReal) : null;
  const n = t.totalParcelas;
  const v = t.valor != null ? Number(t.valor) : null;
  if (vr != null && n != null && v != null && n >= 1) {
    return v * n > vr + 0.02;
  }
  return false;
}

/** Marca {@code grupoParcelaId} quando qualquer parcela do grupo indica juros. */
export function buildGrupoParcelamentoTemJuros(transacoes: Transacao[]): Map<string, boolean> {
  const m = new Map<string, boolean>();
  for (const t of transacoes) {
    if (!t.grupoParcelaId) {
      continue;
    }
    if (temJurosMetadadoOuCalculo(t)) {
      m.set(t.grupoParcelaId, true);
    }
  }
  return m;
}

export function transacaoMostraBadgeJuros(t: Transacao, gruposJuros?: Map<string, boolean>): boolean {
  if (temJurosMetadadoOuCalculo(t)) {
    return true;
  }
  if (t.grupoParcelaId && gruposJuros?.get(t.grupoParcelaId)) {
    return true;
  }
  return false;
}

export const TOOLTIP_JUROS_TRANSACAO = 'Esta compra incluiu juros';
