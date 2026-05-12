import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

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

export interface DashboardProjection {
  labels: string[];
  real: Array<number | null>;
  projetado: number[];
  simulado: number[];
  timelineImpacto: TimelineImpacto[];
  simulacoesAtivas: SimulacaoImpacto[];
}

export interface OportunidadeInvestimento {
  saldoOcioso: number;
  rendimentoPoupanca: number;
  rendimentoTesouroSelic: number;
  rendimentoCdb: number;
  melhorOpcao: string;
  explicacaoIa: string;
}

export interface PrevisaoFuturoPonto {
  dia: number;
  saldo: number;
  serie: 'REAL' | 'PROJETADO';
}

export interface MarketIndicatorsHud {
  selicAa?: number | null;
  ipcaMes?: number | null;
  dolarBrl?: number | null;
  fonteResumo?: string;
  dadosParciais?: boolean;
}

export interface ProvisaoMemoriaHud {
  diaAlvo: number;
  valor: number;
  rotulo?: string;
  periodoHistorico?: string;
  contextoOrigem?: string;
}

export interface PrevisaoFuturoChart {
  diaHoje: number;
  ultimoDiaMes: number;
  saldoAtual: number;
  saldoProjetadoFimMes: number;
  projecaoNegativa: boolean;
  protocoloOtimizacaoRecomendado?: boolean;
  mesesEscudoEnergia?: number | null;
  diasAteSaldoNegativo?: number | null;
  /** Dias com vencimento de obrigações fixas (Sentinela). */
  diasVencimentoDespesasFixas?: number[];
  /** Projeção — marcos de “gasto fantasma” (memória sazonal). */
  diasProvisaoMemoria?: number[];
  provisoesMemoria?: ProvisaoMemoriaHud[];
  indicadoresMercado?: MarketIndicatorsHud;
  fatorCorrecaoInflacao?: number | null;
  notaJarvisMercado?: string;
  pontos: PrevisaoFuturoPonto[];
}

export interface ProtocoloOtimizacaoAjuste {
  orcamentoId?: number;
  categoriaNome?: string;
  limiteAnterior?: number;
  limiteNovo?: number;
}

export interface ProtocoloOtimizacaoResponse {
  mensagemJarvis?: string;
  fatorAjusteEmergenciaMedio?: number;
  percentualMedioReducaoTetos?: number;
  sobrevidaSaldoProjetado?: number;
  novoSaldoProjetadoFimMes?: number;
  ajustes?: ProtocoloOtimizacaoAjuste[];
  previsaoAjustada?: PrevisaoFuturoChart;
}

@Injectable({ providedIn: 'root' })
export class ProjecaoDashboardService {
  private readonly base = `${environment.apiUrl}/projecoes`;
  private readonly jarvisBase = `${environment.apiUrl}/jarvis`;

  constructor(private http: HttpClient) {}

  dashboard(): Observable<DashboardProjection> {
    return this.http.get<DashboardProjection>(`${this.base}/dashboard`);
  }

  definirSimulacoesAtivas(ativa: boolean): Observable<SimulacaoImpacto[]> {
    return this.http.patch<SimulacaoImpacto[]>(`${this.base}/simulacoes/ativas`, { ativa });
  }

  oportunidadeInvestimento(): Observable<OportunidadeInvestimento> {
    return this.http.get<OportunidadeInvestimento>(`${this.base}/oportunidade-investimento`);
  }

  /** Sentinela — série saldo real vs projeção até fim do mês. */
  previsaoFuturo(): Observable<PrevisaoFuturoChart> {
    return this.http.get<PrevisaoFuturoChart>(`${this.base}/previsao-futuro`);
  }

  /** Protocolo de otimização — ajuste de tetos em categorias não essenciais + série projetada. */
  otimizarProtocoloMetas(): Observable<ProtocoloOtimizacaoResponse> {
    return this.http.patch<ProtocoloOtimizacaoResponse>(`${this.jarvisBase}/otimizar-metas`, {});
  }
}
