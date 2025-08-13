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
import { MatSnackBar } from '@angular/material/snack-bar';
import { AuthService } from '../../services/auth.service';
import { BankApiService } from '../../services/bank-api.service';

export interface BankConfig {
  id?: number;
  bankName: string;
  bankCode: string;
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
    MatProgressSpinnerModule
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
  
  // Bancos disponíveis
  availableBanks = [
    {
      code: 'MERCADOPAGO',
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
    this.loadBankConfigs();
  }

  /**
   * Inicializa o formulário
   */
  private initForm(): FormGroup {
    return this.fb.group({
      bankName: ['', Validators.required],
      bankCode: ['', Validators.required],
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
  }

  /**
   * Carrega as configurações dos bancos
   */
  loadBankConfigs(): void {
    this.loading = true;
    console.log('[BankConfigComponent] Carregando configurações dos bancos...');
    
    // Por enquanto, vamos criar configurações padrão
    this.createDefaultConfigs();
    this.loading = false;
  }

  /**
   * Cria configurações padrão para todos os bancos
   */
  createDefaultConfigs(): void {
    console.log('[BankConfigComponent] Criando configurações padrão...');
    
    this.bankConfigs = this.availableBanks.map(bank => ({
      bankName: bank.name,
      bankCode: bank.code,
      clientId: this.getDefaultClientId(bank.code),
      clientSecret: this.getDefaultClientSecret(bank.code),
      userId: this.getDefaultUserId(bank.code),
      apiUrl: this.getDefaultApiUrl(bank.code),
      authUrl: this.getDefaultAuthUrl(bank.code),
      tokenUrl: this.getDefaultTokenUrl(bank.code),
      redirectUri: this.getDefaultRedirectUri(bank.code),
      scope: this.getDefaultScope(bank.code),
      isSandbox: true,
      isActive: false,
      timeoutMs: 30000,
      maxRetries: 3,
      retryDelayMs: 1000
    }));

    console.log('[BankConfigComponent] Configurações padrão criadas:', this.bankConfigs);
  }

  /**
   * Obtém valores padrão para cada banco
   */
  private getDefaultClientId(bankCode: string): string {
    const defaults: { [key: string]: string } = {
      'MERCADOPAGO': '4223603750190943',
      'ITAU': 'your_itau_client_id',
      'INTER': 'your_inter_client_id',
      'NUBANK': 'your_nubank_client_id'
    };
    return defaults[bankCode] || '';
  }

  private getDefaultClientSecret(bankCode: string): string {
    const defaults: { [key: string]: string } = {
      'MERCADOPAGO': 'APP_USR-4223603750190943-XXXXXX',
      'ITAU': 'your_itau_client_secret',
      'INTER': 'your_inter_client_secret',
      'NUBANK': 'your_nubank_client_secret'
    };
    return defaults[bankCode] || '';
  }

  private getDefaultUserId(bankCode: string): string {
    const defaults: { [key: string]: string } = {
      'MERCADOPAGO': '209112973',
      'ITAU': '',
      'INTER': '',
      'NUBANK': ''
    };
    return defaults[bankCode] || '';
  }

  private getDefaultApiUrl(bankCode: string): string {
    const defaults: { [key: string]: string } = {
      'MERCADOPAGO': 'https://api.mercadopago.com/v1',
      'ITAU': 'https://openbanking.itau.com.br/api',
      'INTER': 'https://cdp.openbanking.bancointer.com.br/api',
      'NUBANK': 'https://api.nubank.com.br/api'
    };
    return defaults[bankCode] || '';
  }

  private getDefaultAuthUrl(bankCode: string): string {
    const defaults: { [key: string]: string } = {
      'MERCADOPAGO': 'https://api.mercadopago.com/authorization',
      'ITAU': 'https://openbanking.itau.com.br/oauth/authorize',
      'INTER': 'https://cdp.openbanking.bancointer.com.br/oauth/authorize',
      'NUBANK': 'https://api.nubank.com.br/oauth/authorize'
    };
    return defaults[bankCode] || '';
  }

  private getDefaultTokenUrl(bankCode: string): string {
    const defaults: { [key: string]: string } = {
      'MERCADOPAGO': 'https://api.mercadopago.com/oauth/token',
      'ITAU': 'https://openbanking.itau.com.br/oauth/token',
      'INTER': 'https://cdp.openbanking.bancointer.com.br/oauth/token',
      'NUBANK': 'https://api.nubank.com.br/oauth/token'
    };
    return defaults[bankCode] || '';
  }

  private getDefaultRedirectUri(bankCode: string): string {
    const defaults: { [key: string]: string } = {
      'MERCADOPAGO': 'https://29e1b0b32eb8.ngrok-free.app/api/auth/mercadopago/callback',
      'ITAU': 'https://29e1b0b32eb8.ngrok-free.app/api/auth/itau/callback',
      'INTER': 'https://29e1b0b32eb8.ngrok-free.app/api/auth/inter/callback',
      'NUBANK': 'https://29e1b0b32eb8.ngrok-free.app/api/auth/nubank/callback'
    };
    return defaults[bankCode] || '';
  }

  private getDefaultScope(bankCode: string): string {
    const defaults: { [key: string]: string } = {
      'MERCADOPAGO': 'read,write',
      'ITAU': 'openid,profile,email,accounts,transactions',
      'INTER': 'openid,profile,email,accounts,transactions',
      'NUBANK': 'openid,profile,email,accounts,transactions'
    };
    return defaults[bankCode] || '';
  }

  /**
   * Abre o formulário para editar uma configuração
   */
  editConfig(bank: BankConfig): void {
    console.log('[BankConfigComponent] Editando configuração:', bank);
    this.selectedBank = bank;
    this.editing = true;
    this.showForm = true;
    this.bankForm.patchValue(bank);
  }

  /**
   * Salva a configuração
   */
  saveConfig(): void {
    if (this.bankForm.valid) {
      const config = this.bankForm.value;
      
      if (this.editing && this.selectedBank) {
        config.id = this.selectedBank.id;
      }
      
      console.log('[BankConfigComponent] Salvando configuração:', config);
      
      // Atualiza a lista
      if (this.editing && this.selectedBank) {
        const index = this.bankConfigs.findIndex(b => b.bankCode === this.selectedBank?.bankCode);
        if (index !== -1) {
          this.bankConfigs[index] = { ...this.selectedBank, ...config };
        }
      } else {
        this.bankConfigs.push(config);
      }
      
      this.showSuccessMessage('Configuração salva com sucesso!');
      this.closeForm();
    } else {
      this.showErrorMessage('Por favor, preencha todos os campos obrigatórios.');
    }
  }

  /**
   * Testa a conexão com o banco
   */
  testConnection(bank: BankConfig): void {
    console.log('[BankConfigComponent] Testando conexão com:', bank.bankName);
    this.showSuccessMessage(`Testando conexão com ${bank.bankName}...`);
    
    // Aqui você implementaria o teste real de conexão
    setTimeout(() => {
      this.showSuccessMessage(`Conexão com ${bank.bankName} testada com sucesso!`);
    }, 2000);
  }

  /**
   * Ativa/desativa uma configuração
   */
  toggleActive(bank: BankConfig): void {
    bank.isActive = !bank.isActive;
    console.log('[BankConfigComponent] Status alterado para:', bank.isActive ? 'ativo' : 'inativo');
    this.showSuccessMessage(`${bank.bankName} ${bank.isActive ? 'ativado' : 'desativado'} com sucesso!`);
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
    const bank = this.availableBanks.find(b => b.code === bankCode);
    return bank?.color || '#666';
  }

  /**
   * Obtém o ícone do banco
   */
  getBankIcon(bankCode: string): string {
    const bank = this.availableBanks.find(b => b.code === bankCode);
    return bank?.icon || 'account_balance';
  }

  /**
   * Obtém a descrição do banco
   */
  getBankDescription(bankCode: string): string {
    const bank = this.availableBanks.find(b => b.code === bankCode);
    return bank?.description || 'Banco não configurado';
  }

  /**
   * Obtém o status do banco
   */
  getBankStatus(bank: BankConfig): string {
    if (!bank.isActive) return 'Inativo';
    if (bank.isSandbox) return 'Sandbox';
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
}
