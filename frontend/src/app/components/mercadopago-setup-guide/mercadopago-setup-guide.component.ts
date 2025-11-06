import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatStepperModule } from '@angular/material/stepper';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBarModule, MatSnackBar } from '@angular/material/snack-bar';

@Component({
  selector: 'app-mercadopago-setup-guide',
  standalone: true,
  imports: [
    CommonModule,
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatStepperModule,
    MatFormFieldModule,
    MatInputModule,
    MatSnackBarModule
  ],
  template: `
    <div class="setup-guide-container">
      <mat-card class="guide-card">
        <mat-card-header>
          <mat-card-title>
            <mat-icon>account_balance</mat-icon>
            Como Configurar o Mercado Pago
          </mat-card-title>
          <mat-card-subtitle>
            Siga este guia passo a passo para conectar sua conta do Mercado Pago
          </mat-card-subtitle>
        </mat-card-header>

        <mat-card-content>
          <mat-stepper #stepper>
            
            <!-- Passo 1: Criar Aplicação -->
            <mat-step label="Criar Aplicação no Mercado Pago">
              <div class="step-content">
                <h3>1. Acesse o Mercado Pago Developers</h3>
                <p>Vá para o painel de desenvolvedor do Mercado Pago:</p>
                
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
                <div class="instruction-list">
                  <div class="instruction-item">
                    <mat-icon>check_circle</mat-icon>
                    <span>Faça login com sua conta do Mercado Pago</span>
                  </div>
                  <div class="instruction-item">
                    <mat-icon>check_circle</mat-icon>
                    <span>Clique em "Criar aplicação"</span>
                  </div>
                  <div class="instruction-item">
                    <mat-icon>check_circle</mat-icon>
                    <span>Preencha os dados:</span>
                  </div>
                </div>

                <div class="form-example">
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

                <div class="instruction-item">
                  <mat-icon>check_circle</mat-icon>
                  <span>Clique em "Criar"</span>
                </div>
              </div>
            </mat-step>

            <!-- Passo 2: Obter Credenciais -->
            <mat-step label="Obter Credenciais">
              <div class="step-content">
                <h3>3. Copiar as Credenciais</h3>
                <p>Após criar a aplicação, você verá:</p>

                <div class="credentials-card">
                  <h4>🔑 Credenciais da Aplicação</h4>
                  
                  <div class="credential-item">
                    <mat-icon>vpn_key</mat-icon>
                    <div>
                      <strong>Client ID</strong>
                      <p>Ex: 1234567890123456789</p>
                      <button mat-button (click)="copyToClipboard('client-id')">
                        <mat-icon>content_copy</mat-icon>
                        Copiar
                      </button>
                    </div>
                  </div>

                  <div class="credential-item">
                    <mat-icon>lock</mat-icon>
                    <div>
                      <strong>Client Secret</strong>
                      <p>Ex: APP_USR_abc123def456...</p>
                      <button mat-button (click)="copyToClipboard('client-secret')">
                        <mat-icon>content_copy</mat-icon>
                        Copiar
                      </button>
                    </div>
                  </div>

                  <div class="credential-item">
                    <mat-icon>person</mat-icon>
                    <div>
                      <strong>User ID</strong>
                      <p>Ex: 123456789</p>
                      <button mat-button (click)="copyToClipboard('user-id')">
                        <mat-icon>content_copy</mat-icon>
                        Copiar
                      </button>
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

            <!-- Passo 3: Configurar no Sistema -->
            <mat-step label="Configurar no Sistema">
              <div class="step-content">
                <h3>4. Inserir Credenciais no Sistema</h3>
                <p>Volte para o sistema e configure as credenciais:</p>

                <div class="instruction-list">
                  <div class="instruction-item">
                    <mat-icon>check_circle</mat-icon>
                    <span>Vá para "APIs Bancárias" no menu</span>
                  </div>
                  <div class="instruction-item">
                    <mat-icon>check_circle</mat-icon>
                    <span>Escolha "Mercado Pago"</span>
                  </div>
                  <div class="instruction-item">
                    <mat-icon>check_circle</mat-icon>
                    <span>Clique em "+ Configurar"</span>
                  </div>
                  <div class="instruction-item">
                    <mat-icon>check_circle</mat-icon>
                    <span>Cole as credenciais copiadas</span>
                  </div>
                  <div class="instruction-item">
                    <mat-icon>check_circle</mat-icon>
                    <span>Clique em "Configurar"</span>
                  </div>
                </div>

                <div class="success-card">
                  <mat-icon>check_circle</mat-icon>
                  <div>
                    <h4>✅ Pronto!</h4>
                    <p>Suas credenciais foram configuradas com sucesso!</p>
                  </div>
                </div>
              </div>
            </mat-step>

          </mat-stepper>
        </mat-card-content>

        <mat-card-actions>
          <button mat-raised-button color="primary" (click)="stepper.next()">
            <mat-icon>arrow_forward</mat-icon>
            Próximo Passo
          </button>
          <button mat-button (click)="stepper.previous()">
            <mat-icon>arrow_back</mat-icon>
            Voltar
          </button>
          <button mat-button (click)="goToConfig()">
            <mat-icon>settings</mat-icon>
            Ir para Configuração
          </button>
        </mat-card-actions>
      </mat-card>
    </div>
  `,
  styles: [`
    .setup-guide-container {
      max-width: 900px;
      margin: 0 auto;
      padding: 20px;
    }

    .guide-card {
      margin-bottom: 20px;
    }

    .step-content {
      padding: 20px 0;
    }

    .action-card {
      display: flex;
      align-items: center;
      gap: 16px;
      padding: 16px;
      border: 1px solid #e0e0e0;
      border-radius: 8px;
      margin: 16px 0;
    }

    .action-card mat-icon {
      font-size: 32px;
      color: #1976d2;
    }

    .instruction-list {
      margin: 16px 0;
    }

    .instruction-item {
      display: flex;
      align-items: center;
      gap: 12px;
      margin: 8px 0;
    }

    .instruction-item mat-icon {
      color: #4caf50;
    }

    .form-example {
      display: flex;
      flex-direction: column;
      gap: 16px;
      margin: 16px 0;
      padding: 16px;
      background-color: #f5f5f5;
      border-radius: 8px;
    }

    .credentials-card {
      border: 1px solid #e0e0e0;
      border-radius: 8px;
      padding: 16px;
      margin: 16px 0;
    }

    .credential-item {
      display: flex;
      align-items: center;
      gap: 16px;
      margin: 12px 0;
      padding: 12px;
      background-color: #f9f9f9;
      border-radius: 4px;
    }

    .credential-item mat-icon {
      color: #1976d2;
    }

    .warning-card {
      display: flex;
      align-items: center;
      gap: 16px;
      padding: 16px;
      background-color: #fff3cd;
      border: 1px solid #ffeaa7;
      border-radius: 8px;
      margin: 16px 0;
    }

    .warning-card mat-icon {
      color: #f39c12;
    }

    .success-card {
      display: flex;
      align-items: center;
      gap: 16px;
      padding: 16px;
      background-color: #d4edda;
      border: 1px solid #c3e6cb;
      border-radius: 8px;
      margin: 16px 0;
    }

    .success-card mat-icon {
      color: #28a745;
    }

    .step-content h3 {
      color: #1976d2;
      margin-bottom: 8px;
    }

    .step-content h4 {
      color: #333;
      margin-bottom: 8px;
    }
  `]
})
export class MercadoPagoSetupGuideComponent implements OnInit {

  constructor(private snackBar: MatSnackBar) { }

  ngOnInit(): void {
  }

  openMercadoPagoDevelopers() {
    window.open('https://developers.mercadopago.com/', '_blank');
  }

  copyToClipboard(type: string) {
    let text = '';
    
    switch (type) {
      case 'client-id':
        text = '1234567890123456789';
        break;
      case 'client-secret':
        text = 'APP_USR_abc123def456ghi789jkl012mno345pqr678stu901vwx234yz';
        break;
      case 'user-id':
        text = '123456789';
        break;
    }

    navigator.clipboard.writeText(text).then(() => {
      this.snackBar.open('✅ Copiado para a área de transferência!', 'Fechar', {
        duration: 3000,
        panelClass: ['success-snackbar']
      });
    });
  }

  goToConfig() {
    // Navegar para a página de configuração
    window.location.href = '/bank-config';
  }
}
