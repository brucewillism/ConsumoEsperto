import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';

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
  
  /**
   * Método executado na inicialização do componente
   * Carrega todos os dados necessários para o dashboard
   */
  ngOnInit() {
    this.loadDashboardData();
  }
  
  /**
   * Carrega todos os dados do dashboard
   * 
   * Simula um delay de carregamento e inicializa os dados
   * mock para demonstração. Em produção, faria chamadas
   * para o backend.
   */
  private loadDashboardData() {
    // Simula tempo de carregamento da API
    setTimeout(() => {
      this.initializeDashboardCards(); // Inicializa os cards principais
      this.initializeChartData();      // Inicializa os dados dos gráficos
      this.loadMockData();             // Carrega dados mock para demonstração
      this.isLoading = false;          // Remove o estado de loading
    }, 1000);
  }
  
  /**
   * Inicializa os cards principais do dashboard
   * 
   * Cria os 4 cards principais com métricas financeiras:
   * - Gastos do Mês
   * - Receitas do Mês
   * - Saldo Atual
   * - Limite Disponível
   */
  private initializeDashboardCards() {
    this.dashboardCards = [
      {
        title: 'Gastos do Mês',
        value: 'R$ 2.450,00',
        change: '+12.5%',
        changeType: 'negative', // Vermelho para gastos
        icon: 'fas fa-arrow-up',
        color: '#f64e60'
      },
      {
        title: 'Receitas do Mês',
        value: 'R$ 5.200,00',
        change: '+8.2%',
        changeType: 'positive', // Verde para receitas
        icon: 'fas fa-arrow-down',
        color: '#1c3238'
      },
      {
        title: 'Saldo Atual',
        value: 'R$ 2.750,00',
        change: '+15.3%',
        changeType: 'positive', // Verde para saldo positivo
        icon: 'fas fa-wallet',
        color: '#3699ff'
      },
      {
        title: 'Limite Disponível',
        value: 'R$ 7.550,00',
        change: '-5.8%',
        changeType: 'neutral', // Neutro para limite
        icon: 'fas fa-credit-card',
        color: '#3f2b13'
      }
    ];
  }
  
  /**
   * Inicializa os dados dos gráficos
   * 
   * Configura dois gráficos principais:
   * 1. Gráfico de linha para gastos mensais
   * 2. Gráfico de pizza para gastos por categoria
   */
  private initializeChartData() {
    // Gráfico de gastos por mês (linha)
    this.spendingChartData = {
      labels: ['Jan', 'Fev', 'Mar', 'Abr', 'Mai', 'Jun'], // Meses
      datasets: [{
        label: 'Gastos Mensais',
        data: [1800, 2200, 1950, 2450, 2100, 2800], // Valores em reais
        backgroundColor: ['rgba(54, 153, 255, 0.2)'], // Azul transparente
        borderColor: ['rgba(54, 153, 255, 1)'],       // Azul sólido
        borderWidth: 2
      }]
    };
    
    // Gráfico de gastos por categoria (pizza)
    this.categoryChartData = {
      labels: ['Alimentação', 'Transporte', 'Lazer', 'Saúde', 'Educação', 'Outros'],
      datasets: [{
        label: 'Gastos por Categoria',
        data: [800, 450, 300, 200, 150, 550], // Valores em reais
        backgroundColor: [
          'rgba(54, 153, 255, 0.8)',   // Azul para alimentação
          'rgba(28, 50, 56, 0.8)',     // Verde escuro para transporte
          'rgba(63, 43, 19, 0.8)',     // Marrom para lazer
          'rgba(246, 78, 96, 0.8)',    // Vermelho para saúde
          'rgba(43, 50, 82, 0.8)',     // Azul escuro para educação
          'rgba(58, 36, 52, 0.8)'      // Roxo para outros
        ],
        borderColor: [
          'rgba(54, 153, 255, 1)',
          'rgba(28, 50, 56, 1)',
          'rgba(63, 43, 19, 1)',
          'rgba(246, 78, 96, 1)',
          'rgba(43, 50, 82, 1)',
          'rgba(58, 36, 52, 1)'
        ],
        borderWidth: 1
      }]
    };
  }
  
  /**
   * Carrega dados mock para demonstração
   * 
   * Em produção, estes dados viriam do backend através
   * de chamadas para APIs de transações e cartões de crédito.
   */
  private loadMockData() {
    // Estatísticas financeiras principais
    this.totalSpent = 2450;      // R$ 2.450,00 gastos
    this.totalIncome = 5200;     // R$ 5.200,00 recebidos
    this.balance = 2750;         // R$ 2.750,00 saldo
    this.creditCardLimit = 10000; // R$ 10.000,00 limite
    this.creditCardUsed = 2450;   // R$ 2.450,00 usado
    
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
}
