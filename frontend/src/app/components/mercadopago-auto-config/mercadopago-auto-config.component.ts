import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBarModule, MatSnackBar } from '@angular/material/snack-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { BankApiService } from '../../services/bank-api.service';

@Component({
  selector: 'app-mercadopago-auto-config',
  standalone: true,
  imports: [
    CommonModule,
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatSnackBarModule,
    MatProgressSpinnerModule
  ],
  template: `
    <div class="auto-config-container">
      <mat-card class="config-card">
        <mat-card-header>
          <mat-card-title>
            <mat-icon>auto_fix_high</mat-icon>
            Configuração Automática do Mercado Pago
          </mat-card-title>
          <mat-card-subtitle>
            Configure automaticamente usando as credenciais do banco de dados
          </mat-card-subtitle>
        </mat-card-header>

        <mat-card-content>
          <div class="config-info">
            <div class="info-item">
              <mat-icon>info</mat-icon>
              <div>
                <h4>Credenciais Já Configuradas</h4>
                <p>As credenciais do Mercado Pago já estão no banco de dados:</p>
                <ul>
                  <li><strong>Client ID:</strong> 4223603750190943</li>
                  <li><strong>Client Secret:</strong> D3pZ1tvPtRXlo8m6QGXVmekh9jZsaxwP</li>
                  <li><strong>User ID:</strong> 209112973</li>
                </ul>
              </div>
            </div>

            <div class="info-item">
              <mat-icon>check_circle</mat-icon>
              <div>
                <h4>Vantagens da Configuração Automática</h4>
                <ul>
                  <li>✅ Não precisa preencher formulários</li>
                  <li>✅ Credenciais centralizadas no banco</li>
                  <li>✅ Configuração instantânea</li>
                  <li>✅ Menos chance de erro</li>
                </ul>
              </div>
            </div>
          </div>

          <div class="config-actions">
            <button mat-raised-button 
                    color="primary" 
                    (click)="configurarAutomaticamente()"
                    [disabled]="loading">
              <mat-icon *ngIf="!loading">auto_fix_high</mat-icon>
              <mat-spinner *ngIf="loading" diameter="20"></mat-spinner>
              {{ loading ? 'Configurando...' : 'Configurar Automaticamente' }}
            </button>

            <button mat-button 
                    (click)="verificarStatus()"
                    [disabled]="loading">
              <mat-icon>info</mat-icon>
              Verificar Status
            </button>
          </div>

          <div class="status-card" *ngIf="status">
            <h4>Status da Configuração</h4>
            <div class="status-item">
              <mat-icon [color]="status.temConfiguracao ? 'primary' : 'warn'">
                {{ status.temConfiguracao ? 'check_circle' : 'error' }}
              </mat-icon>
              <span>{{ status.message }}</span>
            </div>
            
            <div class="status-item" *ngIf="status.temConfiguracao">
              <mat-icon>vpn_key</mat-icon>
              <span>Client ID: {{ status.clientId }}</span>
            </div>
            
            <div class="status-item" *ngIf="status.temConfiguracao">
              <mat-icon>person</mat-icon>
              <span>User ID: {{ status.userId }}</span>
            </div>
          </div>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [`
    .auto-config-container {
      max-width: 800px;
      margin: 0 auto;
      padding: 20px;
    }

    .config-card {
      margin-bottom: 20px;
    }

    .config-info {
      margin: 20px 0;
    }

    .info-item {
      display: flex;
      align-items: flex-start;
      gap: 16px;
      margin: 16px 0;
      padding: 16px;
      border: 1px solid #e0e0e0;
      border-radius: 8px;
      background-color: #f9f9f9;
    }

    .info-item mat-icon {
      font-size: 32px;
      color: #1976d2;
      margin-top: 4px;
    }

    .info-item h4 {
      margin: 0 0 8px 0;
      color: #333;
    }

    .info-item p {
      margin: 0 0 8px 0;
      color: #666;
    }

    .info-item ul {
      margin: 0;
      padding-left: 20px;
    }

    .info-item li {
      margin: 4px 0;
      color: #666;
    }

    .config-actions {
      display: flex;
      gap: 16px;
      margin: 24px 0;
    }

    .status-card {
      margin: 20px 0;
      padding: 16px;
      border: 1px solid #e0e0e0;
      border-radius: 8px;
      background-color: #f5f5f5;
    }

    .status-card h4 {
      margin: 0 0 16px 0;
      color: #333;
    }

    .status-item {
      display: flex;
      align-items: center;
      gap: 12px;
      margin: 8px 0;
    }

    .status-item mat-icon {
      font-size: 20px;
    }
  `]
})
export class MercadoPagoAutoConfigComponent implements OnInit {
  loading = false;
  status: any = null;

  constructor(
    private bankApiService: BankApiService,
    private snackBar: MatSnackBar
  ) { }

  ngOnInit(): void {
    this.verificarStatus();
  }

  configurarAutomaticamente() {
    this.loading = true;
    
    this.bankApiService.configurarMercadoPagoAutomatico().subscribe({
      next: (response) => {
        this.loading = false;
        if (response.success) {
          this.snackBar.open('✅ Configuração automática concluída!', 'Fechar', {
            duration: 5000,
            panelClass: ['success-snackbar']
          });
          this.verificarStatus();
        } else {
          this.snackBar.open(`❌ Erro: ${response.message}`, 'Fechar', {
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

  verificarStatus() {
    this.bankApiService.verificarStatusMercadoPago().subscribe({
      next: (response) => {
        this.status = response;
      },
      error: (error) => {
        this.snackBar.open(`❌ Erro ao verificar status: ${error.message}`, 'Fechar', {
          duration: 3000,
          panelClass: ['error-snackbar']
        });
      }
    });
  }
}
