import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBarModule, MatSnackBar } from '@angular/material/snack-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { BankApiService } from '../../services/bank-api.service';

@Component({
  selector: 'app-mercadopago-config',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatIconModule,
    MatSnackBarModule,
    MatProgressSpinnerModule
  ],
  template: `
    <div class="config-container">
      <mat-card class="config-card">
        <mat-card-header>
          <mat-card-title>
            <mat-icon>account_balance</mat-icon>
            Configurar Mercado Pago
          </mat-card-title>
          <mat-card-subtitle>
            Configure suas credenciais reais do Mercado Pago
          </mat-card-subtitle>
        </mat-card-header>

        <mat-card-content>
          <form [formGroup]="configForm" (ngSubmit)="onSubmit()">
            <div class="form-row">
              <mat-form-field appearance="outline" class="full-width">
                <mat-label>Client ID</mat-label>
                <input matInput 
                       formControlName="clientId" 
                       placeholder="Ex: 4223603750190943"
                       type="text">
                <mat-icon matSuffix>vpn_key</mat-icon>
              </mat-form-field>
            </div>

            <div class="form-row">
              <mat-form-field appearance="outline" class="full-width">
                <mat-label>Client Secret</mat-label>
                <input matInput 
                       formControlName="clientSecret" 
                       placeholder="Ex: APP_USR_..."
                       type="password">
                <mat-icon matSuffix>lock</mat-icon>
              </mat-form-field>
            </div>

            <div class="form-row">
              <mat-form-field appearance="outline" class="full-width">
                <mat-label>Access Token (Opcional)</mat-label>
                <input matInput 
                       formControlName="accessToken" 
                       placeholder="Token de acesso OAuth2"
                       type="password">
                <mat-icon matSuffix>security</mat-icon>
              </mat-form-field>
            </div>

            <div class="form-row">
              <mat-form-field appearance="outline" class="full-width">
                <mat-label>Refresh Token (Opcional)</mat-label>
                <input matInput 
                       formControlName="refreshToken" 
                       placeholder="Token de renovação OAuth2"
                       type="password">
                <mat-icon matSuffix>refresh</mat-icon>
              </mat-form-field>
            </div>

            <div class="form-actions">
              <button mat-raised-button 
                      color="primary" 
                      type="submit"
                      [disabled]="!configForm.valid || loading">
                <mat-icon *ngIf="!loading">save</mat-icon>
                <mat-spinner *ngIf="loading" diameter="20"></mat-spinner>
                {{ loading ? 'Configurando...' : 'Configurar' }}
              </button>

              <button mat-button 
                      type="button"
                      (click)="checkStatus()"
                      [disabled]="loading">
                <mat-icon>info</mat-icon>
                Verificar Status
              </button>
            </div>
          </form>
        </mat-card-content>
      </mat-card>

      <!-- Status Card -->
      <mat-card class="status-card" *ngIf="status">
        <mat-card-header>
          <mat-card-title>
            <mat-icon>info</mat-icon>
            Status da Configuração
          </mat-card-title>
        </mat-card-header>

        <mat-card-content>
          <div class="status-item">
            <mat-icon [color]="status.hasApiConfig ? 'primary' : 'warn'">
              {{ status.hasApiConfig ? 'check_circle' : 'error' }}
            </mat-icon>
            <span>Configuração da API: {{ status.hasApiConfig ? 'Configurada' : 'Não configurada' }}</span>
          </div>

          <div class="status-item" *ngIf="status.hasApiConfig">
            <mat-icon [color]="status.hasClientSecret ? 'primary' : 'warn'">
              {{ status.hasClientSecret ? 'check_circle' : 'error' }}
            </mat-icon>
            <span>Client Secret: {{ status.hasClientSecret ? 'Configurado' : 'Não configurado' }}</span>
          </div>

          <div class="status-item">
            <mat-icon [color]="status.hasAuth ? 'primary' : 'warn'">
              {{ status.hasAuth ? 'check_circle' : 'error' }}
            </mat-icon>
            <span>Autorização: {{ status.hasAuth ? 'Ativa' : 'Não configurada' }}</span>
          </div>

          <div class="status-item" *ngIf="status.hasAuth">
            <mat-icon [color]="!status.isTokenExpired ? 'primary' : 'warn'">
              {{ !status.isTokenExpired ? 'check_circle' : 'error' }}
            </mat-icon>
            <span>Token: {{ status.isTokenExpired ? 'Expirado' : 'Válido' }}</span>
          </div>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [`
    .config-container {
      max-width: 800px;
      margin: 0 auto;
      padding: 20px;
    }

    .config-card, .status-card {
      margin-bottom: 20px;
    }

    .form-row {
      margin-bottom: 16px;
    }

    .full-width {
      width: 100%;
    }

    .form-actions {
      display: flex;
      gap: 16px;
      margin-top: 24px;
    }

    .status-item {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: 8px;
    }

    .status-item mat-icon {
      font-size: 20px;
      width: 20px;
      height: 20px;
    }
  `]
})
export class MercadoPagoConfigComponent implements OnInit {
  configForm: FormGroup;
  loading = false;
  status: any = null;

  constructor(
    private fb: FormBuilder,
    private bankApiService: BankApiService,
    private snackBar: MatSnackBar
  ) {
    this.configForm = this.fb.group({
      clientId: ['', [Validators.required]],
      clientSecret: ['', [Validators.required]],
      accessToken: [''],
      refreshToken: ['']
    });
  }

  ngOnInit() {
    this.checkStatus();
  }

  onSubmit() {
    if (this.configForm.valid) {
      this.loading = true;
      
      const credentials = {
        clientId: this.configForm.value.clientId,
        clientSecret: this.configForm.value.clientSecret,
        accessToken: this.configForm.value.accessToken || null,
        refreshToken: this.configForm.value.refreshToken || null
      };

      this.bankApiService.configureMercadoPagoCredentials(credentials).subscribe({
        next: (response) => {
          this.loading = false;
          if (response.success) {
            this.snackBar.open('✅ Credenciais configuradas com sucesso!', 'Fechar', {
              duration: 5000,
              panelClass: ['success-snackbar']
            });
            this.checkStatus();
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

  checkStatus() {
    this.bankApiService.getMercadoPagoConfigStatus().subscribe({
      next: (response) => {
        this.status = response;
        if (response.hasApiConfig) {
          this.configForm.patchValue({
            clientId: response.clientId || ''
          });
        }
      },
      error: (error) => {
        console.error('Erro ao verificar status:', error);
      }
    });
  }
}
