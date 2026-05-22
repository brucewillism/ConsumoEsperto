import { SafraPatrimonioDTO, SerieProjecaoSafraDTO, normalizarSafra } from './dashboard-projection.model';

export interface ForecastFinanceiro {
  diaAtual: number;
  diasNoMes: number;
  rendaLiquida: number;
  gastoAtual: number;
  mediaDiaria: number;
  gastoProjetado: number;
  patrimonioLiquido?: number;
  receitasPrevistas?: number;
  receitasFiscaisPrevistas?: number;
  despesasPrevistas?: number;
  saldoProjetado: number;
  probabilidadeVermelho: number;
  nivelRisco: string;
  mensagemIa: string;
  maioresCategorias: string[];
  safraPatrimonio?: SafraPatrimonioDTO | null;
}

export function safraDoForecast(forecast: ForecastFinanceiro | null | undefined): SerieProjecaoSafraDTO[] {
  return normalizarSafra(forecast?.safraPatrimonio);
}
