import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import {
  DashboardProjection,
  PrevisaoFuturoChart,
  ProjecaoDashboardService,
  ProtocoloOtimizacaoResponse,
} from './projecao-dashboard.service';
import { EstadoDashboardCompleto, TickerMercadoSegmento } from '../models/jarvis-hud.model';

const ESTADO_INICIAL: EstadoDashboardCompleto = {
  previsaoFuturoChart: null,
  tickerMercadoSegmentos: [],
  preservarGraficoPosProtocolo: false,
  novoFuturoProtocolo: false,
  protocoloOtimizacaoEmAndamento: false,
  radarPulsoHud: false,
};

/**
 * Facade do painel — projeções Mark II com estado HUD central (BehaviorSubject).
 */
@Injectable({ providedIn: 'root' })
export class DashboardService {
  private readonly estadoSubject = new BehaviorSubject<EstadoDashboardCompleto>(ESTADO_INICIAL);

  /** Estado fundido (real vs série preservada) — fonte única para HUD / Sentinela. */
  readonly estadoDashboardCompleto$: Observable<EstadoDashboardCompleto> = this.estadoSubject.asObservable();

  constructor(private projecao: ProjecaoDashboardService) {}

  get estadoDashboardSnapshot(): EstadoDashboardCompleto {
    return this.estadoSubject.value;
  }

  /** Antes de recarregar dados (não silencioso): libera substituição total da previsão. */
  prepararNovaRecargaCompleta(): void {
    this.aplicarPatch({
      preservarGraficoPosProtocolo: false,
      novoFuturoProtocolo: false,
    });
  }

  /**
   * Após fetch do painel: substitui previsão ou funde só mercado se a série estiver preservada.
   */
  sincronizarPrevisaoAposFetch(payload: PrevisaoFuturoChart | null | undefined): void {
    const cur = this.estadoSubject.value;
    if (!cur.preservarGraficoPosProtocolo) {
      this.definirPrevisaoETicker(payload ?? null);
      return;
    }
    if (payload) {
      this.fundirCamposMercadoPrevisaoNoEstadoAtual(payload);
    }
  }

  /** Resposta do protocolo de otimização — nova série + preservar + marco visual. */
  aplicarPrevisaoPosOtimizacao(chart: PrevisaoFuturoChart): void {
    this.aplicarPatch({
      previsaoFuturoChart: chart,
      tickerMercadoSegmentos: DashboardService.montarSegmentosTicker(chart),
      preservarGraficoPosProtocolo: true,
      novoFuturoProtocolo: true,
    });
  }

  setProtocoloOtimizacaoEmAndamento(emAndamento: boolean): void {
    this.aplicarPatch({ protocoloOtimizacaoEmAndamento: emAndamento });
  }

  private aplicarPatch(patch: Partial<EstadoDashboardCompleto>): void {
    const cur = this.estadoSubject.value;
    const next: EstadoDashboardCompleto = {
      ...cur,
      ...patch,
    };
    if (patch.previsaoFuturoChart !== undefined && patch.tickerMercadoSegmentos === undefined) {
      next.tickerMercadoSegmentos = DashboardService.montarSegmentosTicker(patch.previsaoFuturoChart);
    }
    next.radarPulsoHud =
      next.protocoloOtimizacaoEmAndamento || next.novoFuturoProtocolo;
    this.estadoSubject.next(next);
  }

  private definirPrevisaoETicker(chart: PrevisaoFuturoChart | null): void {
    this.aplicarPatch({
      previsaoFuturoChart: chart,
      tickerMercadoSegmentos: DashboardService.montarSegmentosTicker(chart),
    });
  }

  /** Mescla Oráculo / delta do payload novo sem substituir pontos da série preservada. */
  private fundirCamposMercadoPrevisaoNoEstadoAtual(fonte: PrevisaoFuturoChart): void {
    const alvo = this.estadoSubject.value.previsaoFuturoChart;
    if (!alvo) {
      this.definirPrevisaoETicker(fonte);
      return;
    }
    DashboardService.fundirCamposMercadoPrevisao(alvo, fonte);
    this.aplicarPatch({
      tickerMercadoSegmentos: DashboardService.montarSegmentosTicker(alvo),
    });
  }

  static fundirCamposMercadoPrevisao(
    alvo: PrevisaoFuturoChart,
    fonte: PrevisaoFuturoChart | null | undefined
  ): void {
    if (!fonte) {
      return;
    }
    if (fonte.indicadoresMercado != null) {
      alvo.indicadoresMercado = { ...fonte.indicadoresMercado };
    }
    if (fonte.notaJarvisMercado != null && fonte.notaJarvisMercado !== '') {
      alvo.notaJarvisMercado = fonte.notaJarvisMercado;
    }
    if (fonte.fatorCorrecaoInflacao != null && !Number.isNaN(Number(fonte.fatorCorrecaoInflacao))) {
      alvo.fatorCorrecaoInflacao = fonte.fatorCorrecaoInflacao;
    }
  }

  static montarSegmentosTicker(chart: PrevisaoFuturoChart | null): TickerMercadoSegmento[] {
    const ind = chart?.indicadoresMercado;
    if (!ind) {
      return [];
    }
    const segs: TickerMercadoSegmento[] = [];
    if (ind.selicAa != null && !Number.isNaN(Number(ind.selicAa))) {
      segs.push({ text: `Selic ${Number(ind.selicAa).toFixed(2)}% a.a.`, kind: 'selic' });
    }
    if (ind.ipcaMes != null && !Number.isNaN(Number(ind.ipcaMes))) {
      segs.push({ text: `IPCA ${Number(ind.ipcaMes).toFixed(2)}%`, kind: 'ipca' });
    }
    if (ind.dolarBrl != null && !Number.isNaN(Number(ind.dolarBrl))) {
      segs.push({ text: `USD/BRL R$ ${Number(ind.dolarBrl).toFixed(3)}`, kind: 'dolar' });
    }
    if (ind.fonteResumo?.trim()) {
      segs.push({ text: ind.fonteResumo.trim(), kind: 'meta' });
    }
    return segs;
  }

  /** Projeção consolidada (saldo real × projetado, simulações). */
  projection(): Observable<DashboardProjection> {
    return this.projecao.dashboard();
  }

  /** Sentinela — série “futuro provável”. */
  previsaoFluxoCaixa(): Observable<PrevisaoFuturoChart> {
    return this.projecao.previsaoFuturo();
  }

  oportunidadeInvestimento() {
    return this.projecao.oportunidadeInvestimento();
  }

  definirSimulacoesAtivas(ativa: boolean) {
    return this.projecao.definirSimulacoesAtivas(ativa);
  }

  otimizarProtocoloMetas(): Observable<ProtocoloOtimizacaoResponse> {
    return this.projecao.otimizarProtocoloMetas();
  }
}
