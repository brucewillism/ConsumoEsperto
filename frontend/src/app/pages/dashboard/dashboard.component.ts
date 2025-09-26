import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { TransacaoService } from '../../services/transacao.service';
import { CartaoCreditoService } from '../../services/cartao-credito.service';
import { RelatorioService } from '../../services/relatorio.service';
import { AuthService } from '../../services/auth.service';
import { BankApiService } from '../../services/bank-api.service';
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
  imports: [CommonModule, RouterLink],
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
   * financeiros do usuário autenticado.
   */
  private loadDashboardData() {
    console.log('📊 Carregando dados do dashboard...');
    this.isLoading = true;
    this.errorMessage = '';
    
    // Primeiro, tenta sincronizar dados do Mercado Pago automaticamente
    console.log('🔄 Iniciando sincronização do Mercado Pago...');
    this.syncMercadoPagoData();
    
    // Calcula datas para o mês atual
    const now = new Date();
    const startOfMonth = new Date(now.getFullYear(), now.getMonth(), 1);
    const endOfMonth = new Date(now.getFullYear(), now.getMonth() + 1, 0);
    
    // Faz múltiplas chamadas em paralelo para obter todos os dados
    forkJoin({
      transacoes: this.transacaoService.buscarPorUsuario().pipe(
        catchError(() => of([]))
      ),
      transacoesMes: this.transacaoService.buscarPorPeriodo(startOfMonth, endOfMonth).pipe(
        catchError(() => of([]))
      ),
      cartoes: this.cartaoCreditoService.buscarPorUsuario().pipe(
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
      },
      error: (error) => {
        console.error('Erro ao carregar dados do dashboard:', error);
        this.errorMessage = 'Erro ao carregar dados. Usando dados de demonstração.';
        this.loadMockData(); // Fallback para dados mock
        this.isLoading = false;
      }
    });
  }
  
  /**
   * Processa os dados reais obtidos do backend
   * 
   * Calcula métricas financeiras baseadas nos dados reais
   * e atualiza os cards e gráficos do dashboard.
   */
  private processarDadosReais(data: any) {
    // Processa transações do mês atual
    const transacoesMes = data.transacoesMes || [];
    const todasTransacoes = data.transacoes || [];
    
    // Calcula totais do mês
    this.totalSpent = this.calcularTotalPorTipo(transacoesMes, 'DESPESA');
    this.totalIncome = this.calcularTotalPorTipo(transacoesMes, 'RECEITA');
    this.balance = this.totalIncome - this.totalSpent;
    
    // Processa dados de cartão de crédito
    this.creditCardLimit = data.limiteTotal || 0;
    this.creditCardUsed = this.creditCardLimit - (data.limiteDisponivel || 0);
    
    // Atualiza cards com dados reais
    this.atualizarCardsComDadosReais();
    
    // Processa transações recentes (últimas 5)
    this.recentTransactions = todasTransacoes
      .sort((a: any, b: any) => new Date(b.dataTransacao).getTime() - new Date(a.dataTransacao).getTime())
      .slice(0, 5)
      .map((t: any) => ({
        id: t.id,
        description: t.descricao,
        amount: t.tipoTransacao === 'RECEITA' ? t.valor : -t.valor,
        category: t.categoriaNome || 'Sem categoria',
        date: new Date(t.dataTransacao),
        type: t.tipoTransacao === 'RECEITA' ? 'credit' : 'debit'
      }));
    
    // Atualiza gráficos com dados reais
    this.atualizarGraficosComDadosReais(todasTransacoes);
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
    this.dashboardCards = [
      {
        title: 'Gastos do Mês',
        value: this.formatCurrency(this.totalSpent),
        change: this.calcularVariacaoPercentual(this.totalSpent, 0), // TODO: Comparar com mês anterior
        changeType: this.totalSpent > 0 ? 'negative' : 'neutral',
        icon: 'fas fa-arrow-up',
        color: '#f64e60'
      },
      {
        title: 'Receitas do Mês',
        value: this.formatCurrency(this.totalIncome),
        change: this.calcularVariacaoPercentual(this.totalIncome, 0), // TODO: Comparar com mês anterior
        changeType: this.totalIncome > 0 ? 'positive' : 'neutral',
        icon: 'fas fa-arrow-down',
        color: '#1c3238'
      },
      {
        title: 'Saldo Atual',
        value: this.formatCurrency(this.balance),
        change: this.calcularVariacaoPercentual(this.balance, 0), // TODO: Comparar com período anterior
        changeType: this.balance >= 0 ? 'positive' : 'negative',
        icon: 'fas fa-wallet',
        color: '#3699ff'
      },
      {
        title: 'Limite Disponível',
        value: this.formatCurrency(this.creditCardLimit - this.creditCardUsed),
        change: this.calcularVariacaoPercentual(this.creditCardLimit - this.creditCardUsed, 0), // TODO: Comparar com período anterior
        changeType: 'neutral',
        icon: 'fas fa-credit-card',
        color: '#3f2b13'
      }
    ];
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
    // Gráfico de gastos por mês (últimos 6 meses)
    this.spendingChartData = this.gerarGraficoGastosMensais(transacoes);
    
    // Gráfico de gastos por categoria
    this.categoryChartData = this.gerarGraficoGastosPorCategoria(transacoes);
  }
  
  /**
   * Gera dados para gráfico de gastos mensais
   */
  private gerarGraficoGastosMensais(transacoes: any[]): ChartData {
    const ultimos6Meses = this.obterUltimos6Meses();
    const gastosMensais = ultimos6Meses.map(mes => {
      const gastos = transacoes
        .filter(t => {
          const dataTransacao = new Date(t.dataTransacao);
          return dataTransacao.getMonth() === mes.mes && 
                 dataTransacao.getFullYear() === mes.ano &&
                 t.tipoTransacao === 'DESPESA';
        })
        .reduce((total, t) => total + (t.valor || 0), 0);
      return gastos;
    });
    
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
  private gerarGraficoGastosPorCategoria(transacoes: any[]): ChartData {
    const gastosPorCategoria = new Map<string, number>();
    
    transacoes
      .filter(t => t.tipoTransacao === 'DESPESA')
      .forEach(t => {
        const categoria = t.categoriaNome || 'Sem categoria';
        const valorAtual = gastosPorCategoria.get(categoria) || 0;
        gastosPorCategoria.set(categoria, valorAtual + (t.valor || 0));
      });
    
    const categorias = Array.from(gastosPorCategoria.keys());
    const valores = Array.from(gastosPorCategoria.values());
    
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
   * Carrega dados mock para demonstração (fallback)
   * 
   * Usado apenas quando há erro ao carregar dados reais do backend.
   */
  private loadMockData() {
    // Estatísticas financeiras principais
    this.totalSpent = 2450;      // R$ 2.450,00 gastos
    this.totalIncome = 5200;     // R$ 5.200,00 recebidos
    this.balance = 2750;         // R$ 2.750,00 saldo
    this.creditCardLimit = 10000; // R$ 10.000,00 limite
    this.creditCardUsed = 2450;   // R$ 2.450,00 usado
    
    // Atualiza cards com dados mock
    this.atualizarCardsComDadosReais();
    
    // Transações recentes para demonstração
    this.recentTransactions = [
      {
        id: 1,
        description: 'Supermercado Extra',
        amount: -120.50, // Valor negativo = despesa
        category: 'Alimentação',
        date: new Date(),
        type: 'debit'
      },
      {
        id: 2,
        description: 'Uber',
        amount: -25.80,
        category: 'Transporte',
        date: new Date(Date.now() - 86400000), // 1 dia atrás
        type: 'debit'
      },
      {
        id: 3,
        description: 'Salário',
        amount: 5200.00, // Valor positivo = receita
        category: 'Receita',
        date: new Date(Date.now() - 172800000), // 2 dias atrás
        type: 'credit'
      },
      {
        id: 4,
        description: 'Netflix',
        amount: -39.90,
        category: 'Lazer',
        date: new Date(Date.now() - 259200000), // 3 dias atrás
        type: 'debit'
      }
    ];
    
    // Gráficos com dados mock
    this.spendingChartData = {
      labels: ['Jan', 'Fev', 'Mar', 'Abr', 'Mai', 'Jun'],
      datasets: [{
        label: 'Gastos Mensais',
        data: [1800, 2200, 1950, 2450, 2100, 2800],
        backgroundColor: ['rgba(54, 153, 255, 0.2)'],
        borderColor: ['rgba(54, 153, 255, 1)'],
        borderWidth: 2
      }]
    };
    
    this.categoryChartData = {
      labels: ['Alimentação', 'Transporte', 'Lazer', 'Saúde', 'Educação', 'Outros'],
      datasets: [{
        label: 'Gastos por Categoria',
        data: [800, 450, 300, 200, 150, 550],
        backgroundColor: this.gerarCores(6),
        borderColor: this.gerarCores(6, false),
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
   * Formata uma data para o padrão brasileiro
   * 
   * @param date Data a ser formatada
   * @returns String formatada (ex: "15/12/2024")
   */
  formatDate(date: Date): string {
    return new Intl.DateTimeFormat('pt-BR', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric'
    }).format(date);
  }

  /**
   * Sincroniza dados do Mercado Pago automaticamente
   * 
   * Este método é chamado automaticamente quando o dashboard carrega
   * para tentar buscar dados reais do Mercado Pago.
   */
  private syncMercadoPagoData() {
    console.log('🔄 Sincronizando dados do Mercado Pago automaticamente...');
    
    this.bankApiService.syncMercadoPagoData().subscribe({
      next: (response) => {
        console.log('✅ Dados do Mercado Pago sincronizados com sucesso:', response);
        // Recarrega os dados do dashboard após sincronização
        this.loadDashboardDataAfterSync();
      },
      error: (error) => {
        console.log('⚠️ Erro ao sincronizar dados do Mercado Pago:', error);
        // Continua carregando dados mesmo se a sincronização falhar
        this.loadDashboardDataAfterSync();
      }
    });
  }

  /**
   * Carrega dados do dashboard após tentativa de sincronização
   * 
   * Este método é chamado após a sincronização do Mercado Pago
   * para carregar os dados atualizados.
   */
  private loadDashboardDataAfterSync() {
    // Calcula datas para o mês atual
    const now = new Date();
    const startOfMonth = new Date(now.getFullYear(), now.getMonth(), 1);
    const endOfMonth = new Date(now.getFullYear(), now.getMonth() + 1, 0);
    
    // Faz múltiplas chamadas em paralelo para obter todos os dados
    forkJoin({
      transacoes: this.transacaoService.buscarPorUsuario().pipe(
        catchError(() => of([]))
      ),
      transacoesMes: this.transacaoService.buscarPorPeriodo(startOfMonth, endOfMonth).pipe(
        catchError(() => of([]))
      ),
      cartoes: this.cartaoCreditoService.buscarPorUsuario().pipe(
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
      },
      error: (error) => {
        console.error('❌ Erro ao carregar dados do dashboard:', error);
        this.errorMessage = 'Erro ao carregar dados. Tente novamente.';
        this.isLoading = false;
      }
    });
  }
}
