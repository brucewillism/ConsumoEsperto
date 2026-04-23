import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';

interface BankProvider {
  id: string;
  name: string;
  icon: string;
  color: string;
  fields: BankField[];
  instructions: string[];
  apiEndpoint: string;
  websiteUrl: string;
}

interface BankField {
  name: string;
  label: string;
  type: 'text' | 'password' | 'email';
  required: boolean;
  placeholder: string;
  helpText: string;
}

@Component({
  selector: 'app-bank-config',
  templateUrl: './bank-config.component.html',
  styleUrls: ['./bank-config.component.scss']
})
export class BankConfigComponent implements OnInit {

  configForm: FormGroup;
  isLoading = false;
  message = '';
  messageType: 'success' | 'error' = 'success';
  selectedProvider: BankProvider | null = null;
  
  // Status das integrações
  integrationStatus: { [key: string]: boolean } = {};

  // Configurações dos bancos
  bankProviders: BankProvider[] = [
    {
      id: 'mercadopago',
      name: 'Mercado Pago',
      icon: '💳',
      color: '#00b1ea',
      apiEndpoint: '/mercadopago',
      websiteUrl: 'https://www.mercadopago.com.br/developers',
      fields: [
        {
          name: 'accessToken',
          label: 'Access Token',
          type: 'password',
          required: true,
          placeholder: 'APP_USR-xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx',
          helpText: 'Token de acesso obtido no painel de desenvolvedores'
        },
        {
          name: 'userId',
          label: 'User ID (opcional)',
          type: 'text',
          required: false,
          placeholder: '123456789',
          helpText: 'ID do usuário no Mercado Pago'
        }
      ],
      instructions: [
        'Acesse o Mercado Pago Developers',
        'Faça login na sua conta',
        'Vá em "Minhas Aplicações"',
        'Selecione sua aplicação ou crie uma nova',
        'Copie o "Access Token" de produção',
        'Cole no campo acima e salve'
      ]
    },
    {
      id: 'nubank',
      name: 'Nubank',
      icon: '🟣',
      color: '#8a05be',
      apiEndpoint: '/nubank',
      websiteUrl: 'https://dev.nubank.com.br',
      fields: [
        {
          name: 'cpf',
          label: 'CPF',
          type: 'text',
          required: true,
          placeholder: '000.000.000-00',
          helpText: 'Seu CPF cadastrado no Nubank'
        },
        {
          name: 'password',
          label: 'Senha',
          type: 'password',
          required: true,
          placeholder: 'Sua senha do Nubank',
          helpText: 'Senha do seu login no Nubank'
        },
        {
          name: 'deviceId',
          label: 'Device ID (opcional)',
          type: 'text',
          required: false,
          placeholder: 'xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx',
          helpText: 'ID do dispositivo para autenticação'
        }
      ],
      instructions: [
        'Use suas credenciais normais do Nubank',
        'CPF e senha são os mesmos do app',
        'Certifique-se que sua conta está ativa',
        'Pode ser necessário confirmar por SMS',
        'Device ID é opcional para primeira conexão'
      ]
    },
    {
      id: 'itau',
      name: 'Itaú',
      icon: '🔶',
      color: '#ec7000',
      apiEndpoint: '/itau',
      websiteUrl: 'https://www.itau.com.br',
      fields: [
        {
          name: 'agencia',
          label: 'Agência',
          type: 'text',
          required: true,
          placeholder: '0000',
          helpText: 'Número da sua agência (4 dígitos)'
        },
        {
          name: 'conta',
          label: 'Conta',
          type: 'text',
          required: true,
          placeholder: '00000-0',
          helpText: 'Número da conta com dígito verificador'
        },
        {
          name: 'senha',
          label: 'Senha Eletrônica',
          type: 'password',
          required: true,
          placeholder: 'Sua senha de 6 dígitos',
          helpText: 'Senha eletrônica do internet banking'
        }
      ],
      instructions: [
        'Use os dados do seu Internet Banking',
        'Agência: 4 dígitos (sem hífen)',
        'Conta: com dígito verificador',
        'Senha: mesma do internet banking',
        'Certifique-se que o acesso online está ativo'
      ]
    },
    {
      id: 'caixa',
      name: 'Caixa Econômica',
      icon: '🏦',
      color: '#0066cc',
      apiEndpoint: '/caixa',
      websiteUrl: 'https://www.caixa.gov.br',
      fields: [
        {
          name: 'cpf',
          label: 'CPF',
          type: 'text',
          required: true,
          placeholder: '000.000.000-00',
          helpText: 'CPF cadastrado na Caixa'
        },
        {
          name: 'senha',
          label: 'Senha Internet Banking',
          type: 'password',
          required: true,
          placeholder: 'Sua senha de acesso',
          helpText: 'Senha do internet banking da Caixa'
        },
        {
          name: 'agencia',
          label: 'Agência (opcional)',
          type: 'text',
          required: false,
          placeholder: '0000',
          helpText: 'Código da agência (4 dígitos)'
        }
      ],
      instructions: [
        'Use as credenciais do Internet Banking',
        'CPF deve estar cadastrado na Caixa',
        'Senha é a mesma do acesso online',
        'Agência é opcional mas recomendada',
        'Certifique-se que não há bloqueios na conta'
      ]
    },
    {
      id: 'inter',
      name: 'Banco Inter',
      icon: '🧡',
      color: '#ff7a00',
      apiEndpoint: '/inter',
      websiteUrl: 'https://www.bancointer.com.br',
      fields: [
        {
          name: 'cpf',
          label: 'CPF',
          type: 'text',
          required: true,
          placeholder: '000.000.000-00',
          helpText: 'CPF cadastrado no Inter'
        },
        {
          name: 'senha',
          label: 'Senha do App',
          type: 'password',
          required: true,
          placeholder: 'Senha do aplicativo',
          helpText: 'Mesma senha usada no app do Inter'
        },
        {
          name: 'deviceId',
          label: 'Device ID',
          type: 'text',
          required: false,
          placeholder: 'ID do dispositivo',
          helpText: 'Identificador único do dispositivo'
        }
      ],
      instructions: [
        'Use as credenciais do aplicativo Inter',
        'CPF e senha são os mesmos do app móvel',
        'Device ID pode ser gerado automaticamente',
        'Conta deve estar ativa e desbloqueada',
        'Pode solicitar confirmação via SMS'
      ]
    }
  ];

  constructor(
    private fb: FormBuilder,
    private http: HttpClient
  ) {
    // Inicializa com Mercado Pago como padrão
    this.selectedProvider = this.bankProviders[0];
    this.configForm = this.createFormForProvider(this.selectedProvider);
  }

  ngOnInit(): void {
    // Carrega configurações existentes se houver
    this.loadExistingConfig();
  }

  private createFormForProvider(provider: BankProvider): FormGroup {
    const formControls: any = {};
    
    provider.fields.forEach(field => {
      const validators = field.required ? [Validators.required] : [];
      if (field.name === 'accessToken') {
        validators.push(Validators.minLength(10));
      }
      formControls[field.name] = ['', validators];
    });

    return this.fb.group(formControls);
  }

  onProviderChange(providerId: string): void {
    const provider = this.bankProviders.find(p => p.id === providerId);
    if (provider) {
      this.selectedProvider = provider;
      this.configForm = this.createFormForProvider(provider);
      this.clearMessage();
    }
  }

  private loadExistingConfig(): void {
    console.log('Carregando configurações bancárias existentes...');
    
    // Verifica status do Mercado Pago
    this.http.get(`${environment.apiUrl}/mercadopago/status`)
      .subscribe({
        next: (response: any) => {
          console.log('Status Mercado Pago:', response);
          if (response.configurado) {
            this.updateProviderStatus('mercadopago', true);
          }
        },
        error: (error) => {
          console.log('Mercado Pago não configurado ou erro:', error);
        }
      });
  }

  private updateProviderStatus(providerId: string, isActive: boolean): void {
    this.integrationStatus[providerId] = isActive;
    const provider = this.bankProviders.find(p => p.id === providerId);
    if (provider) {
      console.log(`Provedor ${provider.name} está ${isActive ? 'ativo' : 'inativo'}`);
    }
  }

  onSubmit(): void {
    if (this.configForm.valid && this.selectedProvider) {
      this.isLoading = true;
      this.message = '';

      // Coleta todos os dados do formulário
      const configData: any = {
        provider: this.selectedProvider.id
      };

      this.selectedProvider.fields.forEach(field => {
        const value = this.configForm.get(field.name)?.value;
        if (value) {
          configData[field.name] = value;
        }
      });

      // Envia para o endpoint específico do banco
      this.http.post(`${environment.apiUrl}${this.selectedProvider.apiEndpoint}/config`, configData)
        .subscribe({
          next: (response: any) => {
            this.message = `Credenciais do ${this.selectedProvider!.name} configuradas com sucesso!`;
            this.messageType = 'success';
            this.isLoading = false;
            
            // Recarrega configurações existentes
            this.loadExistingConfig();
            
            // Testa a conexão automaticamente
            this.testConnection();
          },
          error: (error) => {
            this.message = `Erro ao configurar ${this.selectedProvider!.name}: ${error.error || error.message || 'Erro desconhecido'}`;
            this.messageType = 'error';
            this.isLoading = false;
          }
        });
    }
  }

  testConnection(): void {
    if (!this.selectedProvider) return;
    
    this.isLoading = true;
    
    this.http.get(`${environment.apiUrl}${this.selectedProvider.apiEndpoint}/teste-conexao`)
      .subscribe({
        next: (response: any) => {
          this.message += ` Conexão com ${this.selectedProvider!.name} testada com sucesso!`;
          this.messageType = 'success';
          this.isLoading = false;
        },
        error: (error) => {
          this.message += ` Falha no teste de conexão com ${this.selectedProvider!.name}.`;
          this.messageType = 'error';
          this.isLoading = false;
        }
      });
  }

  openBankWebsite(): void {
    if (this.selectedProvider?.websiteUrl) {
      window.open(this.selectedProvider.websiteUrl, '_blank');
    }
  }

  clearMessage(): void {
    this.message = '';
  }

  // Getters para validação de campos dinâmicos
  getFieldControl(fieldName: string) {
    return this.configForm.get(fieldName);
  }

  isFieldInvalid(fieldName: string): boolean {
    const control = this.getFieldControl(fieldName);
    return !!(control?.invalid && control?.touched);
  }

  getFieldErrors(fieldName: string): string[] {
    const control = this.getFieldControl(fieldName);
    const errors: string[] = [];
    
    if (control?.errors) {
      if (control.errors['required']) {
        const field = this.selectedProvider?.fields.find(f => f.name === fieldName);
        errors.push(`${field?.label || fieldName} é obrigatório`);
      }
      if (control.errors['minlength']) {
        errors.push(`Deve ter pelo menos ${control.errors['minlength'].requiredLength} caracteres`);
      }
    }
    
    return errors;
  }
}
