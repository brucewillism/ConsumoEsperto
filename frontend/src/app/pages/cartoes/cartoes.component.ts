import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule, FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatNativeDateModule } from '@angular/material/core';
import { BankApiService, BankConnection, Invoice, CreditCard } from '../../services/bank-api.service';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-cartoes',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule, 
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatChipsModule,
    MatDatepickerModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    MatProgressSpinnerModule,
    MatNativeDateModule
  ],
  templateUrl: './cartoes.component.html',
  styleUrls: ['./cartoes.component.scss']
})
export class CartoesComponent implements OnInit {
  
  // Data properties
  creditCards: CreditCard[] = [];
  connectedBanks: BankConnection[] = [];
  invoices: Invoice[] = [];
  contas: any[] = [];
  cartoes: CreditCard[] = [];
  
  // Summary properties
  totalCreditLimit: number = 0;
  totalAvailableCredit: number = 0;
  totalBalance: number = 0;
<<<<<<< HEAD

  // Mercado Pago status
  mercadoPagoStatus: any = null;
  showMercadoPagoConfig = false;
=======
>>>>>>> origin/main
  
  // UI properties
  loading = false;
  error: string | null = null;
  showForm = false;
  
  // Form properties
  novoCartaoForm!: FormGroup;
  
  // Filtros
  selectedBank: string = 'all';
  selectedStatus: string = 'all';
  searchTerm: string = '';

  constructor(
    private fb: FormBuilder,
    public bankApiService: BankApiService,
    private authService: AuthService,
    private router: Router
  ) {
    this.initForm();
  }

  ngOnInit(): void {
    console.log('[CartoesComponent] ngOnInit iniciado');
    console.log('[CartoesComponent] Verificando autenticação...');
    
    // Verifica se o usuário está autenticado
    if (!this.authService.isAuthenticated()) {
      console.log('[CartoesComponent] Usuário não autenticado, redirecionando para login...');
      this.router.navigate(['/login']);
      return;
    }

    console.log('[CartoesComponent] Usuário autenticado, carregando dados...');
    console.log('[CartoesComponent] Token disponível:', this.authService.getToken());
    this.loadData();
  }

  /**
   * Inicializa o formulário
   */
  private initForm(): void {
    this.novoCartaoForm = this.fb.group({
      banco: ['', Validators.required],
      numero: ['', [Validators.required, Validators.minLength(13)]],
      titular: ['', Validators.required],
      limite: [0, [Validators.required, Validators.min(0.01)]],
      vencimento: ['', Validators.required],
      fechamento: ['', Validators.required]
    });
  }

  /**
   * Carrega todos os dados necessários
   */
  loadData(): void {
    console.log('[CartoesComponent] Iniciando carregamento de dados...');
    console.log('[CartoesComponent] Token atual:', this.authService.getToken());
    this.loading = true;
    this.error = null;

    // Carrega bancos conectados
    console.log('[CartoesComponent] Carregando bancos conectados...');
    this.bankApiService.getConnectedBanks().subscribe({
      next: (banks) => {
        console.log('[CartoesComponent] Bancos conectados carregados:', banks);
        this.connectedBanks = banks;
        console.log('Bancos conectados:', banks);
      },
      error: (err) => {
        console.error('[CartoesComponent] Erro ao carregar bancos:', err);
        console.error('[CartoesComponent] Status do erro:', err.status);
        console.error('[CartoesComponent] Mensagem do erro:', err.message);
        this.error = 'Erro ao carregar bancos conectados';
      }
    });

    // Carrega cartões de crédito
    console.log('[CartoesComponent] Carregando cartões de crédito...');
    this.bankApiService.getCreditCards().subscribe({
      next: (cards) => {
        console.log('[CartoesComponent] Cartões de crédito carregados:', cards);
        this.creditCards = cards;
        this.cartoes = cards; // Mapeia para a propriedade usada no template
        console.log('Cartões carregados:', cards);
      },
      error: (err) => {
        console.error('[CartoesComponent] Erro ao carregar cartões:', err);
        console.error('[CartoesComponent] Status do erro:', err.status);
        console.error('[CartoesComponent] Mensagem do erro:', err.message);
        this.error = 'Erro ao carregar cartões de crédito';
      }
    });

    // Verificar status do Mercado Pago
    this.verificarStatusMercadoPago();

    // Carrega faturas
    console.log('[CartoesComponent] Carregando faturas...');
    this.bankApiService.getInvoices().subscribe({
      next: (invoices) => {
        console.log('[CartoesComponent] Faturas carregadas:', invoices);
        this.invoices = invoices;
        console.log('Faturas carregadas:', invoices);
      },
      error: (err) => {
        console.error('[CartoesComponent] Erro ao carregar faturas:', err);
        console.error('[CartoesComponent] Status do erro:', err.status);
        console.error('[CartoesComponent] Mensagem do erro:', err.message);
        this.error = 'Erro ao carregar faturas';
      }
    });

    // Carrega totais consolidados
    console.log('[CartoesComponent] Carregando totais consolidados...');
    this.bankApiService.getConsolidatedStats().subscribe({
      next: (stats) => {
        console.log('[CartoesComponent] Estatísticas consolidadas carregadas:', stats);
        this.totalCreditLimit = stats.totalCreditLimit;
        this.totalAvailableCredit = stats.totalAvailableCredit;
        this.totalBalance = stats.totalBalance;
      },
      error: (err) => {
        console.error('[CartoesComponent] Erro ao carregar estatísticas:', err);
        // Fallback para valores locais
        this.totalCreditLimit = this.getTotalCreditLimit();
        this.totalAvailableCredit = this.getTotalAvailableCredit();
        this.totalBalance = 0; // Não há implementação local para saldo
      },
      complete: () => {
        console.log('[CartoesComponent] Carregamento de dados concluído');
        this.loading = false;
      }
    });

    // Carrega contas (placeholder - implementar quando disponível)
    this.contas = [];
  }

  /**
   * Sincroniza dados de um banco específico
   */
  syncBank(bankId: number): void {
    this.loading = true;
    this.bankApiService.syncBankData(bankId).subscribe({
      next: (result) => {
        console.log('Sincronização concluída:', result);
        // Recarrega os dados após sincronização
        this.loadData();
      },
      error: (err) => {
        console.error('Erro na sincronização:', err);
        this.error = 'Erro ao sincronizar dados do banco';
          this.loading = false;
        },
      complete: () => {
          this.loading = false;
        }
      });
  }

  /**
   * Sincroniza dados de todos os bancos
   */
  syncAllBanks(): void {
    this.loading = true;
    this.bankApiService.syncAllBanks().subscribe({
      next: (result) => {
        console.log('Sincronização geral concluída:', result);
        // Recarrega os dados após sincronização
        this.loadData();
      },
      error: (err) => {
        console.error('Erro na sincronização geral:', err);
        this.error = 'Erro ao sincronizar dados dos bancos';
        this.loading = false;
      },
      complete: () => {
        this.loading = false;
      }
      });
  }

  /**
   * Conecta um novo banco
   */
  connectBank(bankType: string): void {
    this.bankApiService.getBankAuthUrl(bankType).subscribe({
        next: (response) => {
        if (response.authUrl) {
          // Abre a URL de autorização em uma nova janela
          window.open(response.authUrl, '_blank', 'width=600,height=700');
        }
      },
      error: (err) => {
        console.error('Erro ao obter URL de autorização:', err);
        this.error = 'Erro ao conectar banco';
      }
    });
  }

  /**
   * Desconecta um banco
   */
  disconnectBank(bankId: number): void {
    if (confirm('Tem certeza que deseja desconectar este banco?')) {
      this.bankApiService.disconnectBank(bankId).subscribe({
        next: () => {
          console.log('Banco desconectado com sucesso');
          this.loadData(); // Recarrega os dados
        },
        error: (err) => {
          console.error('Erro ao desconectar banco:', err);
          this.error = 'Erro ao desconectar banco';
        }
      });
    }
  }

  /**
   * Adiciona um novo cartão
   */
  adicionarCartao(): void {
    if (this.novoCartaoForm.valid) {
      const cartao = this.novoCartaoForm.value;
      console.log('Adicionando cartão:', cartao);
      // TODO: Implementar chamada para API
      this.showForm = false;
      this.novoCartaoForm.reset();
      this.loadData();
    }
  }

  /**
   * Atualiza dados
   */
  atualizarDados(): void {
    this.loadData();
  }

  /**
   * Obtém cor do banco
   */
  getBancoColor(banco: string): string {
    const cores: { [key: string]: string } = {
      'itau': '#EC7000',
      'nubank': '#8A05BE',
      'inter': '#FF7A00',
      'mercadopago': '#009EE3'
    };
    return cores[banco.toLowerCase()] || '#666';
  }

  /**
   * Obtém nome do banco
   */
  getBancoNome(banco: string): string {
    const nomes: { [key: string]: string } = {
      'itau': 'Itaú',
      'nubank': 'Nubank',
      'inter': 'Banco Inter',
      'mercadopago': 'Mercado Pago'
    };
    return nomes[banco.toLowerCase()] || banco;
  }

  /**
   * Conecta banco
   */
  conectarBanco(banco: any): void {
    this.connectBank(banco.bankName);
  }

  /**
   * Obtém cor do status
   */
  getStatusColor(status: string): string {
    switch (status.toLowerCase()) {
      case 'active':
      case 'ativo':
        return 'primary';
      case 'inactive':
      case 'inativo':
      case 'blocked':
      case 'bloqueado':
        return 'warn';
      case 'pending':
      case 'pendente':
        return 'accent';
      default:
        return 'primary';
    }
  }

  /**
   * Obtém status do cartão
   */
  getStatusCartao(cartao: any): string {
    return cartao.status || 'Ativo';
  }

  /**
   * Formata moeda
   */
  formatarMoeda(value: number): string {
    return new Intl.NumberFormat('pt-BR', {
      style: 'currency',
      currency: 'BRL'
    }).format(value);
  }

  /**
   * Obtém limite utilizado
   */
  getLimiteUtilizado(cartao: any): number {
    return cartao.limit - cartao.available;
  }

  /**
   * Formata percentual
   */
  formatarPercentual(value: number): string {
    return `${value.toFixed(1)}%`;
  }

  /**
   * Obtém percentual de uso
   */
  getPercentualUso(cartao: any): number {
    if (cartao.limit <= 0) return 0;
    return ((cartao.limit - cartao.available) / cartao.limit) * 100;
  }

  /**
   * Visualiza faturas
   */
  visualizarFaturas(cartao: any): void {
    console.log('Visualizando faturas do cartão:', cartao);
    // TODO: Implementar navegação para faturas
  }

  /**
   * Edita cartão
   */
  editarCartao(cartao: any): void {
    console.log('Editando cartão:', cartao);
    // TODO: Implementar edição
  }

  /**
   * Exclui cartão
   */
  excluirCartao(cartao: any): void {
    if (confirm('Tem certeza que deseja excluir este cartão?')) {
      console.log('Excluindo cartão:', cartao);
      // TODO: Implementar exclusão
      this.loadData();
    }
  }

  /**
   * Formata data
   */
  formatarData(dateString: string): string {
    if (!dateString) return '';
    return new Date(dateString).toLocaleDateString('pt-BR');
  }

  /**
   * Filtra cartões por banco
   */
  getFilteredCards(): CreditCard[] {
    let filtered = this.creditCards;

    // Filtro por banco
    if (this.selectedBank !== 'all') {
      filtered = filtered.filter(card => card.bank === this.selectedBank);
    }

    // Filtro por status
    if (this.selectedStatus !== 'all') {
      filtered = filtered.filter(card => card.status === this.selectedStatus);
    }

    // Filtro por termo de busca
    if (this.searchTerm.trim()) {
      const term = this.searchTerm.toLowerCase();
      filtered = filtered.filter(card => 
        card.name.toLowerCase().includes(term) ||
        card.number.includes(term) ||
        card.bank.toLowerCase().includes(term)
      );
    }

    return filtered;
  }

  /**
   * Filtra faturas por cartão
   */
  getFilteredInvoices(): Invoice[] {
    let filtered = this.invoices;

    // Filtro por banco (se aplicável)
    if (this.selectedBank !== 'all') {
      filtered = filtered.filter(invoice => {
        const card = this.creditCards.find(c => c.id === invoice.id);
        return card && card.bank === this.selectedBank;
      });
    }

    // Filtro por status
    if (this.selectedStatus !== 'all') {
      filtered = filtered.filter(invoice => invoice.status === this.selectedStatus);
    }

    // Filtro por termo de busca
    if (this.searchTerm.trim()) {
      const term = this.searchTerm.toLowerCase();
      filtered = filtered.filter(invoice => 
        invoice.number.toLowerCase().includes(term) ||
        invoice.cardName.toLowerCase().includes(term)
      );
    }

    return filtered;
  }

  /**
   * Obtém total de limite de crédito
   */
  getTotalCreditLimit(): number {
    return this.creditCards.reduce((total, card) => total + card.limit, 0);
  }

  /**
   * Obtém total de limite disponível
   */
  getTotalAvailableCredit(): number {
    return this.creditCards.reduce((total, card) => total + card.available, 0);
  }

  /**
   * Obtém total de faturas
   */
  getTotalInvoices(): number {
    return this.invoices.reduce((total, invoice) => total + invoice.amount, 0);
  }

  /**
   * Obtém faturas vencidas
   */
  getOverdueInvoices(): Invoice[] {
    const today = new Date();
    return this.invoices.filter(invoice => {
      const dueDate = new Date(invoice.dueDate);
      return dueDate < today && invoice.status !== 'PAID';
    });
  }

  /**
   * Formata valor monetário
   */
  formatCurrency(value: number): string {
    return new Intl.NumberFormat('pt-BR', {
      style: 'currency',
      currency: 'BRL'
    }).format(value);
  }

  /**
   * Formata data
   */
  formatDate(dateString: string): string {
    return new Date(dateString).toLocaleDateString('pt-BR');
  }

  /**
   * Obtém classe CSS para status
   */
  getStatusClass(status: string): string {
    switch (status.toLowerCase()) {
      case 'active':
      case 'ativo':
        return 'status-active';
      case 'inactive':
      case 'inativo':
      case 'blocked':
      case 'bloqueado':
        return 'status-inactive';
      case 'pending':
      case 'pendente':
        return 'status-pending';
      default:
        return 'status-default';
    }
  }

  /**
   * Limpa filtros
   */
  clearFilters(): void {
    this.selectedBank = 'all';
    this.selectedStatus = 'all';
    this.searchTerm = '';
  }

  /**
   * Recarrega dados
   */
  refreshData(): void {
    this.loadData();
  }

  /**
   * Verifica o status da configuração do Mercado Pago
   */
  verificarStatusMercadoPago(): void {
    console.log('[CartoesComponent] Verificando status do Mercado Pago...');
    this.bankApiService.obterStatusMercadoPago().subscribe({
      next: (status) => {
        console.log('[CartoesComponent] Status do Mercado Pago:', status);
        this.mercadoPagoStatus = status;
        
        // Usar o campo isConfigured que considera tanto BankApiConfig quanto AutorizacaoBancaria
        if (status.isConfigured === false) {
          this.showMercadoPagoConfig = true;
        } else {
          // Se está configurado (qualquer método), esconder o card de configuração
          this.showMercadoPagoConfig = false;
        }
      },
      error: (err) => {
        console.error('[CartoesComponent] Erro ao verificar status do Mercado Pago:', err);
        this.showMercadoPagoConfig = true; // Mostrar configuração em caso de erro
      }
    });
  }

  /**
   * Configura credenciais do Mercado Pago
   */
  configurarMercadoPago(): void {
    // Para simplificar, vou usar credenciais de exemplo
    // Em produção, isso viria de um formulário
    const credentials = {
      clientId: 'SEU_CLIENT_ID_AQUI',
      clientSecret: 'SEU_CLIENT_SECRET_AQUI',
      accessToken: 'SEU_ACCESS_TOKEN_AQUI',
      banco: 'MERCADOPAGO'
    };

    console.log('[CartoesComponent] Configurando Mercado Pago...');
    this.bankApiService.configurarMercadoPago(credentials).subscribe({
      next: (response) => {
        console.log('[CartoesComponent] Mercado Pago configurado:', response);
        this.showMercadoPagoConfig = false;
        this.loadData(); // Recarregar dados
      },
      error: (err) => {
        console.error('[CartoesComponent] Erro ao configurar Mercado Pago:', err);
        this.error = 'Erro ao configurar Mercado Pago';
      }
    });
  }
}
