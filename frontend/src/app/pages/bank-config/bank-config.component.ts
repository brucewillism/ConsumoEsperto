import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule, FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBarModule, MatSnackBar } from '@angular/material/snack-bar';
import { AuthService } from '../../services/auth.service';
import { BankApiService } from '../../services/bank-api.service';

export interface BankConfig {
  id?: number;
  bankName: string;
  bankCode: string;
  accessToken?: string; // Para Mercado Pago
  publicKey?: string;   // Para Mercado Pago
  clientId: string;
  clientSecret: string;
  userId?: string;
  apiUrl: string;
  authUrl?: string;
  tokenUrl?: string;
  redirectUri?: string;
  scope?: string;
  isSandbox: boolean;
  isActive: boolean;
  timeoutMs: number;
  maxRetries: number;
  retryDelayMs: number;
}

@Component({
  selector: 'app-bank-config',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatChipsModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    MatProgressSpinnerModule,
    MatSnackBarModule
  ],
  templateUrl: './bank-config.component.html',
  styleUrls: ['./bank-config.component.scss']
})
export class BankConfigComponent implements OnInit {
  
  // Dados dos bancos
  bankConfigs: BankConfig[] = [];
  selectedBank: BankConfig | null = null;
  
  // Estados da UI
  loading = false;
  showForm = false;
  editing = false;
  
  // Formulário
  bankForm: FormGroup;
  
  // Bancos disponíveis para configuração
  availableBanks = [
    {
      code: 'MERCADO_PAGO',
      name: 'Mercado Pago',
      description: 'Cartões de crédito, saldo e transações',
      icon: 'credit_card',
      color: '#009ee3'
    },
    {
      code: 'ITAU',
      name: 'Itaú',
      description: 'Open Banking completo',
      icon: 'account_balance',
      color: '#ec7000'
    },
    {
      code: 'INTER',
      name: 'Inter',
      description: 'APIs Open Banking',
      icon: 'account_balance',
      color: '#ff7a00'
    },
    {
      code: 'NUBANK',
      name: 'Nubank',
      description: 'Cartões e conta digital',
      icon: 'credit_card',
      color: '#8a05be'
    }
  ];

  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private bankApiService: BankApiService,
    public router: Router,
    private snackBar: MatSnackBar
  ) {
    console.log('[BankConfigComponent] Construtor chamado');
    this.bankForm = this.initForm();
  }

  ngOnInit(): void {
    // Verifica se o usuário está autenticado
    if (!this.authService.isAuthenticated()) {
      console.log('[BankConfigComponent] Usuário não autenticado, redirecionando para login...');
      this.router.navigate(['/login']);
      return;
    }

    console.log('[BankConfigComponent] Usuário autenticado, carregando configurações...');
    
    // Garantir que começa mostrando a lista, não o formulário
    this.showForm = false;
    this.editing = false;
    this.selectedBank = null;
    
    this.loadBankConfigs();
  }

  /**
   * Inicializa o formulário
   */
  private initForm(): FormGroup {
    const form = this.fb.group({
      bankName: ['', Validators.required],
      bankCode: ['', Validators.required],
      accessToken: ['', Validators.required], // Para Mercado Pago
      publicKey: ['', Validators.required],   // Para Mercado Pago
      clientId: ['', Validators.required],
      clientSecret: ['', Validators.required],
      userId: [''],
      apiUrl: ['', Validators.required],
      authUrl: [''],
      tokenUrl: [''],
      redirectUri: [''],
      scope: [''],
      isSandbox: [true],
      isActive: [true],
      timeoutMs: [30000, [Validators.required, Validators.min(1000)]],
      maxRetries: [3, [Validators.required, Validators.min(0)]],
      retryDelayMs: [1000, [Validators.required, Validators.min(100)]]
    });
    return form;
  }

  /**
   * Carrega as configurações dos bancos
   */
  loadBankConfigs(): void {
    this.loading = true;
    console.log('[BankConfigComponent] 🔍 Carregando configurações dos bancos...');
    
    // Timeout de segurança para evitar travamento
    const timeoutId = setTimeout(() => {
      console.warn('[BankConfigComponent] ⚠️ Timeout de carregamento atingido (10s)');
      this.loading = false;
      this.showErrorMessage('⚠️ Timeout ao carregar configurações. Tente novamente.');
    }, 10000);
    
    // PRIMEIRO: Testar conectividade com o backend
    console.log('[BankConfigComponent] 🧪 Testando conectividade com o backend...');
    this.bankApiService.testBackendConnection().subscribe({
      next: (testResponse) => {
        console.log('[BankConfigComponent] ✅ Conectividade com backend OK:', testResponse);
        
        // SEGUNDO: Agora buscar configurações
        console.log('[BankConfigComponent] 🔍 Buscando configurações bancárias...');
        this.bankApiService.getBankConfigs().subscribe({
          next: (configs) => {
            console.log('[BankConfigComponent] ✅ Configurações carregadas do backend:', configs);
            
            // Mapear dados do backend para o formato esperado pelo frontend
            this.bankConfigs = configs.map((config: any) => ({
              id: config.id,
              bankName: config.banco || config.bankName || 'Mercado Pago',
              bankCode: config.banco || config.bankCode || 'MERCADOPAGO', // Manter valor original do banco
              accessToken: config.access_token || config.accessToken || '',
              publicKey: config.public_key || config.publicKey || '',
              clientId: config.client_id || config.clientId || '',
              clientSecret: config.client_secret || config.clientSecret || '',
              userId: config.user_id || config.userId || '',
              apiUrl: config.api_url || config.apiUrl || 'https://api.mercadopago.com/v1',
              authUrl: config.auth_url || config.authUrl || 'https://api.mercadopago.com/authorization',
              tokenUrl: config.token_url || config.tokenUrl || '',
              redirectUri: config.redirect_uri || config.redirectUri || '',
              scope: config.scope || '',
              isSandbox: config.sandbox !== undefined ? config.sandbox : false,
              isActive: config.ativo !== undefined ? config.ativo : true,
              timeoutMs: config.timeout_ms || config.timeoutMs || 30000,
              maxRetries: config.max_retries || config.maxRetries || 3,
              retryDelayMs: config.retry_delay_ms || config.retryDelayMs || 1000
            }));
            
            console.log('[BankConfigComponent] ✅ Configurações mapeadas:', this.bankConfigs);
            
            // Mostrar mensagem de sucesso se há configurações
            if (this.bankConfigs && this.bankConfigs.length > 0) {
              this.showSuccessMessage(`✅ ${this.bankConfigs.length} configuração(ões) carregada(s) do banco`);
            } else {
              console.log('[BankConfigComponent] ℹ️ Nenhuma configuração encontrada no banco');
              this.showSuccessMessage('ℹ️ Nenhuma configuração bancária encontrada. Você pode criar uma nova configuração.');
            }
            
            // Limpar timeout e parar loading
            clearTimeout(timeoutId);
            this.loading = false;
          },
          error: (configError) => {
            console.error('[BankConfigComponent] ❌ Erro ao carregar configurações:', configError);
            
            // Em caso de erro, mostrar lista vazia mas permitir criar novas
            this.bankConfigs = [];
            
            // Limpar timeout e parar loading
            clearTimeout(timeoutId);
            this.loading = false;
            
            // Mostrar erro específico para o usuário
            if (configError.status === 401) {
              this.showErrorMessage('❌ Erro de autenticação. Por favor, faça login novamente.');
            } else if (configError.status === 403) {
              this.showErrorMessage('❌ Acesso negado. Verifique suas permissões.');
            } else if (configError.status === 0) {
              this.showErrorMessage('❌ Erro de conexão. Verifique se o backend está rodando.');
            } else {
              this.showErrorMessage(`❌ Erro ao carregar configurações: ${this.extractErrorMessage(configError)}`);
            }
          }
        });
      },
      error: (testError) => {
        console.error('[BankConfigComponent] ❌ Erro de conectividade com backend:', testError);
        
        // Limpar timeout e parar loading
        clearTimeout(timeoutId);
        this.loading = false;
        
        // Mostrar erro de conectividade
        if (testError.status === 0) {
          this.showErrorMessage('❌ Backend não está acessível. Verifique se está rodando na porta 8080.');
        } else if (testError.status === 401) {
          this.showErrorMessage('❌ Erro de autenticação. Por favor, faça login novamente.');
        } else {
          this.showErrorMessage(`❌ Erro de conectividade: ${this.extractErrorMessage(testError)}`);
        }
        
        // Em caso de erro de conectividade, mostrar lista vazia
        this.bankConfigs = [];
      }
    });
  }

  /**
   * Preenche automaticamente o formulário com configuração existente
   */
  private autoFillFormWithExistingConfig(config: any): void {
    console.log('[BankConfigComponent] Preenchendo formulário com configuração existente:', config);
    
    // Mapear campos do backend para o formulário (ajustado para estrutura real do banco)
    const formData = {
      bankName: config.banco || config.bankName || 'Mercado Pago',
      bankCode: config.banco || config.bankCode || 'MERCADOPAGO', // Manter valor original do banco
      accessToken: config.access_token || config.accessToken || '',
      publicKey: config.public_key || config.publicKey || '',
      clientId: config.client_id || config.clientId || '',
      clientSecret: config.client_secret || config.clientSecret || '',
      userId: config.user_id || config.userId || '',
      apiUrl: config.api_url || config.apiUrl || 'https://api.mercadopago.com/v1',
      authUrl: config.auth_url || config.authUrl || 'https://api.mercadopago.com/authorization',
      tokenUrl: config.token_url || config.tokenUrl || '',
      redirectUri: config.redirect_uri || config.redirectUri || '',
      scope: config.scope || '',
      isSandbox: config.sandbox !== undefined ? config.sandbox : false,
      isActive: config.ativo !== undefined ? config.ativo : true,
      timeoutMs: config.timeout_ms || config.timeoutMs || 30000,
      maxRetries: config.max_retries || config.maxRetries || 3,
      retryDelayMs: config.retry_delay_ms || config.retryDelayMs || 1000
    };
    
    console.log('[BankConfigComponent] Dados mapeados para o formulário:', formData);
    
    // Preencher o formulário
    this.bankForm.patchValue(formData);
    
    // Mostrar o formulário preenchido
    this.showForm = true;
    this.editing = true;
    this.selectedBank = config;
    
    console.log('[BankConfigComponent] Formulário preenchido automaticamente');
    
    // Ativar a integração por padrão se a configuração estiver ativa
    if (formData.isActive) {
      this.activateBankIntegration(config);
    }
  }

  /**
   * Ativa a integração com o banco
   */
  private activateBankIntegration(config: BankConfig): void {
    console.log('[BankConfigComponent] Ativando integração com:', config.bankName);
    
    // Aqui você pode implementar a lógica para ativar a integração
    // Por exemplo, fazer uma chamada para testar a conexão
    this.showSuccessMessage(`🔗 Integração com ${config.bankName} ativada com sucesso!`);
  }

  // MÉTODO REMOVIDO - NÃO MAIS CRIAR CONFIGURAÇÕES PADRÃO
  // O sistema deve funcionar APENAS com dados reais do banco

  /**
   * Obtém valores padrão para cada banco
   */
  private getDefaultApiUrl(bankCode: string): string {
    switch (bankCode) {
      case 'MERCADO_PAGO': return 'https://api.mercadopago.com/v1';
      case 'ITAU': return 'https://openbanking.itau.com.br/api';
      case 'INTER': return 'https://cdp.openbanking.bancointer.com.br/api';
      case 'NUBANK': return 'https://api.nubank.com.br/api';
      default: return '';
    }
  }

  private getDefaultAuthUrl(bankCode: string): string {
    switch (bankCode) {
      case 'MERCADO_PAGO': return 'https://api.mercadopago.com/authorization';
      case 'ITAU': return 'https://openbanking.itau.com.br/oauth/authorize';
      case 'INTER': return 'https://cdp.openbanking.bancointer.com.br/oauth/authorize';
      case 'NUBANK': return 'https://api.nubank.com.br/oauth/authorize';
      default: return '';
    }
  }

  private getDefaultTokenUrl(bankCode: string): string {
    switch (bankCode) {
      case 'MERCADO_PAGO': return 'https://api.mercadopago.com/oauth/token';
      case 'ITAU': return 'https://openbanking.itau.com.br/oauth/token';
      case 'INTER': return 'https://cdp.openbanking.bancointer.com.br/oauth/token';
      case 'NUBANK': return 'https://api.nubank.com.br/oauth/token';
      default: return '';
    }
  }

  private getDefaultRedirectUri(bankCode: string): string {
    switch (bankCode) {
      case 'MERCADO_PAGO': return 'https://29e1b0b32eb8.ngrok-free.app/api/auth/mercadopago/callback';
      case 'ITAU': return 'https://29e1b0b32eb8.ngrok-free.app/api/auth/itau/callback';
      case 'INTER': return 'https://29e1b0b32eb8.ngrok-free.app/api/auth/inter/callback';
      case 'NUBANK': return 'https://29e1b0b32eb8.ngrok-free.app/api/auth/nubank/callback';
      default: return '';
    }
  }

  private getDefaultScope(bankCode: string): string {
    switch (bankCode) {
      case 'MERCADO_PAGO': return 'read,write';
      case 'ITAU': return 'openid,profile,email,accounts,transactions';
      case 'INTER': return 'openid,profile,email,accounts,transactions';
      case 'NUBANK': return 'openid,profile,email,accounts,transactions';
      default: return '';
    }
  }

  /**
   * Abre o formulário para criar uma nova configuração
   */
  createConfig(bankCode: string): void {
    console.log('[BankConfigComponent] 🆕 Criando nova configuração para:', bankCode);
    
    const bank = this.availableBanks.find(b => b.code === bankCode);
    if (bank) {
      // Preencher campos padrão baseados no banco
      const defaultValues = {
        bankName: bank.name,
        bankCode: bank.code,
        accessToken: '', // Para Mercado Pago
        publicKey: '',   // Para Mercado Pago
        clientId: '',
        clientSecret: '',
        userId: '',
        apiUrl: this.getDefaultApiUrl(bank.code),
        authUrl: this.getDefaultAuthUrl(bank.code),
        tokenUrl: this.getDefaultTokenUrl(bank.code),
        redirectUri: this.getDefaultRedirectUri(bank.code),
        scope: this.getDefaultScope(bank.code),
        isSandbox: true,
        isActive: true,
        timeoutMs: 30000,
        maxRetries: 3,
        retryDelayMs: 1000
      };
      
      console.log('[BankConfigComponent] 🆕 Valores padrão para nova configuração:', defaultValues);
      
      this.bankForm.patchValue(defaultValues);
    }
    
    this.editing = false;
    this.selectedBank = null;
    this.showForm = true;
    
    console.log('[BankConfigComponent] 🆕 Formulário aberto para nova configuração');
    this.showSuccessMessage(`🆕 Criando configuração para ${bank?.name || bankCode}`);
  }

  /**
   * Abre o formulário para editar uma configuração
   */
  editConfig(bank: BankConfig): void {
    console.log('[BankConfigComponent] Editando configuração:', bank);
    
    // Mapear dados para o formulário
    const formData = {
      bankName: bank.bankName,
      bankCode: bank.bankCode,
      accessToken: bank.accessToken || '',
      publicKey: bank.publicKey || '',
      clientId: bank.clientId,
      clientSecret: bank.clientSecret,
      userId: bank.userId || '',
      apiUrl: bank.apiUrl,
      authUrl: bank.authUrl || '',
      tokenUrl: bank.tokenUrl || '',
      redirectUri: bank.redirectUri || '',
      scope: bank.scope || '',
      isSandbox: bank.isSandbox,
      isActive: bank.isActive,
      timeoutMs: bank.timeoutMs,
      maxRetries: bank.maxRetries,
      retryDelayMs: bank.retryDelayMs
    };
    
    console.log('[BankConfigComponent] Dados mapeados para edição:', formData);
    
    // Preencher o formulário
    this.bankForm.patchValue(formData);
    
    this.selectedBank = bank;
    this.editing = true;
    this.showForm = true;
    
    console.log('[BankConfigComponent] Formulário aberto para edição');
  }

  /**
   * Salva a configuração
   */
  saveConfig(): void {
    console.log('[BankConfigComponent] 💾 Salvando configuração...');
    
    if (this.bankForm.valid) {
      const formData = this.bankForm.value;
      
      // Mapear campos do formulário para o formato esperado pelo backend
      const config = {
        id: this.editing && this.selectedBank ? this.selectedBank.id : undefined,
        nome: formData.bankName, // Campo obrigatório para o backend
        tipoBanco: formData.bankCode, // Campo obrigatório para o backend
        banco: formData.bankCode, // Campo de compatibilidade
        bankName: formData.bankName,
        bankCode: formData.bankCode,
        accessToken: formData.accessToken,
        publicKey: formData.publicKey,
        clientId: formData.clientId,
        clientSecret: formData.clientSecret,
        userId: formData.userId,
        apiUrl: formData.apiUrl,
        authUrl: formData.authUrl,
        tokenUrl: formData.tokenUrl,
        redirectUri: formData.redirectUri,
        scope: formData.scope,
        isSandbox: formData.isSandbox,
        ativo: formData.isActive, // Campo obrigatório para o backend
        isActive: formData.isActive,
        timeoutMs: formData.timeoutMs,
        maxRetries: formData.maxRetries,
        retryDelayMs: formData.retryDelayMs
      };
      
      console.log('[BankConfigComponent] 💾 Configuração mapeada para salvar:', config);
      
      // SALVAR NO BACKEND usando endpoint padrão
      if (this.editing && this.selectedBank && config.id) {
        // Atualizar configuração existente
        console.log('[BankConfigComponent] 💾 Atualizando configuração existente ID:', config.id);
        this.bankApiService.updateBankConfig(config.id, config).subscribe({
          next: (response) => {
            console.log('[BankConfigComponent] ✅ Configuração atualizada com sucesso:', response);
            this.showSuccessMessage('✅ Configuração atualizada com sucesso!');
            this.loadBankConfigs(); // Recarregar do backend
            this.closeForm();
          },
          error: (error) => {
            console.error('[BankConfigComponent] ❌ Erro ao atualizar configuração:', error);
            let errorMessage = this.extractErrorMessage(error);
            this.showErrorMessage('❌ Erro ao atualizar configuração: ' + errorMessage);
          }
        });
      } else {
        // Criar nova configuração
        console.log('[BankConfigComponent] 💾 Criando nova configuração');
        this.bankApiService.saveBankConfig(config).subscribe({
          next: (response) => {
            console.log('[BankConfigComponent] ✅ Configuração salva com sucesso:', response);
            this.showSuccessMessage('✅ Configuração salva com sucesso!');
            this.loadBankConfigs(); // Recarregar do backend
            this.closeForm();
          },
          error: (error) => {
            console.error('[BankConfigComponent] ❌ Erro ao salvar configuração:', error);
            let errorMessage = this.extractErrorMessage(error);
            this.showErrorMessage('❌ Erro ao salvar configuração: ' + errorMessage);
          }
        });
      }
    } else {
      console.error('[BankConfigComponent] ❌ Formulário inválido:', this.bankForm.errors);
      this.showErrorMessage('❌ Por favor, preencha todos os campos obrigatórios.');
    }
  }

  /**
   * Testa a conexão com o banco
   */
  testConnection(bank: BankConfig): void {
    console.log('[BankConfigComponent] 🧪 Testando conexão com:', bank.bankName);
    
    if (!bank.id) {
      this.showErrorMessage('❌ Configuração não possui ID válido para teste');
      return;
    }
    
    this.showSuccessMessage(`🔄 Testando conexão com ${bank.bankName}...`);
    
    // Testar conexão real com a API
    this.bankApiService.testBankConnection(bank.id).subscribe({
      next: (response) => {
        console.log('[BankConfigComponent] ✅ Resposta do teste de conexão:', response);
        
        if (response.success) {
          this.showSuccessMessage(`✅ ${response.message}`);
          // Recarregar configurações para atualizar o status
          this.loadBankConfigs();
        } else {
          this.showErrorMessage(`❌ ${response.message}`);
        }
      },
      error: (error) => {
        console.error('[BankConfigComponent] ❌ Erro ao testar conexão:', error);
        let errorMessage = this.extractErrorMessage(error);
        this.showErrorMessage(`❌ Erro ao testar conexão: ${errorMessage}`);
      }
    });
  }

  /**
   * Busca dados reais do banco
   */
  fetchRealData(bank: BankConfig): void {
    console.log('[BankConfigComponent] 📊 Buscando dados reais de:', bank.bankName);
    
    this.showSuccessMessage(`🔄 Sincronizando dados de ${bank.bankName}...`);
    
    // Para Mercado Pago, usar o endpoint de sincronização
    if (bank.bankCode === 'MERCADO_PAGO' || bank.bankCode === 'MERCADOPAGO') {
      this.bankApiService.syncMercadoPagoData().subscribe({
        next: (response) => {
          console.log('[BankConfigComponent] ✅ Dados sincronizados:', response);
          
          if (response && response.success) {
            const cartoes = response.cartoes_sincronizados || 0;
            const faturas = response.faturas_sincronizadas || 0;
            const transacoes = response.transacoes_sincronizadas || 0;
            
            this.showSuccessMessage(
              `✅ Sincronização concluída! ${cartoes} cartões, ${faturas} faturas, ${transacoes} transações`
            );
            
            console.log('[BankConfigComponent] 📊 Dados sincronizados:', {
              cartoes: cartoes,
              faturas: faturas,
              transacoes: transacoes
            });
          } else {
            this.showErrorMessage(`❌ Falha na sincronização: ${response?.erro || 'Erro desconhecido'}`);
          }
        },
        error: (error) => {
          console.error('[BankConfigComponent] ❌ Erro ao sincronizar dados:', error);
          let errorMessage = this.extractErrorMessage(error);
          this.showErrorMessage(`❌ Erro ao sincronizar dados: ${errorMessage}`);
        }
      });
    } else {
      // Para outros bancos, usar o método original
      this.bankApiService.getRealCreditCards(bank.bankCode).subscribe({
        next: (response) => {
          console.log('[BankConfigComponent] ✅ Dados reais obtidos:', response);
          
          if (response && response.cartoes) {
            const cartoesCount = Array.isArray(response.cartoes) ? response.cartoes.length : 0;
            this.showSuccessMessage(`✅ ${cartoesCount} cartão(ões) encontrado(s) em ${bank.bankName}!`);
            
            console.log('[BankConfigComponent] 📊 Dados dos cartões:', response.cartoes);
          } else {
            this.showSuccessMessage(`ℹ️ Nenhum cartão encontrado em ${bank.bankName}`);
          }
        },
        error: (error) => {
          console.error('[BankConfigComponent] ❌ Erro ao buscar dados reais:', error);
          let errorMessage = this.extractErrorMessage(error);
          this.showErrorMessage(`❌ Erro ao buscar dados reais: ${errorMessage}`);
        }
      });
    }
  }

  /**
   * Ativa/desativa uma configuração
   */
  toggleActive(bank: BankConfig): void {
    const newStatus = !bank.isActive;
    console.log('[BankConfigComponent] Alterando status de', bank.bankName, 'de', bank.isActive, 'para', newStatus);
    
    // Atualizar o objeto local primeiro para feedback visual imediato
    bank.isActive = newStatus;
    
    // SALVAR NO BACKEND usando endpoint padrão
    if (bank.id) {
      // Mapear para o formato esperado pelo backend
      const config = {
        id: bank.id,
        nome: bank.bankName,
        tipoBanco: bank.bankCode,
        banco: bank.bankCode,
        clientId: bank.clientId,
        clientSecret: bank.clientSecret,
        apiUrl: bank.apiUrl,
        ativo: newStatus,
        isActive: newStatus
      };
      
      // Atualizar configuração existente
      this.bankApiService.updateBankConfig(bank.id, config).subscribe({
        next: (response) => {
          console.log('[BankConfigComponent] ✅ Status atualizado com sucesso:', response);
          this.showSuccessMessage(`✅ ${bank.bankName} ${bank.isActive ? 'ativado' : 'desativado'} com sucesso!`);
          // Recarregar configurações para sincronizar com o backend
          this.loadBankConfigs();
        },
        error: (error) => {
          console.error('[BankConfigComponent] ❌ Erro ao atualizar status:', error);
          // Reverter a mudança em caso de erro
          bank.isActive = !newStatus;
          let errorMessage = this.extractErrorMessage(error);
          this.showErrorMessage(`❌ Erro ao ${newStatus ? 'ativar' : 'desativar'} ${bank.bankName}: ` + errorMessage);
        }
      });
    } else {
      this.showErrorMessage('❌ Configuração não possui ID válido para atualização');
      // Reverter a mudança
      bank.isActive = !newStatus;
    }
  }

  /**
   * Fecha o formulário
   */
  closeForm(): void {
    this.showForm = false;
    this.editing = false;
    this.selectedBank = null;
    this.bankForm.reset();
  }

  /**
   * Obtém a cor do banco
   */
  getBankColor(bankCode: string): string {
    // Cores padrão baseadas no código do banco
    switch (bankCode) {
      case 'MERCADO_PAGO': return '#009ee3';
      case 'ITAU': return '#ec7000';
      case 'INTER': return '#ff7a00';
      case 'NUBANK': return '#8a05be';
      default: return '#666';
    }
  }

  /**
   * Obtém o ícone do banco
   */
  getBankIcon(bankCode: string): string {
    // Ícones padrão baseados no código do banco
    switch (bankCode) {
      case 'MERCADO_PAGO': return 'credit_card';
      case 'ITAU': return 'account_balance';
      case 'INTER': return 'account_balance';
      case 'NUBANK': return 'credit_card';
      default: return 'account_balance';
    }
  }

  /**
   * Obtém a descrição do banco
   */
  getBankDescription(bankCode: string): string {
    // Descrições padrão baseadas no código do banco
    switch (bankCode) {
      case 'MERCADO_PAGO': return 'Cartões de crédito, saldo e transações';
      case 'ITAU': return 'Open Banking completo';
      case 'INTER': return 'APIs Open Banking';
      case 'NUBANK': return 'Cartões e conta digital';
      default: return 'Banco não configurado';
    }
  }

  /**
   * Obtém o status do banco
   */
  getBankStatus(bank: BankConfig): string {
    if (bank.isActive === false) return 'Inativo';
    if (bank.isSandbox === true) return 'Sandbox';
    return 'Ativo';
  }

  /**
   * Obtém a cor do status
   */
  getStatusColor(status: string): string {
    switch (status) {
      case 'Ativo': return 'primary';
      case 'Sandbox': return 'accent';
      case 'Inativo': return 'warn';
      default: return 'default';
    }
  }

  /**
   * Obtém bancos disponíveis para configuração (não configurados ainda)
   */
  getAvailableBanksForConfig(): any[] {
    const configuredBankCodes = this.bankConfigs.map(config => config.bankCode);
    const availableBanks = this.availableBanks.filter(bank => !configuredBankCodes.includes(bank.code));
    return availableBanks;
  }

  /**
   * Exibe mensagem de sucesso
   */
  private showSuccessMessage(message: string): void {
    this.snackBar.open(message, 'Fechar', {
      duration: 3000,
      panelClass: ['success-snackbar']
    });
  }

  /**
   * Exibe mensagem de erro
   */
  private showErrorMessage(message: string): void {
    this.snackBar.open(message, 'Fechar', {
      duration: 5000,
      panelClass: ['error-snackbar']
    });
  }

  /**
   * Extrai mensagem de erro de forma robusta
   */
  private extractErrorMessage(error: any): string {
    if (error.error) {
      if (typeof error.error === 'string') {
        return error.error;
      } else if (error.error.message) {
        return error.error.message;
      } else {
        return JSON.stringify(error.error);
      }
    } else if (error.message) {
      return error.message;
    } else if (error.status) {
      return `Erro HTTP ${error.status}: ${error.statusText || 'Erro no servidor'}`;
    }
    return 'Erro desconhecido';
  }
}
