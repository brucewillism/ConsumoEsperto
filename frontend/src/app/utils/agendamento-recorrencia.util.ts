/** Descrições humanas de recorrência (espelha backend AgendamentoRecorrenciaUtil + VencimentoMensalUtil). */

export const RECORRENCIAS = [
  'UNICA',
  'DIARIA',
  'SEMANAL',
  'QUINZENAL',
  'MENSAL',
  'BIMESTRAL',
  'TRIMESTRAL',
  'SEMESTRAL',
  'ANUAL',
] as const;

export type RecorrenciaCodigo = (typeof RECORRENCIAS)[number];

const LABELS: Record<string, string> = {
  UNICA: 'Uma vez',
  DIARIA: 'Diariamente',
  SEMANAL: 'Semanalmente',
  QUINZENAL: 'Quinzenalmente',
  MENSAL: 'Mensalmente',
  BIMESTRAL: 'Bimestralmente',
  TRIMESTRAL: 'Trimestralmente',
  SEMESTRAL: 'Semestralmente',
  ANUAL: 'Anualmente',
};

/** Regra mensal: dia configurado; se o mês não tiver o dia, usa o último dia válido. */
export function diaEfetivoNoMes(diaDesejado: number, ultimoDiaMes: number): number {
  return Math.min(Math.max(1, diaDesejado), ultimoDiaMes);
}

export function descricaoRecorrencia(recorrencia: string, diaVencimentoMensal?: number | null): string {
  const base = LABELS[recorrencia] ?? recorrencia ?? '—';
  const periodicidadesMensais = new Set(['MENSAL', 'BIMESTRAL', 'TRIMESTRAL', 'SEMESTRAL']);
  if (!periodicidadesMensais.has(recorrencia) || !diaVencimentoMensal) {
    return base;
  }
  if (diaVencimentoMensal >= 29) {
    return `${base} (dia ${diaVencimentoMensal}; em meses curtos usa o último dia válido do mês)`;
  }
  return `${base} (dia ${diaVencimentoMensal})`;
}

export function labelRecorrencia(recorrencia: string): string {
  return LABELS[recorrencia] ?? recorrencia ?? '—';
}
