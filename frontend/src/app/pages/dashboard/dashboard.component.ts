import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { TransacaoService } from '../../services/transacao.service';
import { CartaoCreditoService } from '../../services/cartao-credito.service';
import { RelatorioService } from '../../services/relatorio.service';
import { AuthService } from '../../services/auth.service';
import { BankApiService } from '../../services/bank-api.service';
import { DateFormatPipe } from '../../pipes/date-format.pipe';
import { forkJoin, catchError, of } from 'rxjs';

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

/**
 * Interface que define a estrutura dos dados dos gráficos
 * 
 * Usada para configurar gráficos de gastos por mês e por categoria
 * usando a biblioteca Chart.js.
 */
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
  imports: [CommonModule, RouterLink, DateFormatPipe],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss'
})
export class DashboardComponent implements OnInit {
  
  // Array de cards do dashboard com métricas financeiras
  dashboardCards: DashboardCard[] = [];
  
  // Dados para o gráfico de gastos por mês
  spendingChartData: ChartData | null = null;
  
  // Dados para o gráfico de gastos por categoria
  categoryChartData: ChartData | null = null;
  
  // Estatísticas financeiras principais
  totalSpent = 0;        // Total gasto no mês
  totalIncome = 0;       // Total recebido no mês
  balance = 0;           // Saldo atual (receitas - despesas)
  creditCardLimit = 0;   // Limite total do cartão de crédito
  creditCardUsed = 0;    // Valor usado do cartão de crédito
  
  // Lista das transações mais recentes
  recentTransactions: any[] = [];
  
  // Estado de carregamento para mostrar spinner
  isLoading = true;
  
  // Dados de erro para tratamento
  errorMessage = '';
  
  // Controle de carregamento para evitar duplicação
  private isLoadingData = false;
  private lastLoadTime = 0;
  
  constructor(
    private transacaoService: TransacaoService,
    private cartaoCreditoService: CartaoCreditoService,
    private relatorioService: RelatorioService,
    private authService: AuthService,
    private bankApiService: BankApiService
  ) {}
  
  /**
   * Método executado na inicialização do componente
   * Carrega todos os dados necessários para o dashboard
   */
  ngOnInit() {
    console.log('🚀 Dashboard inicializando...');
    this.loadDashboardData();
  }
  
  /**
   * Carrega todos os dados do dashboard
   * 
   * Faz chamadas reais para o backend para obter dados
   * financeiros do usuário autenticado. SEM DADOS MOCK.
   * Sempre sincroniza dados reais do Mercado Pago primeiro.
   * Implementa controle anti-duplicação.
   */
  public loadDashboardData() {
    // Controle anti-duplicação: evita múltiplas chamadas simultâneas
    if (this.isLoadingData) {
      console.log('⚠️ Carregamento já em andamento, ignorando chamada duplicada');
      return;
    }

    // Controle de rate limiting: evita chamadas muito frequentes
    const now = Date.now();
    if (now - this.lastLoadTime < 2000) { // 2 segundos de cooldown
      console.log('⚠️ Carregamento muito frequente, aguardando cooldown');
      return;
    }

    console.log('📊 Carregando dados REAIS do dashboard...');
    this.isLoading = true;
    this.isLoadingData = true;
    this.errorMessage = '';
    this.lastLoadTime = now;
    
    // SEMPRE sincronizar dados reais primeiro
    this.sincronizarDadosReais();
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
    
    // Calcula totais do mês
    this.totalSpent = this.calcularTotalPorTipo(transacoesMes, 'DESPESA');
    this.totalIncome = this.calcularTotalPorTipo(transacoesMes, 'RECEITA');
    this.balance = this.totalIncome - this.totalSpent;
    
    // Processa dados de cartão de crédito
    this.creditCardLimit = data.limiteTotal || 0;
    this.creditCardUsed = this.creditCardLimit - (data.limiteDisponivel || 0);
    
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
    this.recentTransactions = transacoesMes
      .filter((t: any) => t.dataTransacao) // Filtra transações com data válida
      .sort((a: any, b: any) => new Date(b.dataTransacao).getTime() - new Date(a.dataTransacao).getTime())
      .slice(0, 5)
      .map((t: any) => ({
        id: t.id,
        description: t.descricao || 'Transação sem descrição',
        amount: t.tipoTransacao === 'RECEITA' ? t.valor : -t.valor,
        category: t.categoriaNome || 'Sem categoria',
        date: new Date(t.dataTransacao),
        type: t.tipoTransacao === 'RECEITA' ? 'credit' : 'debit'
      }));
    
    // Atualiza gráficos com dados reais
    this.atualizarGraficosComDadosReais(transacoesMes);
    
    console.log('✅ Dados processados:', {
      totalSpent: this.totalSpent,
      totalIncome: this.totalIncome,
      balance: this.balance,
      creditCardLimit: this.creditCardLimit,
      creditCardUsed: this.creditCardUsed,
      recentTransactionsCount: this.recentTransactions.length
    });
  }
  
  /**
   * Calcula o total de transações por tipo
   */
  private calcularTotalPorTipo(transacoes: any[], tipo: string): number {
    return transacoes
      .filter(t => t.tipoTransacao === tipo)
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
        change: this.totalIncome > 0 ? 'Dados do mês atual' : 'Nenhuma receita registrada',
        changeType: this.totalIncome > 0 ? 'positive' : 'neutral',
        icon: 'fas fa-arrow-down',
        color: '#1c3238'
      },
      {
        title: 'Saldo Atual',
        value: this.formatCurrency(this.balance),
        change: this.balance > 0 ? 'Saldo positivo' : this.balance < 0 ? 'Saldo negativo' : 'Saldo zerado',
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
    
    // Gráfico de gastos por categoria
    this.categoryChartData = this.gerarGraficoGastosPorCategoria(transacoes);
    
    console.log('📊 Gráficos atualizados:', {
      spendingChart: this.spendingChartData ? 'Dados disponíveis' : 'Sem dados',
      categoryChart: this.categoryChartData ? 'Dados disponíveis' : 'Sem dados'
    });
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
        label: 'Gastos Mensais',
        data: gastosMensais,
        backgroundColor: ['rgba(54, 153, 255, 0.2)'],
        borderColor: ['rgba(54, 153, 255, 1)'],
        borderWidth: 2
      }]
    };
  }
  
  /**
   * Gera dados para gráfico de gastos por categoria
   */
  private gerarGraficoGastosPorCategoria(transacoes: any[]): ChartData | null {
    if (!transacoes || transacoes.length === 0) {
      console.log('📊 Nenhuma transação encontrada para gráfico de categorias');
      return null;
    }
    
    const gastosPorCategoria = new Map<string, number>();
    
    transacoes
      .filter(t => t.tipoTransacao === 'DESPESA' && t.valor > 0)
      .forEach(t => {
        const categoria = t.categoriaNome || 'Sem categoria';
        const valorAtual = gastosPorCategoria.get(categoria) || 0;
        gastosPorCategoria.set(categoria, valorAtual + (t.valor || 0));
      });
    
    const categorias = Array.from(gastosPorCategoria.keys());
    const valores = Array.from(gastosPorCategoria.values());
    
    // Verifica se há dados para exibir
    if (categorias.length === 0) {
      console.log('📊 Nenhum gasto por categoria encontrado');
      return null;
    }
    
    return {
      labels: categorias,
      datasets: [{
        label: 'Gastos por Categoria',
        data: valores,
        backgroundColor: this.gerarCores(categorias.length),
        borderColor: this.gerarCores(categorias.length, false),
        borderWidth: 1
      }]
    };
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
      'rgba(54, 153, 255, 0.8)',
      'rgba(28, 50, 56, 0.8)',
      'rgba(63, 43, 19, 0.8)',
      'rgba(246, 78, 96, 0.8)',
      'rgba(43, 50, 82, 0.8)',
      'rgba(58, 36, 52, 0.8)'
    ];
    
    if (!transparente) {
      return cores.map(cor => cor.replace('0.8', '1'));
    }
    
    return cores.slice(0, quantidade);
  }
  
  
  /**
   * Inicializa dados vazios quando não há dados reais
   * 
   * Usado apenas para inicializar variáveis com valores padrão.
   */
  private initializeEmptyData() {
    this.totalSpent = 0;
    this.totalIncome = 0;
    this.balance = 0;
    this.creditCardLimit = 0;
    this.creditCardUsed = 0;
    this.recentTransactions = [];
    this.spendingChartData = null;
    this.categoryChartData = null;
    this.dashboardCards = [];
  }
  
  /**
   * Calcula a porcentagem de uso do cartão de crédito
   * 
   * @returns Porcentagem de uso (0-100)
   */
  getCreditCardUsagePercentage(): number {
    return (this.creditCardUsed / this.creditCardLimit) * 100;
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
    if (percentage >= 80) return '#f64e60'; // Vermelho para uso alto
    if (percentage >= 60) return '#ffa800'; // Amarelo para uso médio
    return '#1c3238';                       // Verde para uso baixo
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
  

  /**
   * Sincroniza dados reais do Mercado Pago automaticamente
   * 
   * Este método é chamado automaticamente quando o dashboard carrega
   * para buscar TODAS as transações reais do Mercado Pago e criar
   * categorias baseadas nas transações reais.
   */
  private syncMercadoPagoData() {
    console.log('🔄 Sincronizando dados REAIS do Mercado Pago...');
    
    // Primeiro tenta obter o ID do usuário logado
    const userId = this.authService.getUserId();
    if (!userId) {
      console.log('⚠️ Usuário não logado, pulando sincronização');
      this.loadDashboardDataAfterSync();
      return;
    }
    
    // Sincroniza dados reais (busca TODAS as transações)
    this.bankApiService.syncMercadoPagoRealData(userId).subscribe({
      next: (response) => {
        console.log('✅ Dados REAIS do Mercado Pago sincronizados:', response);
        // Recarrega os dados do dashboard após sincronização
        this.loadDashboardDataAfterSync();
      },
      error: (error) => {
        console.log('⚠️ Erro ao sincronizar dados REAIS do Mercado Pago:', error);
        // Continua carregando dados mesmo se a sincronização falhar
        this.loadDashboardDataAfterSync();
      }
    });
  }

  /**
   * Sincroniza dados reais do Mercado Pago (método público)
   * 
   * Este método pode ser chamado pelo usuário através do botão
   * para forçar uma sincronização completa dos dados reais.
   */
  public sincronizarDadosReais() {
    console.log('🔄 Usuário solicitou sincronização de dados reais...');
    this.isLoading = true;
    this.errorMessage = '';
    
    const userId = this.authService.getUserId();
    if (!userId) {
      console.log('⚠️ Usuário não logado, redirecionando para login');
      this.errorMessage = 'Usuário não logado. Faça login para sincronizar dados.';
      this.isLoading = false;
      return;
    }
    
    // Sincroniza dados reais do Mercado Pago
    this.bankApiService.syncMercadoPagoRealData(userId).subscribe({
      next: (response) => {
        console.log('✅ Dados REAIS do Mercado Pago sincronizados:', response);
        // Aguarda um pouco para garantir que os dados foram salvos
        setTimeout(() => {
          this.loadDashboardDataAfterSync();
        }, 1000);
      },
      error: (error) => {
        console.error('❌ Erro ao sincronizar dados REAIS:', error);
        // Mesmo com erro na sincronização, tenta carregar dados existentes
        console.log('⚠️ Tentando carregar dados existentes...');
        this.loadDashboardDataAfterSync();
      }
    });
  }

  /**
   * Carrega dados do dashboard após tentativa de sincronização
   * 
   * Este método é chamado após a sincronização do Mercado Pago
   * para carregar os dados atualizados.
   * Implementa controle anti-duplicação.
   */
  private loadDashboardDataAfterSync() {
    // Verifica se já está carregando para evitar duplicação
    if (this.isLoadingData) {
      console.log('⚠️ Carregamento já em andamento, ignorando chamada duplicada');
      return;
    }

    this.isLoadingData = true;
    console.log('📊 Carregando dados após sincronização...');
    
    // Calcula datas para o mês atual
    const now = new Date();
    const startOfMonth = new Date(now.getFullYear(), now.getMonth(), 1);
    const endOfMonth = new Date(now.getFullYear(), now.getMonth() + 1, 0);
    
    // Faz múltiplas chamadas em paralelo para obter todos os dados REAIS
    forkJoin({
      transacoesMes: this.transacaoService.buscarDoMesAtual().pipe(
        catchError(() => of([]))
      ),
      resumoMes: this.transacaoService.obterResumoDoMesAtual().pipe(
        catchError(() => of({}))
      ),
      cartoes: this.cartaoCreditoService.buscarTodosCartoes().pipe(
        catchError(() => of([]))
      ),
      limiteTotal: this.cartaoCreditoService.getLimiteTotal().pipe(
        catchError(() => of(0))
      ),
      limiteDisponivel: this.cartaoCreditoService.getLimiteDisponivel().pipe(
        catchError(() => of(0))
      ),
      resumoFinanceiro: this.relatorioService.getResumoFinanceiro().pipe(
        catchError(() => of({}))
      )
    }).subscribe({
      next: (data) => {
        this.processarDadosReais(data);
        this.isLoading = false;
        this.isLoadingData = false;
        console.log('✅ Dados carregados com sucesso');
      },
      error: (error) => {
        console.error('❌ Erro ao carregar dados do dashboard:', error);
        this.errorMessage = 'Erro ao carregar dados. Tente novamente.';
        this.isLoading = false;
        this.isLoadingData = false;
      }
    });
  }
}
