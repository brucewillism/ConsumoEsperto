import { PrevisaoFuturoChart } from '../services/projecao-dashboard.service';

/** Segmento do letreiro Oráculo (HUD — cores por indicador). */
export interface TickerMercadoSegmento {
  text: string;
  kind: 'selic' | 'ipca' | 'dolar' | 'meta';
}

/** Linha exibida em “Transações recentes” no HUD. */
export interface RecentTxRow {
  id?: number;
  description: string;
  amount: number;
  category: string;
  date: Date;
  type: 'credit' | 'debit';
  showJurosWarning?: boolean;
}

/**
 * Estado único Mark II — projeção Sentinela + ticker + flags de protocolo/radar.
 * Emitido pelo {@link DashboardService}; o dashboard apenas espelha para o template.
 */
export interface EstadoDashboardCompleto {
  previsaoFuturoChart: PrevisaoFuturoChart | null;
  tickerMercadoSegmentos: TickerMercadoSegmento[];
  preservarGraficoPosProtocolo: boolean;
  novoFuturoProtocolo: boolean;
  protocoloOtimizacaoEmAndamento: boolean;
  /** Radar do ticker e do gráfico — mesma expressão, mesmo tick de emissão */
  radarPulsoHud: boolean;
}
