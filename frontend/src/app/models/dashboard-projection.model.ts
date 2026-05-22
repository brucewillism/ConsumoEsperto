export interface SimulacaoImpacto {
  id: string;
  descricao: string;
  valorMensalImpacto: number;
  mesesImpacto: number;
  metaDescricao?: string;
  icone?: string;
  ativa: boolean;
  mensagem?: string;
  impactoScore?: number;
  criadaEm?: string;
}

export interface TimelineImpacto {
  titulo: string;
  icone: string;
  mesesOriginais: number;
  mesesProjetados: number;
  deslocamentoMeses: number;
}

/** Resposta bruta do backend — um mês da safra cascata. */
export interface ProjecaoMesResumoDTO {
  competencia: string;
  rotuloMes: string;
  patrimonioInicial: number;
  patrimonioLiquido: number;
  receitasPrevistas: number;
  receitasFiscaisPrevistas: number;
  despesasPrevistas: number;
  saldoProjetadoFimMes: number;
}

/** Visão normalizada de um mês (M, M+1, M+2) para UI e gráficos. */
export interface SerieProjecaoSafraDTO {
  mesReferencia: string;
  saldoInicial: number;
  saldoFinalProjetado: number;
  burnRateEstimado: number;
  receitasFiscaisPrevistas: number;
}

/** Container API — campo `safraPatrimonio` do dashboard. */
export interface SafraPatrimonioDTO {
  meses: ProjecaoMesResumoDTO[];
}

export interface DashboardProjection {
  labels: string[];
  real: Array<number | null>;
  projetado: number[];
  simulado: number[];
  timelineImpacto: TimelineImpacto[];
  simulacoesAtivas: SimulacaoImpacto[];
  safraPatrimonio?: SafraPatrimonioDTO | null;
}

export function normalizarMesSafra(raw: ProjecaoMesResumoDTO): SerieProjecaoSafraDTO {
  const diasNoMes = diasNoCompetencia(raw.competencia);
  const burnMensal = Number(raw.despesasPrevistas) || 0;
  return {
    mesReferencia: raw.competencia || raw.rotuloMes,
    saldoInicial: Number(raw.patrimonioInicial ?? raw.patrimonioLiquido) || 0,
    saldoFinalProjetado: Number(raw.saldoProjetadoFimMes) || 0,
    burnRateEstimado: diasNoMes > 0 ? burnMensal / diasNoMes : burnMensal,
    receitasFiscaisPrevistas: Number(raw.receitasFiscaisPrevistas) || 0,
  };
}

export function normalizarSafra(api?: SafraPatrimonioDTO | null): SerieProjecaoSafraDTO[] {
  if (!api?.meses?.length) {
    return [];
  }
  return api.meses.map(normalizarMesSafra);
}

function diasNoCompetencia(competencia: string): number {
  const m = /^(\d{4})-(\d{2})$/.exec(competencia || '');
  if (!m) {
    return 30;
  }
  const y = Number(m[1]);
  const mo = Number(m[2]);
  return new Date(y, mo, 0).getDate();
}
