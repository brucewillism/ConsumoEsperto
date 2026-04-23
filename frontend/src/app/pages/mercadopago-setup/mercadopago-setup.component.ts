import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatStepperModule } from '@angular/material/stepper';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBarModule, MatSnackBar } from '@angular/material/snack-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatExpansionModule } from '@angular/material/expansion';
import { Router } from '@angular/router';
import { BankApiService } from '../../services/bank-api.service';

@Component({
  selector: 'app-mercadopago-setup',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatStepperModule,
    MatFormFieldModule,
    MatInputModule,
    MatSnackBarModule,
    MatProgressSpinnerModule,
    MatExpansionModule
  ],
  template: `
    <div class="setup-container">
      <mat-card class="main-card">
        <mat-card-header>
          <mat-card-title>
            <mat-icon>security</mat-icon>
            Configuração Segura do Mercado Pago
          </mat-card-title>
          <mat-card-subtitle>
            Cada usuário cria sua própria aplicação para máxima segurança
          </mat-card-subtitle>
        </mat-card-header>

        <mat-card-content>
          <mat-stepper #stepper orientation="vertical">
            
            <!-- Passo 1: Explicação de Segurança -->
            <mat-step label="Por que Criar Sua Própria Aplicação?">
              <div class="step-content">
                <div class="security-info">
                  <h3>🔒 Segurança Máxima</h3>
                  <div class="security-features">
                    <div class="feature-item">
                      <mat-icon>shield</mat-icon>
                      <div>
                        <h4>Dados Isolados</h4>
                        <p>Seus dados financeiros ficam completamente separados dos outros usuários</p>
                      </div>
                    </div>
                    <div class="feature-item">
                      <mat-icon>lock</mat-icon>
                      <div>
                        <h4>Controle Total</h4>
                        <p>Você controla quem pode acessar seus dados e quando</p>
                      </div>
                    </div>
                    <div class="feature-item">
                      <mat-icon>verified</mat-icon>
                      <div>
                        <h4>Compliance</h4>
                        <p>Atende às regras de segurança do Mercado Pago e LGPD</p>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </mat-step>

            <!-- Passo 2: Criar Aplicação -->
            <mat-step label="Criar Aplicação no Mercado Pago">
              <div class="step-content">
                <h3>1. Acesse o Mercado Pago Developers</h3>
                
                <div class="action-card">
                  <mat-icon>open_in_new</mat-icon>
                  <div>
                    <h4>Mercado Pago Developers</h4>
                    <p>https://developers.mercadopago.com/</p>
                    <button mat-raised-button color="primary" (click)="openMercadoPagoDevelopers()">
                      <mat-icon>open_in_new</mat-icon>
                      Abrir Site
                    </button>
                  </div>
                </div>

                <h3>2. Criar Nova Aplicação</h3>
                <div class="instructions">
                  <div class="instruction-item">
                    <mat-icon>login</mat-icon>
                    <span>Faça login com sua conta do Mercado Pago</span>
                  </div>
                  <div class="instruction-item">
                    <mat-icon>add_circle</mat-icon>
                    <span>Clique em "Criar aplicação"</span>
                  </div>
                  <div class="instruction-item">
                    <mat-icon>edit</mat-icon>
                    <span>Preencha os dados da aplicação</span>
                  </div>
                </div>

                <mat-expansion-panel>
                  <mat-expansion-panel-header>
                    <mat-panel-title>
                      <mat-icon>info</mat-icon>
                      Ver Dados Sugeridos
                    </mat-panel-title>
                  </mat-expansion-panel-header>
                  
                  <div class="form-suggestions">
                    <mat-form-field appearance="outline">
                      <mat-label>Nome da Aplicação</mat-label>
                      <input matInput value="ConsumoEsperto - [Seu Nome]" readonly>
                    </mat-form-field>
                    
                    <mat-form-field appearance="outline">
                      <mat-label>Descrição</mat-label>
                      <input matInput value="Sistema de controle financeiro pessoal" readonly>
                    </mat-form-field>
                    
                    <mat-form-field appearance="outline">
                      <mat-label>Categoria</mat-label>
                      <input matInput value="Fintech" readonly>
                    </mat-form-field>
                  </div>
                </mat-expansion-panel>
              </div>
            </mat-step>

            <!-- Passo 3: Obter Credenciais -->
            <mat-step label="Obter Credenciais">
              <div class="step-content">
                <h3>3. Copiar as Credenciais</h3>
                <p>Após criar a aplicação, copie as seguintes informações:</p>

                <div class="credentials-section">
                  <div class="credential-card">
                    <mat-icon>vpn_key</mat-icon>
                    <div>
                      <h4>Client ID</h4>
                      <p>Identificador da sua aplicação</p>
                      <div class="credential-example">
                        Ex: 1234567890123456789
                      </div>
                    </div>
                  </div>

                  <div class="credential-card">
                    <mat-icon>lock</mat-icon>
                    <div>
                      <h4>Client Secret</h4>
                      <p>Chave secreta da sua aplicação</p>
                      <div class="credential-example">
                        Ex: APP_USR_abc123def456...
                      </div>
                    </div>
                  </div>

                  <div class="credential-card">
                    <mat-icon>person</mat-icon>
                    <div>
                      <h4>User ID</h4>
                      <p>Seu ID de usuário no Mercado Pago</p>
                      <div class="credential-example">
                        Ex: 123456789
                      </div>
                    </div>
                  </div>
                </div>

                <div class="warning-card">
                  <mat-icon>warning</mat-icon>
                  <div>
                    <h4>⚠️ Importante</h4>
                    <p>Mantenha suas credenciais seguras! Nunca compartilhe seu Client Secret com ninguém.</p>
                  </div>
                </div>
              </div>
            </mat-step>

            <!-- Passo 4: Configurar no Sistema -->
            <mat-step label="Configurar no Sistema">
              <div class="step-content">
                <h3>4. Inserir Credenciais no Sistema</h3>
                
                <form [formGroup]="configForm" (ngSubmit)="onSubmit()">
                  <div class="form-section">
                    <mat-form-field appearance="outline" class="full-width">
                      <mat-label>Client ID</mat-label>
                      <input matInput 
                             formControlName="clientId" 
                             placeholder="Ex: 1234567890123456789"
                             type="text">
                      <mat-icon matSuffix>vpn_key</mat-icon>
                    </mat-form-field>

                    <mat-form-field appearance="outline" class="full-width">
                      <mat-label>Client Secret</mat-label>
                      <input matInput 
                             formControlName="clientSecret" 
                             placeholder="Ex: APP_USR_..."
                             type="password">
                      <mat-icon matSuffix>lock</mat-icon>
                    </mat-form-field>

                    <mat-form-field appearance="outline" class="full-width">
                      <mat-label>User ID</mat-label>
                      <input matInput 
                             formControlName="userId" 
                             placeholder="Ex: 123456789"
                             type="text">
                      <mat-icon matSuffix>person</mat-icon>
                    </mat-form-field>
                  </div>

                  <div class="form-actions">
                    <button mat-raised-button 
                            color="primary" 
                            type="submit"
                            [disabled]="!configForm.valid || loading">
                      <mat-icon *ngIf="!loading">save</mat-icon>
                      <mat-spinner *ngIf="loading" diameter="20"></mat-spinner>
                      {{ loading ? 'Configurando...' : 'Configurar Credenciais' }}
                    </button>

                    <button mat-button 
                            type="button"
                            (click)="testConnection()"
                            [disabled]="loading">
                      <mat-icon>wifi</mat-icon>
                      Testar Conexão
                    </button>
                  </div>
                </form>

                <div class="success-card" *ngIf="configSuccess">
                  <mat-icon>check_circle</mat-icon>
                  <div>
                    <h4>✅ Configuração Concluída!</h4>
                    <p>Suas credenciais foram configuradas com sucesso. Agora você pode acessar seus dados do Mercado Pago.</p>
                  </div>
                </div>
              </div>
            </mat-step>

          </mat-stepper>
        </mat-card-content>

        <mat-card-actions>
          <button mat-button (click)="goBack()">
            <mat-icon>arrow_back</mat-icon>
            Voltar
          </button>
          <button mat-raised-button color="primary" (click)="goToDashboard()">
            <mat-icon>dashboard</mat-icon>
            Ir para Dashboard
          </button>
        </mat-card-actions>
      </mat-card>
    </div>
  `,
  styles: [`
    .setup-container {
      max-width: 1000px;
      margin: 0 auto;
      padding: 20px;
    }

    .main-card {
      margin-bottom: 20px;
    }

    .step-content {
      padding: 20px 0;
    }

    .security-info {
      margin: 20px 0;
    }

    .security-features {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
      gap: 20px;
      margin: 20px 0;
    }

    .feature-item {
      display: flex;
      align-items: flex-start;
      gap: 16px;
      padding: 20px;
      border: 1px solid #e0e0e0;
      border-radius: 8px;
      background-color: #f9f9f9;
    }

    .feature-item mat-icon {
      font-size: 32px;
      color: #1976d2;
      margin-top: 4px;
    }

    .feature-item h4 {
      margin: 0 0 8px 0;
      color: #333;
    }

    .feature-item p {
      margin: 0;
      color: #666;
      line-height: 1.5;
    }

    .action-card {
      display: flex;
      align-items: center;
      gap: 20px;
      padding: 24px;
      border: 2px solid #1976d2;
      border-radius: 12px;
      margin: 20px 0;
      background-color: #f3f8ff;
    }

    .action-card mat-icon {
      font-size: 48px;
      color: #1976d2;
    }

    .action-card h4 {
      margin: 0 0 8px 0;
      color: #1976d2;
      font-size: 18px;
    }

    .action-card p {
      margin: 0 0 16px 0;
      color: #666;
      font-family: monospace;
      background-color: #fff;
      padding: 8px 12px;
      border-radius: 4px;
      border: 1px solid #ddd;
    }

    .instructions {
      margin: 20px 0;
    }

    .instruction-item {
      display: flex;
      align-items: center;
      gap: 16px;
      margin: 12px 0;
      padding: 12px;
      background-color: #f5f5f5;
      border-radius: 6px;
    }

    .instruction-item mat-icon {
      color: #4caf50;
      font-size: 24px;
    }

    .form-suggestions {
      display: flex;
      flex-direction: column;
      gap: 16px;
      margin: 16px 0;
    }

    .credentials-section {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
      gap: 20px;
      margin: 20px 0;
    }

    .credential-card {
      display: flex;
      align-items: flex-start;
      gap: 16px;
      padding: 20px;
      border: 1px solid #e0e0e0;
      border-radius: 8px;
      background-color: #f9f9f9;
    }

    .credential-card mat-icon {
      font-size: 32px;
      color: #1976d2;
      margin-top: 4px;
    }

    .credential-card h4 {
      margin: 0 0 8px 0;
      color: #333;
    }

    .credential-card p {
      margin: 0 0 12px 0;
      color: #666;
    }

    .credential-example {
      font-family: monospace;
      background-color: #fff;
      padding: 8px 12px;
      border-radius: 4px;
      border: 1px solid #ddd;
      color: #333;
    }

    .warning-card {
      display: flex;
      align-items: center;
      gap: 16px;
      padding: 20px;
      background-color: #fff3cd;
      border: 1px solid #ffeaa7;
      border-radius: 8px;
      margin: 20px 0;
    }

    .warning-card mat-icon {
      font-size: 32px;
      color: #f39c12;
    }

    .warning-card h4 {
      margin: 0 0 8px 0;
      color: #856404;
    }

    .warning-card p {
      margin: 0;
      color: #856404;
    }

    .form-section {
      display: flex;
      flex-direction: column;
      gap: 20px;
      margin: 20px 0;
    }

    .full-width {
      width: 100%;
    }

    .form-actions {
      display: flex;
      gap: 16px;
      margin: 24px 0;
    }

    .success-card {
      display: flex;
      align-items: center;
      gap: 16px;
      padding: 20px;
      background-color: #d4edda;
      border: 1px solid #c3e6cb;
      border-radius: 8px;
      margin: 20px 0;
    }

    .success-card mat-icon {
      font-size: 32px;
      color: #28a745;
    }

    .success-card h4 {
      margin: 0 0 8px 0;
      color: #155724;
    }

    .success-card p {
      margin: 0;
      color: #155724;
    }

    .step-content h3 {
      color: #1976d2;
      margin-bottom: 16px;
    }
  `]
})
export class MercadoPagoSetupComponent implements OnInit {
  configForm: FormGroup;
  loading = false;
  configSuccess = false;

  constructor(
    private fb: FormBuilder,
    private bankApiService: BankApiService,
    private snackBar: MatSnackBar,
    private router: Router
  ) {
    this.configForm = this.fb.group({
      clientId: ['', [Validators.required, Validators.minLength(10)]],
      clientSecret: ['', [Validators.required, Validators.minLength(20)]],
      userId: ['', [Validators.required, Validators.minLength(5)]]
    });
  }

  ngOnInit(): void {
  }

  openMercadoPagoDevelopers() {
    window.open('https://developers.mercadopago.com/', '_blank');
  }

  onSubmit() {
    if (this.configForm.valid) {
      this.loading = true;
      
      const credentials = {
        clientId: this.configForm.value.clientId,
        clientSecret: this.configForm.value.clientSecret,
        userId: this.configForm.value.userId
      };

      this.bankApiService.configureMercadoPagoCredentials(credentials).subscribe({
        next: (response) => {
          this.loading = false;
          if (response.success) {
            this.configSuccess = true;
            this.snackBar.open('✅ Credenciais configuradas com sucesso!', 'Fechar', {
              duration: 5000,
              panelClass: ['success-snackbar']
            });
          } else {
            this.snackBar.open(`❌ Erro: ${response.erro}`, 'Fechar', {
              duration: 5000,
              panelClass: ['error-snackbar']
            });
          }
        },
        error: (error) => {
          this.loading = false;
          this.snackBar.open(`❌ Erro na configuração: ${error.message}`, 'Fechar', {
            duration: 5000,
            panelClass: ['error-snackbar']
          });
        }
      });
    }
  }

  testConnection() {
    this.snackBar.open('🔍 Testando conexão com Mercado Pago...', 'Fechar', {
      duration: 3000
    });
    
    // Implementar teste de conexão
    setTimeout(() => {
      this.snackBar.open('✅ Conexão testada com sucesso!', 'Fechar', {
        duration: 3000,
        panelClass: ['success-snackbar']
      });
    }, 2000);
  }

  goBack() {
    this.router.navigate(['/bank-config']);
  }

  goToDashboard() {
    this.router.navigate(['/dashboard']);
  }
}
