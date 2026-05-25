import { Component, DestroyRef, OnDestroy, OnInit, inject } from '@angular/core';
import { DashboardService } from '../../services/dashboard.service';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { BaseChartDirective } from 'ng2-charts';
import { FormsModule } from '@angular/forms';
import { parseValorBrasileiro, resolveHttpError } from '../../shared/utils/form.utils';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { openCeFormDialog } from '../../shared/ce-form-dialog.util';
import { QuickTransacaoDialogComponent } from '../../shared/quick-transacao-dialog/quick-transacao-dialog.component';
import { TransacaoService } from '../../services/transacao.service';
import { CartaoCreditoService } from '../../services/cartao-credito.service';
import { RelatorioCategoriaMesAtual, RelatorioService } from '../../services/relatorio.service';
import { RendaConfigService, RendaConfigDto } from '../../services/renda-config.service';
import {
  DashboardProjection,
  OportunidadeInvestimento,
  PrevisaoFuturoChart,
  SerieProjecaoSafraDTO,
  TimelineImpacto,
  normalizarSafra,
} from '../../services/projecao-dashboard.service';
import { buildSafraCascadeChart, toLineChartDatasets } from '../../utils/safra-cascata-chart.util';
import { PrevisaoFuturoChartComponent } from '../../components/previsao-futuro-chart/previsao-futuro-chart.component';
import { ScoreService, UsuarioScore } from '../../services/score.service';
import { InboxNotification, NotificacaoInboxService } from '../../services/notificacao-inbox.service';
import { JarvisChatPanelComponent } from '../../shared/jarvis-chat/jarvis-chat-panel.component';
import { ContencaoJarvisService, SugestaoContencaoJarvis } from '../../services/contencao-jarvis.service';
import { JarvisMemoriaService, JarvisMemoriaTimelineItem } from '../../services/jarvis-memoria.service';
import { JarvisFeedbackService } from '../../services/jarvis-feedback.service';
import { AuthService } from '../../services/auth.service';
import { UsuarioService } from '../../services/usuario.service';
import { PreferenciaTratamentoJarvis, Usuario } from '../../models/usuario.model';
import { HttpErrorResponse } from '@angular/common/http';
import { DateFormatPipe } from '../../pipes/date-format.pipe';
import { forkJoin, catchError, of, fromEvent, timer, Subscription, filter, finalize, Observable } from 'rxjs';
import { timeout } from 'rxjs/operators';
import { ChartConfiguration, ChartOptions } from 'chart.js';
import { LoadingIndicatorComponent } from '../../components/loading-indicator/loading-indicator.component';
import { LoadingService } from '../../services/loading.service';
import { ChartMetodologiaComponent } from '../../shared/chart-metodologia/chart-metodologia.component';
import { FinancaAlteracaoService } from '../../services/financa-alteracao.service';
import { CartaoCredito } from '../../models/cartao-credito.model';
import { TipoTransacao, Transacao } from '../../models/transacao.model';
import {
  TOOLTIP_JUROS_TRANSACAO,
  buildGrupoParcelamentoTemJuros,
  descricaoComIndicadorParcela,
  transacaoMostraBadgeJuros
} from '../../utils/transacao-parcela.util';
import { EstadoDashboardCompleto, RecentTxRow, TickerMercadoSegmento } from '../../models/jarvis-hud.model';

/**
 * Interface que define a estrutura de um card do dashboard
 * 
 * Cada card exibe uma métrica financeira com título, valor,
 * mudança percentual, tipo de mudança, ícone e cor.
 */
interface DashboardCard {
  title: string;        // Título do card (ex: "Gastos do Mês")
  value: string;        // Valor principal (ex: "R$ 2.450,00")
  change: string;       // Mudança percentual (ex: "+12.5%")
  changeType: 'positive' | 'negative' | 'neutral'; // Tipo da mudança
  icon: string;         // Ícone FontAwesome para o card
  color: string;        // Cor principal do card
}

/** Linha do ranking HUD (hábitos por categoria). */
interface CategoriaRankingRow {
  categoria: string;
  valor: number;
  pctOfMax: number;
  variacaoPct: number | null;
  excesso: boolean;
}

interface ChartData {
  labels: string[];     // Rótulos do eixo X (ex: meses ou categorias)
  datasets: {           // Conjunto de dados para o gráfico
    label: string;      // Rótulo da série de dados
    data: number[];     // Valores numéricos para cada rótulo
    backgroundColor: string[]; // Cores de fundo das barras/linhas
    borderColor: string[];     // Cores das bordas das barras/linhas
    borderWidth: number;       // Largura das bordas
  }[];
}

/**
 * Componente principal do dashboard da aplicação ConsumoEsperto
 * 
 * Este componente exibe um resumo completo da situação financeira
 * do usuário, incluindo cards com métricas principais, gráficos
 * de gastos e transações recentes. É a primeira tela que o usuário
 * vê após fazer login.
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    DateFormatPipe,
    BaseChartDirective,
    FormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatSnackBarModule,
    MatProgressSpinnerModule,
    MatIconModule,
    MatTooltipModule,
    MatDialogModule,
    PrevisaoFuturoChartComponent,
    LoadingIndicatorComponent,
    ChartMetodologiaComponent,
    JarvisChatPanelComponent,
  ],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss'
})
export class DashboardComponent implements OnInit, OnDestroy {
  private readonly destroyRef = inject(DestroyRef);
  
  // Array de cards do dashboard com métricas financeiras
  dashboardCards: DashboardCard[] = [];
  
  // Dados para o gráfico de gastos por mês
  spendingChartData: ChartData | null = null;
  
  // Dados para o gráfico de gastos por categoria
  categoryChartData: ChartData | null = null;
  doughnutChartData: ChartConfiguration<'doughnut'>['data'] = {
    labels: [],
    datasets: [{ data: [], backgroundColor: [], borderColor: [], borderWidth: 1 }]
  };
  doughnutColors: string[] = [];
  doughnutChartOptions: ChartOptions<'doughnut'> = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: {
        display: true,
        position: 'bottom',
        labels: {
          color: '#e2e8f0',
          boxWidth: 12,
          padding: 10,
          font: { family: 'Inter', size: 11 }
        }
      }
    }
  };

  /** Configuração de renda (API) + gráfico bruto → líquido vs descontos */
  rendaConfig: RendaConfigDto | null = null;
  rendaDoughnutChartData: ChartConfiguration<'doughnut'>['data'] = {
    labels: [],
    datasets: [{ data: [], backgroundColor: [], borderColor: [], borderWidth: 1 }]
  };
  rendaDoughnutChartOptions: ChartOptions<'doughnut'> = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: {
        display: true,
        position: 'bottom',
        labels: {
          color: '#e2e8f0',
          boxWidth: 12,
          padding: 10,
          font: { family: 'Inter', size: 11 }
        }
      }
    }
  };

  /** Gastos mensais — série temporal (Chart.js) */
  spendingLineChartData: ChartConfiguration<'line'>['data'] = {
    labels: [],
    datasets: []
  };
  spendingLineChartOptions: ChartOptions<'line'> = {
    responsive: true,
    maintainAspectRatio: false,
    interaction: { mode: 'index', intersect: false },
    plugins: {
      legend: {
        display: false,
        labels: { color: '#e2e8f0', font: { family: 'Inter', size: 11 } }
      },
      tooltip: {
        callbacks: {
          label: (ctx) => {
            const v = ctx.parsed.y;
            if (v == null || Number.isNaN(v)) {
              return `${ctx.dataset.label}: —`;
            }
            return `${ctx.dataset.label}: ${new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(v)}`;
          }
        }
      }
    },
    scales: {
      x: {
        ticks: {
          color: '#94a3b8',
          maxRotation: 55,
          minRotation: 0,
          autoSkip: true,
          maxTicksLimit: 18,
        },
        grid: { color: 'rgba(51, 65, 85, 0.45)' }
      },
      y: {
        ticks: {
          color: '#94a3b8',
          callback: (value) =>
            new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL', maximumFractionDigits: 0 }).format(Number(value)),
        },
        grid: { color: 'rgba(51, 65, 85, 0.45)' }
      }
    }
  };
  
  // Estatísticas financeiras principais
  totalSpent = 0;        // Total gasto no mês (confirmado — alinhado ao resumo da API)
  totalIncome = 0;       // Total recebido no mês (confirmado — alinhado ao resumo da API)
  receitasPrevistasMes = 0; // Gap salarial previsto (renda config − já confirmado)
  balance = 0;           // Saldo atual (receitas - despesas)
  creditCardLimit = 0;   // Limite total do cartão de crédito
  creditCardUsed = 0;    // Valor usado do cartão de crédito
  
  // Lista das transações mais recentes
  recentTransactions: RecentTxRow[] = [];
  readonly tooltipJurosTransacao = TOOLTIP_JUROS_TRANSACAO;
  
  // Estado de carregamento para mostrar spinner
  isLoading = true;
  
  // Dados de erro para tratamento
  errorMessage = '';
  
  // Controle de carregamento para evitar duplicação
  private isLoadingData = false;
  private lastLoadTime = 0;

  /** Polling 60s — cancelado em ngOnDestroy */
  private pollingSubscription?: Subscription;

  /** Atualização em segundo plano (polling / eventos) sem overlay completo */
  isSilentRefreshing = false;
  ultimaAtualizacao: Date | null = null;

  readonly mensagemCarregamentoDashboard =
    'Sincronizando o ecossistema operacional do J.A.R.V.I.S.…';

  readonly tipoTransacaoEnum = TipoTransacao;
  modoSimulacao = false;
  /** Sentinela — mesma fonte que {@link PrevisaoFuturoChartComponent} quando integrado ao painel. */
  previsaoFuturoChart: PrevisaoFuturoChart | null = null;
  /** Mantém a série retornada pelo protocolo (GET /previsao-futuro ainda usa burn histórico, não o corte simulado). */
  preservarGraficoPosProtocolo = false;
  novoFuturoProtocolo = false;
  protocoloOtimizacaoEmAndamento = false;
  categoriaRanking: CategoriaRankingRow[] = [];
  dashboardProjection: DashboardProjection | null = null;
  mesesSafra: SerieProjecaoSafraDTO[] = [];
  timelineImpacto: TimelineImpacto[] = [];
  usuarioScore: UsuarioScore | null = null;
  oportunidadeInvestimento: OportunidadeInvestimento | null = null;
  insightsFeed: InboxNotification[] = [];
  /** Metas de contenção sugeridas pelo J.A.R.V.I.S. (hábito / pós-importação). */
  sugestoesContencaoJarvis: SugestaoContencaoJarvis[] = [];
  sugestaoContencaoEmAcao: number | null = null;
  userPerfilJarvis: Usuario | null = null;
  showJarvisTratamentoWizard = false;
  /** Só avalia o wizard após GET /perfil (evita localStorage antigo sem jarvisConfigurado). */
  private jarvisPerfilSincronizado = false;
  jarvisWizardPreviewPref: PreferenciaTratamentoJarvis = 'SENHOR';
  salvarTratamentoEmAndamento = false;

  /** Painel de memória semântica J.A.R.V.I.S. */
  painelMemoriaAberto = false;
  memoriaCarregando = false;
  itensMemoria: JarvisMemoriaTimelineItem[] = [];

  /** Segmentos para marquee com classes por indicador (Selic / IPCA / USD). */
  tickerMercadoSegmentos: TickerMercadoSegmento[] = [];
  /** Radar HUD — mesma flag que o gráfico Sentinela (emitida em um único tick pelo {@link DashboardService}). */
  radarPulsoHud = false;
  readonly opcoesTratamentoWizard: { value: PreferenciaTratamentoJarvis; label: string }[] = [
    { value: 'SENHOR', label: 'Senhor' },
    { value: 'SENHORA', label: 'Senhora' },
    { value: 'DOUTOR', label: 'Doutor' },
    { value: 'DOUTORA', label: 'Doutora' },
    { value: 'NENHUM', label: 'Apenas meu nome' },
  ];

  constructor(
    private transacaoService: TransacaoService,
    private cartaoCreditoService: CartaoCreditoService,
    private relatorioService: RelatorioService,
    private rendaConfigService: RendaConfigService,
    private financaAlteracao: FinancaAlteracaoService,
    private dashboardService: DashboardService,
    private scoreService: ScoreService,
    private notificacaoInbox: NotificacaoInboxService,
    private contencaoJarvisService: ContencaoJarvisService,
    private jarvisMemoriaService: JarvisMemoriaService,
    private jarvisFeedbackService: JarvisFeedbackService,
    private authService: AuthService,
    private usuarioService: UsuarioService,
    private snackBar: MatSnackBar,
    private dialog: MatDialog,
    private loadingService: LoadingService
  ) {
    this.financaAlteracao.alteracoes$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.loadDashboardData({ silent: true }));
  }

  /**
   * Método executado na inicialização do componente
   * Carrega todos os dados necessários para o dashboard
   */
  ngOnInit() {
    console.log('🚀 Dashboard inicializando...');
    this.authService.currentUser$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((u) => {
        this.userPerfilJarvis = u;
        if (this.jarvisPerfilSincronizado) {
          this.avaliarWizardJarvis(u);
        }
      });
    this.authService.reloadCurrentUserProfile().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.jarvisPerfilSincronizado = true;
        this.avaliarWizardJarvis(this.authService.getCurrentUser());
      },
      error: () => {
        this.jarvisPerfilSincronizado = true;
        this.avaliarWizardJarvis(this.authService.getCurrentUser());
      },
    });
    this.carregarInsightsFeed();

    this.dashboardService.estadoDashboardCompleto$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((e: EstadoDashboardCompleto) => {
        this.previsaoFuturoChart = e.previsaoFuturoChart;
        this.tickerMercadoSegmentos = e.tickerMercadoSegmentos;
        this.preservarGraficoPosProtocolo = e.preservarGraficoPosProtocolo;
        this.novoFuturoProtocolo = e.novoFuturoProtocolo;
        this.protocoloOtimizacaoEmAndamento = e.protocoloOtimizacaoEmAndamento;
        this.radarPulsoHud = e.radarPulsoHud;
      });

    this.loadDashboardData();

    fromEvent(document, 'visibilitychange')
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        if (document.visibilityState === 'visible') {
          this.loadDashboardData({ silent: true });
        }
      });

    this.pollingSubscription = timer(60000, 60000)
      .pipe(filter(() => document.visibilityState === 'visible'))
      .subscribe(() => this.loadDashboardData({ silent: true }));

    this.syncDashboardPageOverlay();
  }

  carregarInsightsFeed(): void {
    this.notificacaoInbox.loadInbox().subscribe((items) => {
      this.insightsFeed = items.slice(0, 5);
    });
  }

  /** Chaves `tipo:id:positivo` para evitar cliques duplicados no feedback. */
  private readonly jarvisFeedbackDedup = new Set<string>();

  classeSegMercado(seg: TickerMercadoSegmento): Record<string, boolean> {
    const fator = this.previsaoFuturoChart?.fatorCorrecaoInflacao ?? 1;
    const ind = this.previsaoFuturoChart?.indicadoresMercado;
    const selic = ind?.selicAa != null ? Number(ind.selicAa) : null;
    const ipca = ind?.ipcaMes != null ? Number(ind.ipcaMes) : null;
    const inflaAlta = fator > 1.0001;
    const selicFavorInvest =
      selic != null && (selic >= 10 || (ipca != null && !Number.isNaN(ipca) && selic > ipca));
    return {
      'ticker-seg--amber': seg.kind === 'ipca' && inflaAlta,
      'ticker-seg--cyan': seg.kind === 'selic' && selicFavorInvest,
    };
  }

  enviarFeedbackNotificacao(item: InboxNotification, positivo: boolean): void {
    const id = (item.key ?? (item.serverId != null ? `db-${item.serverId}` : '')).trim();
    if (!id) {
      return;
    }
    const dedup = `NOTIFICACAO:${id}:${positivo}`;
    if (this.jarvisFeedbackDedup.has(dedup)) {
      return;
    }
    this.jarvisFeedbackDedup.add(dedup);
    this.jarvisFeedbackService
      .enviar({ insightId: id, positivo, tipoAlvo: 'NOTIFICACAO' })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.snackBar.open(
            positivo ? 'Feedback positivo registrado no núcleo tático.' : 'Recalibrando prioridades de análise, Senhor.',
            'Fechar',
            { duration: 4000 }
          );
        },
        error: () => {
          this.jarvisFeedbackDedup.delete(dedup);
          this.snackBar.open('Não foi possível enviar o feedback agora.', 'Fechar', { duration: 3500 });
        },
      });
  }

  enviarFeedbackContencao(s: SugestaoContencaoJarvis, positivo: boolean): void {
    if (s.id == null) {
      return;
    }
    const id = String(s.id);
    const dedup = `CONTENCAO:${id}:${positivo}`;
    if (this.jarvisFeedbackDedup.has(dedup)) {
      return;
    }
    this.jarvisFeedbackDedup.add(dedup);
    const chave = (s.chaveAgrupamento || s.categoriaNome || s.rotuloExibicao || '').trim();
    this.jarvisFeedbackService
      .enviar({
        insightId: id,
        positivo,
        tipoAlvo: 'CONTENCAO',
        ...(chave ? { categoriaChave: chave } : {}),
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.snackBar.open(
            positivo ? 'Protocolo de contenção validado. Obrigado, Senhor.' : 'Prioridade deste protocolo reduzida por 30 dias.',
            'Fechar',
            { duration: 4500 }
          );
        },
        error: () => {
          this.jarvisFeedbackDedup.delete(dedup);
          this.snackBar.open('Não foi possível enviar o feedback agora.', 'Fechar', { duration: 3500 });
        },
      });
  }

  aceitarSugestaoContencaoJarvis(s: SugestaoContencaoJarvis): void {
    const id = s.id;
    if (id == null) return;
    this.sugestaoContencaoEmAcao = id;
    this.contencaoJarvisService
      .aceitar(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.sugestaoContencaoEmAcao = null;
          this.snackBar.open(
            'Protocolo de contenção ativo. Vamos monitorar seus lançamentos e avisar perto de 80% do limite.',
            'Fechar',
            { duration: 6000 }
          );
          this.sugestoesContencaoJarvis = this.sugestoesContencaoJarvis.filter((x) => x.id !== id);
          this.financaAlteracao.notificar();
        },
        error: () => {
          this.sugestaoContencaoEmAcao = null;
          this.snackBar.open('Não foi possível ativar o protocolo. Tente novamente.', 'Fechar', { duration: 4000 });
        },
      });
  }

  recusarSugestaoContencaoJarvis(s: SugestaoContencaoJarvis): void {
    const id = s.id;
    if (id == null) return;
    this.sugestaoContencaoEmAcao = id;
    this.contencaoJarvisService
      .recusar(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.sugestaoContencaoEmAcao = null;
          this.snackBar.open('Sugestão recusada.', 'Fechar', { duration: 3000 });
          this.sugestoesContencaoJarvis = this.sugestoesContencaoJarvis.filter((x) => x.id !== id);
        },
        error: () => {
          this.sugestaoContencaoEmAcao = null;
          this.snackBar.open('Não foi possível recusar agora.', 'Fechar', { duration: 4000 });
        },
      });
  }

  ngOnDestroy(): void {
    this.pollingSubscription?.unsubscribe();
    this.pollingSubscription = undefined;
    this.loadingService.setPageOverlay(false);
  }

  private syncDashboardPageOverlay(): void {
    if (this.isLoading) {
      this.loadingService.setPageOverlay(true, this.mensagemCarregamentoDashboard);
    } else {
      this.loadingService.setPageOverlay(false);
    }
  }

  private setDashboardLoading(loading: boolean): void {
    this.isLoading = loading;
    this.syncDashboardPageOverlay();
  }

  abrirNovoLancamento(): void {
    openCeFormDialog(this.dialog, QuickTransacaoDialogComponent, { width: '560px' })
      .afterClosed()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((criada) => {
        if (criada) {
          this.financaAlteracao.notificar();
        }
      });
  }

  private parseValorBrasileiro(v: unknown): number | null {
    return parseValorBrasileiro(v);
  }
  
  /**
   * Carrega todos os dados do dashboard
   * 
   * Faz chamadas reais para o backend para obter dados
   * financeiros do usuário autenticado. SEM DADOS MOCK.
   * Implementa controle anti-duplicação.
   */
  public loadDashboardData(options?: { silent?: boolean }) {
    const silent = options?.silent === true;

    if (!silent) {
      this.dashboardService.prepararNovaRecargaCompleta();
    }
    if (this.isLoadingData) {
      console.log('⚠️ Carregamento já em andamento, ignorando chamada duplicada');
      if (!silent) {
        this.setDashboardLoading(true);
      }
      return;
    }

    const now = Date.now();
    if (now - this.lastLoadTime < 2000) {
      console.log('⚠️ Carregamento muito frequente, aguardando cooldown');
      if (!silent && this.isLoadingData) {
        this.setDashboardLoading(true);
      }
      return;
    }

    console.log('📊 Carregando dados REAIS do dashboard...', silent ? '(silencioso)' : '');
    if (!silent) {
      this.setDashboardLoading(true);
    } else {
      this.isSilentRefreshing = true;
    }
    this.isLoadingData = true;
    this.errorMessage = '';
    this.lastLoadTime = now;

    this.loadDashboardDataAfterSync();
  }
  
  /**
   * Processa os dados reais obtidos do backend
   * 
   * Calcula métricas financeiras baseadas nos dados reais
   * e atualiza os cards e gráficos do dashboard.
   */
  private processarDadosReais(data: any) {
    console.log('📊 Processando dados reais do backend:', data);
    
    // Processa transações do mês atual
    const transacoesMes = data.transacoesMes || [];
    console.log('📊 Transações do mês:', transacoesMes.length, 'transações encontradas');
    
    // Totais mensais: mesma base do endpoint /transacoes/resumo-mes-atual (apenas confirmadas).
    const resumoMes = data.resumoMes as {
      saldo?: number;
      totalReceitas?: number;
      totalDespesas?: number;
      receitasPrevistas?: number;
    } | undefined;
    const receitasConfirmadas = this.coercerValorMonetario(resumoMes?.totalReceitas);
    const despesasConfirmadas = this.coercerValorMonetario(resumoMes?.totalDespesas);
    this.totalIncome = resumoMes?.totalReceitas != null && !Number.isNaN(receitasConfirmadas)
      ? receitasConfirmadas
      : this.calcularTotalPorTipo(transacoesMes, 'RECEITA', true);
    this.totalSpent = resumoMes?.totalDespesas != null && !Number.isNaN(despesasConfirmadas)
      ? despesasConfirmadas
      : this.calcularTotalPorTipo(transacoesMes, 'DESPESA', true);
    this.receitasPrevistasMes = Math.max(0, this.coercerValorMonetario(resumoMes?.receitasPrevistas));
    // Saldo do mês corrente (alinhado a "Receitas/Gastos do mês"). O endpoint /transacoes/resumo
    // usa saldo acumulado histórico (todas as confirmações) e distorce o card junto aos totais mensais.
    if (resumoMes != null && resumoMes.saldo != null && !Number.isNaN(Number(resumoMes.saldo))) {
      this.balance = Number(resumoMes.saldo);
    } else {
      this.balance = this.totalIncome - this.totalSpent;
    }

    const rawRenda = data.rendaConfig as RendaConfigDto | null | undefined;
    const bruto = rawRenda != null ? Number(rawRenda.salarioBruto) : 0;
    this.rendaConfig = rawRenda != null && bruto > 0 ? rawRenda : null;
    this.syncRendaDoughnut();
    this.usuarioScore = data.usuarioScore || null;
    this.oportunidadeInvestimento = data.oportunidadeInvestimento || null;
    this.dashboardService.sincronizarPrevisaoAposFetch(data.previsaoFuturo ?? null);
    
    // Cartões: totais a partir da lista (limite utilizado = soma na fatura aberta; nunca mistura com saldo em conta)
    const cartoesList = data.cartoes || [];
    this.creditCardLimit = cartoesList.reduce((s: number, c: { limiteCredito?: number }) => s + (Number(c.limiteCredito) || 0), 0);
    this.creditCardUsed = cartoesList.reduce((s: number, c: { limiteUtilizado?: number; limiteCredito?: number; limiteDisponivel?: number }) => {
      if (c.limiteUtilizado != null && !Number.isNaN(Number(c.limiteUtilizado))) {
        return s + Number(c.limiteUtilizado);
      }
      const lim = Number(c.limiteCredito) || 0;
      const disp = Number(c.limiteDisponivel) || 0;
      return s + Math.max(0, lim - disp);
    }, 0);
    
    console.log('📊 Totais calculados:', {
      totalSpent: this.totalSpent,
      totalIncome: this.totalIncome,
      balance: this.balance,
      creditCardLimit: this.creditCardLimit,
      creditCardUsed: this.creditCardUsed
    });
    
    // Atualiza cards com dados reais (SEMPRE cria os cards, mesmo se dados forem zero)
    this.atualizarCardsComDadosReais();
    
    // Processa transações recentes (últimas 5 do mês atual)
    const mesList = (transacoesMes || []) as Transacao[];
    const gruposJuros = buildGrupoParcelamentoTemJuros(mesList);
    this.recentTransactions = mesList
      .filter((t) => !!t.dataTransacao)
      .sort((a, b) => new Date(b.dataTransacao!).getTime() - new Date(a.dataTransacao!).getTime())
      .slice(0, 5)
      .map((t) => ({
        id: t.id,
        description: descricaoComIndicadorParcela(t),
        amount: t.tipoTransacao === 'RECEITA' ? t.valor : -t.valor,
        category: t.categoriaNome || 'Sem categoria',
        date: new Date(t.dataTransacao!),
        type: t.tipoTransacao === 'RECEITA' ? 'credit' : 'debit',
        showJurosWarning: transacaoMostraBadgeJuros(t, gruposJuros)
      }));
    
    // Atualiza gráficos com dados reais
    this.atualizarGraficosComDadosReais(transacoesMes);
    this.dashboardProjection = data.dashboardProjection || null;
    this.mesesSafra = normalizarSafra(this.dashboardProjection?.safraPatrimonio);
    this.timelineImpacto = this.dashboardProjection?.timelineImpacto || [];
    this.syncSpendingLineChart();
    const relatorioCategoria = this.resolverRelatorioCategoria(
      data.despesasCategoriaMesAtual,
      mesList
    );
    this.atualizarDoughnutComRelatorio(relatorioCategoria);
    if (!this.doughnutChartData.labels?.length && this.categoryChartData) {
      this.aplicarDoughnutDeChartData(this.categoryChartData);
    }
    this.syncCategoriaRankingHud(relatorioCategoria, data.relatorioCategoriaMesPassado);
    
    console.log('✅ Dados processados:', {
      totalSpent: this.totalSpent,
      totalIncome: this.totalIncome,
      balance: this.balance,
      creditCardLimit: this.creditCardLimit,
      creditCardUsed: this.creditCardUsed,
      recentTransactionsCount: this.recentTransactions.length
    });
  }

  private atualizarDoughnutComRelatorio(relatorio: RelatorioCategoriaMesAtual) {
    const itens = Array.isArray(relatorio?.itens) ? relatorio.itens : [];
    if (itens.length === 0) {
      if (this.categoryChartData?.labels?.length && this.categoryChartData.datasets?.[0]?.data?.length) {
        this.aplicarDoughnutDeChartData(this.categoryChartData);
        return;
      }
      this.doughnutColors = [];
      this.doughnutChartData = {
        labels: [],
        datasets: [{ data: [], backgroundColor: [], borderColor: [], borderWidth: 1 }]
      };
      return;
    }

    const labels = itens.map((item) => item.categoria || 'Sem categoria');
    const valores = itens.map((item) => this.coercerValorMonetario(item.valor));
    const bg = this.gerarCores(labels.length);
    const border = this.gerarCores(labels.length, false);
    this.doughnutColors = bg;

    this.doughnutChartData = {
      labels,
      datasets: [{
        data: valores,
        backgroundColor: bg,
        borderColor: border,
        borderWidth: 1
      }]
    };
  }

  private aplicarDoughnutDeChartData(chart: ChartData): void {
    const labels = [...(chart.labels ?? [])].map(String);
    const raw = chart.datasets?.[0]?.data ?? [];
    const valores = raw.map((v) => this.coercerValorMonetario(v));
    const bg = this.gerarCores(labels.length);
    const border = this.gerarCores(labels.length, false);
    this.doughnutColors = bg;
    this.doughnutChartData = {
      labels,
      datasets: [{
        data: valores,
        backgroundColor: bg,
        borderColor: border,
        borderWidth: 1
      }]
    };
  }

  private coercerValorMonetario(valor: unknown): number {
    const n = Number(valor);
    return Number.isFinite(n) ? n : 0;
  }

  getDoughnutColor(index: number): string {
    return this.doughnutColors[index] || '#10b981';
  }
  
  /**
   * Calcula o total de transações por tipo.
   * Por omissão considera apenas lançamentos confirmados (mesma base do resumo mensal da API).
   */
  private calcularTotalPorTipo(transacoes: any[], tipo: string, apenasConfirmadas = false): number {
    return transacoes
      .filter(t => t.tipoTransacao === tipo && (!apenasConfirmadas || t.statusConferencia === 'CONFIRMADA'))
      .reduce((total, t) => total + (t.valor || 0), 0);
  }
  
  /**
   * Atualiza os cards com dados reais calculados
   */
  private atualizarCardsComDadosReais() {
    const limiteDisponivel = this.creditCardLimit - this.creditCardUsed;
    
    console.log('📊 Atualizando cards do dashboard:', {
      totalSpent: this.totalSpent,
      totalIncome: this.totalIncome,
      balance: this.balance,
      creditCardLimit: this.creditCardLimit,
      limiteDisponivel: limiteDisponivel
    });
    
    this.dashboardCards = [
      {
        title: 'Gastos do Mês',
        value: this.formatCurrency(this.totalSpent),
        change: this.totalSpent > 0 ? 'Dados do mês atual' : 'Nenhum gasto registrado',
        changeType: this.totalSpent > 0 ? 'negative' : 'neutral',
        icon: 'fas fa-arrow-up',
        color: '#f64e60'
      },
      {
        title: 'Receitas do Mês',
        value: this.formatCurrency(this.totalIncome),
        change: this.totalIncome > 0
          ? 'Dados do mês atual'
          : this.receitasPrevistasMes > 0
            ? `Salário previsto (${this.formatCurrency(this.receitasPrevistasMes)}) — aguardando lançamento`
            : 'Nenhuma receita registrada',
        changeType: this.totalIncome > 0 ? 'positive' : 'neutral',
        icon: 'fas fa-arrow-down',
        color: '#1c3238'
      },
      {
        title: 'Saldo Atual',
        value: this.formatCurrency(this.balance),
        change: 'Receitas do mês − despesas do mês (mesma base dos cards ao lado)',
        changeType: this.balance >= 0 ? 'positive' : 'negative',
        icon: 'fas fa-wallet',
        color: '#3699ff'
      },
      {
        title: 'Limite Disponível',
        value: this.formatCurrency(limiteDisponivel),
        change: this.creditCardLimit > 0 ? `${((limiteDisponivel / this.creditCardLimit) * 100).toFixed(1)}% disponível` : 'Sem cartão cadastrado',
        changeType: 'neutral',
        icon: 'fas fa-credit-card',
        color: '#3f2b13'
      }
    ];
    
    console.log('✅ Cards atualizados:', this.dashboardCards.length, 'cards criados');
  }
  
  /**
   * Calcula variação percentual (simplificado)
   */
  private calcularVariacaoPercentual(valorAtual: number, valorAnterior: number): string {
    if (valorAnterior === 0) return '0%';
    const variacao = ((valorAtual - valorAnterior) / valorAnterior) * 100;
    return `${variacao >= 0 ? '+' : ''}${variacao.toFixed(1)}%`;
  }
  
  /**
   * Atualiza gráficos com dados reais
   */
  private atualizarGraficosComDadosReais(transacoes: any[]) {
    console.log('📊 Atualizando gráficos com dados reais...');
    
    // Gráfico de gastos por mês (últimos 6 meses)
    this.spendingChartData = this.gerarGraficoGastosMensais(transacoes);
    this.syncSpendingLineChart();
    
    // Gráfico de gastos por categoria
    this.categoryChartData = this.gerarGraficoGastosPorCategoria(transacoes);
    
    console.log('📊 Gráficos atualizados:', {
      spendingChart: this.spendingChartData ? 'Dados disponíveis' : 'Sem dados',
      categoryChart: this.categoryChartData ? 'Dados disponíveis' : 'Sem dados'
    });
  }

  private syncSpendingLineChart(): void {
    if (this.dashboardProjection?.labels?.length) {
      const cascade = buildSafraCascadeChart(this.dashboardProjection);
      if (cascade) {
        this.spendingLineChartData = {
          labels: cascade.labels,
          datasets: toLineChartDatasets(
            cascade,
            this.modoSimulacao,
            this.dashboardProjection.simulacoesAtivas?.length ?? 0
          ),
        };
        return;
      }
    }
    const sc = this.spendingChartData;
    if (!sc?.labels?.length || !sc.datasets?.[0]?.data?.length) {
      this.spendingLineChartData = { labels: [], datasets: [] };
      return;
    }
    const ds = sc.datasets[0];
    this.spendingLineChartData = {
      labels: [...sc.labels],
      datasets: [{
        label: ds.label || 'Gastos',
        data: [...ds.data],
        borderColor: '#10b981',
        backgroundColor: 'rgba(16, 185, 129, 0.15)',
        fill: true,
        tension: 0.35,
        pointBackgroundColor: '#10b981',
        pointBorderColor: '#0f172a',
        pointHoverRadius: 6,
        borderWidth: 2
      }]
    };
  }
  
  /**
   * Gera dados para gráfico de gastos mensais
   */
  private gerarGraficoGastosMensais(transacoes: any[]): ChartData | null {
    if (!transacoes || transacoes.length === 0) {
      console.log('📊 Nenhuma transação encontrada para gráfico mensal');
      return null;
    }
    
    const ultimos6Meses = this.obterUltimos6Meses();
    const gastosMensais = ultimos6Meses.map(mes => {
      const gastos = transacoes
        .filter(t => {
          if (!t.dataTransacao) return false;
          const dataTransacao = new Date(t.dataTransacao);
          return dataTransacao.getMonth() === mes.mes && 
                 dataTransacao.getFullYear() === mes.ano &&
                 t.tipoTransacao === 'DESPESA';
        })
        .reduce((total, t) => total + (t.valor || 0), 0);
      return gastos;
    });
    
    // Verifica se há dados para exibir
    const temDados = gastosMensais.some(gasto => gasto > 0);
    if (!temDados) {
      console.log('📊 Nenhum gasto encontrado para gráfico mensal');
      return null;
    }
    
    return {
      labels: ultimos6Meses.map(m => m.nome),
      datasets: [{
        label: 'Gastos mensais',
        data: gastosMensais,
        backgroundColor: ['rgba(16, 185, 129, 0.2)'],
        borderColor: ['#10b981'],
        borderWidth: 2
      }]
    };
  }
  
  /**
   * Gera dados para gráfico de gastos por categoria
   */
  private gerarGraficoGastosPorCategoria(transacoes: Transacao[]): ChartData | null {
    const relatorio = this.montarRelatorioCategoriaDeTransacoes(transacoes);
    if (!relatorio.itens.length) {
      console.log('📊 Nenhum gasto por categoria encontrado nas transações do mês');
      return null;
    }

    const labels = relatorio.itens.map((i) => i.categoria);
    const valores = relatorio.itens.map((i) => i.valor);

    return {
      labels,
      datasets: [{
        label: 'Gastos por Categoria',
        data: valores,
        backgroundColor: this.gerarCores(labels.length),
        borderColor: this.gerarCores(labels.length, false),
        borderWidth: 1
      }]
    };
  }

  /** API de relatório pode falhar ou filtrar só confirmadas — extrai do mês carregado. */
  private resolverRelatorioCategoria(
    api: RelatorioCategoriaMesAtual | null | undefined,
    transacoes: Transacao[]
  ): RelatorioCategoriaMesAtual {
    const fromApi = this.normalizarRelatorioCategoria(api);
    const fromTx = this.montarRelatorioCategoriaDeTransacoes(transacoes);
    if (fromApi.itens.length === 0) {
      return fromTx;
    }
    if (fromTx.itens.length > fromApi.itens.length) {
      return fromTx;
    }
    return fromApi;
  }

  private normalizarRelatorioCategoria(raw: RelatorioCategoriaMesAtual | null | undefined): RelatorioCategoriaMesAtual {
    const now = new Date();
    const base: RelatorioCategoriaMesAtual = {
      ano: now.getFullYear(),
      mes: now.getMonth() + 1,
      totalDespesas: 0,
      itens: [],
    };
    if (!raw || typeof raw !== 'object') {
      return base;
    }

    const itensBrutos = (raw as { itens?: unknown; transacoesPorCategoria?: unknown }).itens
      ?? (raw as { transacoesPorCategoria?: unknown }).transacoesPorCategoria
      ?? [];

    const itens = (Array.isArray(itensBrutos) ? itensBrutos : [])
      .map((item: unknown) => {
        if (Array.isArray(item)) {
          return {
            categoria: String(item[0] ?? 'Sem categoria'),
            valor: this.coercerValorMonetario(item[1]),
            percentual: 0,
          };
        }
        const row = item as { categoria?: string; valor?: unknown; percentual?: unknown };
        return {
          categoria: String(row.categoria ?? 'Sem categoria'),
          valor: this.coercerValorMonetario(row.valor),
          percentual: this.coercerValorMonetario(row.percentual),
        };
      })
      .filter((item) => item.valor > 0);

    const totalDespesas = itens.reduce((sum, item) => sum + item.valor, 0);
    return {
      ano: Number((raw as RelatorioCategoriaMesAtual).ano) || base.ano,
      mes: Number((raw as RelatorioCategoriaMesAtual).mes) || base.mes,
      totalDespesas: this.coercerValorMonetario((raw as RelatorioCategoriaMesAtual).totalDespesas) || totalDespesas,
      itens,
    };
  }

  private montarRelatorioCategoriaDeTransacoes(transacoes: Transacao[]): RelatorioCategoriaMesAtual {
    const now = new Date();
    const map = new Map<string, number>();

    for (const t of transacoes) {
      if (!this.isTransacaoDespesa(t)) {
        continue;
      }
      const valor = this.coercerValorMonetario(t.valor);
      if (valor <= 0) {
        continue;
      }
      const categoria = this.nomeCategoriaTransacao(t);
      map.set(categoria, (map.get(categoria) ?? 0) + valor);
    }

    const totalDespesas = Array.from(map.values()).reduce((sum, v) => sum + v, 0);
    const itens = Array.from(map.entries())
      .map(([categoria, valor]) => ({
        categoria,
        valor,
        percentual: totalDespesas > 0 ? Math.round((valor / totalDespesas) * 1000) / 10 : 0,
      }))
      .sort((a, b) => b.valor - a.valor);

    return {
      ano: now.getFullYear(),
      mes: now.getMonth() + 1,
      totalDespesas,
      itens,
    };
  }

  private isTransacaoDespesa(t: Transacao): boolean {
    const tipo = String(t.tipoTransacao ?? t.tipo ?? '').toUpperCase();
    return tipo === 'DESPESA';
  }

  private nomeCategoriaTransacao(t: Transacao): string {
    const nested = (t.categoria as { nome?: string } | undefined)?.nome;
    const nome = (nested ?? t.categoriaNome ?? '').trim();
    return nome || 'Sem categoria';
  }
  
  /**
   * Obtém os últimos 6 meses
   */
  private obterUltimos6Meses() {
    const meses = [];
    const agora = new Date();
    
    for (let i = 5; i >= 0; i--) {
      const data = new Date(agora.getFullYear(), agora.getMonth() - i, 1);
      meses.push({
        mes: data.getMonth(),
        ano: data.getFullYear(),
        nome: data.toLocaleDateString('pt-BR', { month: 'short' })
      });
    }
    
    return meses;
  }
  
  /**
   * Gera cores para gráficos
   */
  private gerarCores(quantidade: number, transparente = true): string[] {
    const cores = [
      'rgba(16, 185, 129, 0.88)',
      'rgba(245, 158, 11, 0.88)',
      'rgba(56, 189, 248, 0.88)',
      'rgba(167, 139, 250, 0.88)',
      'rgba(244, 114, 182, 0.88)',
      'rgba(52, 211, 153, 0.75)'
    ];
    const slice = cores.slice(0, Math.max(quantidade, 1));
    if (!transparente) {
      return slice.map(c => c.replace('0.88', '1').replace('0.75', '1'));
    }
    return slice;
  }
  
  
  /**
   * Inicializa dados vazios quando não há dados reais
   * 
   * Usado apenas para inicializar variáveis com valores padrão.
   */
  private initializeEmptyData() {
    this.totalSpent = 0;
    this.totalIncome = 0;
    this.receitasPrevistasMes = 0;
    this.balance = 0;
    this.creditCardLimit = 0;
    this.creditCardUsed = 0;
    this.recentTransactions = [];
    this.spendingChartData = null;
    this.categoryChartData = null;
    this.spendingLineChartData = { labels: [], datasets: [] };
    this.dashboardCards = [];
    this.rendaConfig = null;
    this.syncRendaDoughnut();
  }

  private syncRendaDoughnut(): void {
    const rc = this.rendaConfig;
    if (!rc || !rc.salarioBruto || Number(rc.salarioBruto) <= 0) {
      this.rendaDoughnutChartData = {
        labels: [],
        datasets: [{ data: [], backgroundColor: [], borderColor: [], borderWidth: 1 }]
      };
      return;
    }
    const liq = Math.max(0, Number(rc.salarioLiquido ?? 0));
    const desc = Math.max(0, Number(rc.totalDescontos ?? 0));
    this.rendaDoughnutChartData = {
      labels: ['Salário líquido', 'Total de descontos'],
      datasets: [{
        data: [liq, desc],
        backgroundColor: ['#10b981', '#f59e0b'],
        borderColor: ['#059669', '#d97706'],
        borderWidth: 1
      }]
    };
  }
  
  /**
   * Calcula a porcentagem de uso do cartão de crédito
   * 
   * @returns Porcentagem de uso (0-100)
   */
  getCreditCardUsagePercentage(): number {
    if (!this.creditCardLimit || this.creditCardLimit <= 0) {
      return 0;
    }
    return Math.min(100, (this.creditCardUsed / this.creditCardLimit) * 100);
  }
  
  /**
   * Retorna a cor baseada no uso do cartão de crédito
   * 
   * - Verde: uso baixo (< 60%)
   * - Amarelo: uso médio (60-80%)
   * - Vermelho: uso alto (> 80%)
   * 
   * @returns Código de cor CSS
   */
  getCreditCardUsageColor(): string {
    const percentage = this.getCreditCardUsagePercentage();
    if (percentage >= 80) return '#f87171';
    if (percentage >= 60) return '#f59e0b';
    return '#10b981';
  }
  
  /**
   * Formata um valor numérico para moeda brasileira
   * 
   * @param value Valor numérico a ser formatado
   * @returns String formatada (ex: "R$ 1.234,56")
   */
  formatCurrency(value: number): string {
    return new Intl.NumberFormat('pt-BR', {
      style: 'currency',
      currency: 'BRL'
    }).format(value);
  }
  
  private wrapDashboardRequest<T>(
    source: Observable<T>,
    fallback: T,
    label: string,
    timeoutMs: number = 45_000
  ): Observable<T> {
    return source.pipe(
      timeout(timeoutMs),
      catchError((e) => {
        console.warn(`[Dashboard] ${label}: falha ou tempo esgotado (${timeoutMs} ms)`, e);
        return of(fallback);
      })
    );
  }

  /**
   * Carrega dados do dashboard após tentativa de sincronização
   *
   * Este método carrega os dados atualizados diretamente da API.
   * Implementa controle anti-duplicação.
   */
  private loadDashboardDataAfterSync() {
    // Não verifica isLoadingData aqui porque este método é chamado
    // após a sincronização e precisa sempre executar para finalizar o carregamento
    
    this.isLoadingData = true;
    console.log('📊 Carregando dados após sincronização...');
    
    // Calcula datas para o mês atual
    const now = new Date();
    const startOfMonth = new Date(now.getFullYear(), now.getMonth(), 1);
    const endOfMonth = new Date(now.getFullYear(), now.getMonth() + 1, 0);
    
    const prevRef = new Date(now.getFullYear(), now.getMonth() - 1, 1);
    const prevAno = prevRef.getFullYear();
    const prevMes = prevRef.getMonth() + 1;

    const despesasCategoriaFallback: RelatorioCategoriaMesAtual = {
      ano: now.getFullYear(),
      mes: now.getMonth() + 1,
      totalDespesas: 0,
      itens: [],
    };

    // Faz múltiplas chamadas em paralelo para obter todos os dados REAIS
    forkJoin({
      transacoesMes: this.wrapDashboardRequest(
        this.transacaoService.buscarDoMesAtual(),
        [] as Transacao[],
        'Transações do mês'
      ),
      resumoMes: this.wrapDashboardRequest(this.transacaoService.obterResumoDoMesAtual(), {}, 'Resumo do mês'),
      cartoes: this.wrapDashboardRequest(
        this.cartaoCreditoService.buscarTodosCartoes(),
        [] as CartaoCredito[],
        'Cartões'
      ),
      despesasCategoriaMesAtual: this.wrapDashboardRequest(
        this.relatorioService.getDespesasPorCategoriaMesAtual(),
        despesasCategoriaFallback,
        'Despesas por categoria (mês)'
      ),
      relatorioCategoriaMesPassado: this.wrapDashboardRequest(
        this.relatorioService.getRelatorioPorCategoria(prevAno, prevMes),
        null,
        'Relatório categoria (mês anterior)'
      ),
      rendaConfig: this.wrapDashboardRequest(this.rendaConfigService.obter(), null, 'Config. renda'),
      dashboardProjection: this.wrapDashboardRequest(
        this.dashboardService.projection(),
        null,
        'Projeção painel',
        90_000
      ),
      previsaoFuturo: this.wrapDashboardRequest(
        this.dashboardService.previsaoFluxoCaixa(),
        null,
        'Previsão de fluxo',
        90_000
      ),
      usuarioScore: this.wrapDashboardRequest(this.scoreService.obter(), null, 'Score utilizador'),
      oportunidadeInvestimento: this.wrapDashboardRequest(
        this.dashboardService.oportunidadeInvestimento(),
        null,
        'Oportunidade investimento'
      ),
      sugestoesContencao: this.wrapDashboardRequest(
        this.contencaoJarvisService.listarPendentes(),
        [] as SugestaoContencaoJarvis[],
        'Sugestões contenção'
      ),
    })
      .pipe(
        finalize(() => {
          this.setDashboardLoading(false);
          this.isLoadingData = false;
          this.isSilentRefreshing = false;
        })
      )
      .subscribe({
        next: (data) => {
          this.sugestoesContencaoJarvis = Array.isArray(data.sugestoesContencao) ? data.sugestoesContencao : [];
          this.processarDadosReais(data);
          this.ultimaAtualizacao = new Date();
          console.log('✅ Dados carregados com sucesso');
        },
        error: (error) => {
          console.error('❌ Erro ao carregar dados do dashboard:', error);
          this.errorMessage = 'Erro ao carregar dados. Tente novamente.';
        },
      });
  }

  getFluxoPercentualReceitas(): number {
    const total = this.totalIncome + this.totalSpent;
    if (total <= 0) {
      return 0;
    }
    return (this.totalIncome / total) * 100;
  }

  getFluxoPercentualDespesas(): number {
    const total = this.totalIncome + this.totalSpent;
    if (total <= 0) {
      return 0;
    }
    return (this.totalSpent / total) * 100;
  }

  alternarModoSimulacao(): void {
    this.dashboardService.definirSimulacoesAtivas(this.modoSimulacao).subscribe({
        next: () => {
          this.dashboardService.projection().subscribe((p) => {
            this.dashboardProjection = p;
            this.mesesSafra = normalizarSafra(p.safraPatrimonio);
            this.timelineImpacto = p.timelineImpacto || [];
            this.syncSpendingLineChart();
          });
        },
      error: () => {
        this.modoSimulacao = !this.modoSimulacao;
        this.snackBar.open('Não foi possível atualizar o modo simulação.', 'Fechar', { duration: 3000 });
      }
    });
  }

  /** Alterna cenários no gráfico (substitui o slide toggle Material, alinhado aos outros botões do HUD). */
  clicarModoSimulacao(): void {
    if (this.isLoading) {
      return;
    }
    this.modoSimulacao = !this.modoSimulacao;
    this.alternarModoSimulacao();
  }

  alternarPainelMemoria(): void {
    this.painelMemoriaAberto = !this.painelMemoriaAberto;
    if (this.painelMemoriaAberto) {
      this.carregarMemoriaTatica();
    }
  }

  private carregarMemoriaTatica(): void {
    this.memoriaCarregando = true;
    this.jarvisMemoriaService.timeline(48).subscribe({
      next: (rows) => {
        this.itensMemoria = rows ?? [];
        this.memoriaCarregando = false;
      },
      error: () => {
        this.itensMemoria = [];
        this.memoriaCarregando = false;
        this.snackBar.open('Não foi possível carregar a memória tática.', 'Fechar', { duration: 3500 });
      }
    });
  }

  get scoreGaugePct(): number {
    const s = this.usuarioScore?.score;
    if (s == null || Number.isNaN(Number(s))) {
      return 0;
    }
    return Math.min(100, Math.max(0, Number(s) / 10));
  }

  get protocoloCautelaAtivo(): boolean {
    return !!(
      this.previsaoFuturoChart?.projecaoNegativa ||
      this.previsaoFuturoChart?.protocoloOtimizacaoRecomendado ||
      (this.sugestoesContencaoJarvis?.length ?? 0) > 0
    );
  }

  get exibirBotaoOtimizacaoProtocolo(): boolean {
    const p = this.previsaoFuturoChart;
    return !!p && (!!p.projecaoNegativa || !!p.protocoloOtimizacaoRecomendado);
  }

  executarOtimizacaoProtocolo(): void {
    if (this.protocoloOtimizacaoEmAndamento) {
      return;
    }
    this.dashboardService.setProtocoloOtimizacaoEmAndamento(true);
    this.dashboardService
      .otimizarProtocoloMetas()
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.dashboardService.setProtocoloOtimizacaoEmAndamento(false);
        })
      )
      .subscribe({
        next: (res) => {
          if (res.previsaoAjustada?.pontos?.length) {
            this.dashboardService.aplicarPrevisaoPosOtimizacao(res.previsaoAjustada);
          }
          this.financaAlteracao.notificar();
          const msg = (res.mensagemJarvis ?? 'Protocolo aplicado.')
            .replace(/\*([^*]+)\*/g, '$1')
            .trim();
          this.snackBar.open(msg, 'Fechar', {
            duration: 22_000,
            panelClass: ['jarvis-protocolo-snack'],
          });
        },
        error: (err: HttpErrorResponse) => {
          const m =
            (typeof err.error === 'object' && err.error && 'message' in err.error
              ? String((err.error as { message?: string }).message)
              : null) || err.message || 'Não foi possível executar o protocolo.';
          this.snackBar.open(m, 'Fechar', { duration: 7000 });
        },
      });
  }

  get mesesReservaRunway(): number | null {
    if (this.totalSpent <= 0) {
      return null;
    }
    const m = this.balance / this.totalSpent;
    return Math.round(m * 10) / 10;
  }

  get mesesReservaBarPct(): number {
    const m = this.mesesReservaRunway;
    if (m == null || m <= 0) {
      return 0;
    }
    return Math.min(100, (m / 12) * 100);
  }

  get mesesReservaSaudavel(): boolean {
    const m = this.mesesReservaRunway;
    return m != null && m >= 6;
  }

  iconeInsightTematico(msg: string): string {
    const t = (msg || '').toLowerCase();
    if (/\b(meta|calend|agend|praz|viagem|crono|venc)\b/i.test(msg)) {
      return 'fas fa-calendar-days';
    }
    if (/\b(combust|gasolina|viatura|fuel)\b/i.test(msg)) {
      return 'fas fa-gas-pump';
    }
    if (/\b(jarvis|protocolo|oráculo|oraculo|consultoria|sistemas)\b/i.test(msg)) {
      return 'fas fa-robot';
    }
    if (/\b(cart|fatura|cartão|cartao|limite)\b/i.test(msg)) {
      return 'fas fa-credit-card';
    }
    return 'fas fa-bolt';
  }

  private mapRelatorioCategoriaPassado(raw: Record<string, unknown> | null): Map<string, number> {
    const m = new Map<string, number>();
    const rows = raw?.['transacoesPorCategoria'];
    if (!Array.isArray(rows)) {
      return m;
    }
    for (const row of rows) {
      if (Array.isArray(row) && row.length >= 2) {
        const nome = String(row[0] ?? 'Sem categoria');
        const valor = Number(row[1] ?? 0);
        m.set(nome, valor);
      }
    }
    return m;
  }

  private syncCategoriaRankingHud(atualData: { itens?: Array<{ categoria?: string; valor?: number }> } | null, rawPassado: Record<string, unknown> | null): void {
    const itens = Array.isArray(atualData?.itens) ? atualData!.itens! : [];
    const prevMap = this.mapRelatorioCategoriaPassado(rawPassado);
    if (!itens.length) {
      this.categoriaRanking = [];
      return;
    }
    const valores = itens.map((i) => Number(i.valor || 0));
    const max = Math.max(...valores, 1);
    const total = valores.reduce((a, b) => a + b, 0);
    const media = total / itens.length;
    this.categoriaRanking = itens
      .map((item) => {
        const nome = item.categoria || 'Sem categoria';
        const valor = Number(item.valor || 0);
        const prev = prevMap.get(nome);
        let variacaoPct: number | null = null;
        if (prev != null && prev > 0) {
          variacaoPct = Math.round(((valor - prev) / prev) * 1000) / 10;
        }
        const excesso =
          (variacaoPct != null && variacaoPct >= 15) || (variacaoPct == null && valor > media * 1.35);
        return {
          categoria: nome,
          valor,
          pctOfMax: (valor / max) * 100,
          variacaoPct,
          excesso,
        };
      })
      .sort((a, b) => b.valor - a.valor)
      .slice(0, 8);
  }

  get linhaCabecalhoDashboard(): string {
    const u = this.userPerfilJarvis;
    if (u?.jarvisConfigurado === true && u.jarvisTratamentoResumo?.trim()) {
      return `Bem-vindo de volta, ${u.jarvisTratamentoResumo}.`;
    }
    return 'Visão geral das suas finanças';
  }

  private avaliarWizardJarvis(user: Usuario | null): void {
    if (!user?.id) {
      this.showJarvisTratamentoWizard = false;
      return;
    }
    if (user.jarvisConfigurado === true) {
      this.showJarvisTratamentoWizard = false;
      return;
    }
    this.showJarvisTratamentoWizard = true;
    this.jarvisWizardPreviewPref = this.inferDefaultSelecaoTratamento(user);
  }

  private inferDefaultSelecaoTratamento(user: Usuario): PreferenciaTratamentoJarvis {
    const p = user.preferenciaTratamentoJarvis;
    if (p && p !== 'AUTOMATICO') {
      return p;
    }
    if (user.genero === 'MALE') return 'SENHOR';
    if (user.genero === 'FEMALE') return 'SENHORA';
    return 'NENHUM';
  }

  previewJarvisTratamento(p: PreferenciaTratamentoJarvis): string {
    const u = this.userPerfilJarvis ?? this.authService.getCurrentUser();
    const n = u?.nome?.trim();
    const pn = n ? (n.split(/\s+/)[0] || '') : '';
    switch (p) {
      case 'NENHUM':
        return pn || 'você';
      case 'SENHOR':
        return pn ? `Senhor ${pn}` : 'Senhor';
      case 'SENHORA':
        return pn ? `Senhora ${pn}` : 'Senhora';
      case 'DOUTOR':
        return pn ? `Doutor ${pn}` : 'Doutor';
      case 'DOUTORA':
        return pn ? `Doutora ${pn}` : 'Doutora';
      default:
        return u?.jarvisTratamentoResumo || '—';
    }
  }

  onHoverOpcaoWizard(p: PreferenciaTratamentoJarvis): void {
    this.jarvisWizardPreviewPref = p;
  }

  get linhaPreviewJarvisWizard(): string {
    return this.previewJarvisTratamento(this.jarvisWizardPreviewPref);
  }

  confirmarTratamentoJarvis(pref: PreferenciaTratamentoJarvis): void {
    if (this.salvarTratamentoEmAndamento) return;
    this.salvarTratamentoEmAndamento = true;
    this.usuarioService.patchPerfilJarvis(pref).subscribe({
      next: (dto) => {
        this.authService.applyPerfilResponse(dto);
        this.userPerfilJarvis = dto;
        this.showJarvisTratamentoWizard = false;
        this.salvarTratamentoEmAndamento = false;
      },
      error: (err: HttpErrorResponse) => {
        this.salvarTratamentoEmAndamento = false;
        let msg =
          err.status === 0
            ? 'Sem ligação à API. Verifique se o backend está ligado e se environment.apiUrl aponta para a API (ex.: :18081).'
            : 'Não foi possível salvar o protocolo J.A.R.V.I.S.';
        this.snackBar.open(msg, 'Fechar', { duration: 5000 });
      },
    });
  }
}
