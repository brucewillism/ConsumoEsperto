import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBarModule, MatSnackBar } from '@angular/material/snack-bar';
import { MatExpansionModule } from '@angular/material/expansion';
import { BankApiService } from '../../services/bank-api.service';

@Component({
  selector: 'app-test-mercado-pago',
  standalone: true,
  imports: [
    CommonModule,
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    MatExpansionModule
  ],
  template: `
    <div class="test-container">
      <mat-card class="main-card">
        <mat-card-header>
          <mat-card-title>
            <mat-icon>science</mat-icon>
            Teste da API do Mercado Pago
          </mat-card-title>
          <mat-card-subtitle>
            Teste completo da integração com dados reais da API
          </mat-card-subtitle>
        </mat-card-header>

        <mat-card-content>
          <div class="test-section">
            <h3>🔍 Teste Completo da API</h3>
            <p>Este teste irá:</p>
            <ul>
              <li>Validar configurações do Mercado Pago</li>
              <li>Testar conectividade com a API</li>
              <li>Buscar dados reais de cartões, pagamentos e faturas</li>
              <li>Exibir todos os dados retornados pela API</li>
            </ul>

            <div class="test-actions">
              <button mat-raised-button 
                      color="primary" 
                      (click)="executarTeste()"
                      [disabled]="loading">
                <mat-icon *ngIf="!loading">play_arrow</mat-icon>
                <mat-spinner *ngIf="loading" diameter="20"></mat-spinner>
                {{ loading ? 'Executando Teste...' : 'Executar Teste Completo' }}
              </button>

              <button mat-button 
                      (click)="limparResultados()"
                      [disabled]="loading">
                <mat-icon>clear</mat-icon>
                Limpar Resultados
              </button>
            </div>
          </div>

          <!-- Resultados do Teste -->
          <div class="results-section" *ngIf="resultados">
            <h3>📊 Resultados do Teste</h3>
            
            <div class="status-card" [ngClass]="resultados.status === 'success' ? 'success' : 'error'">
              <mat-icon>{{ resultados.status === 'success' ? 'check_circle' : 'error' }}</mat-icon>
              <div>
                <h4>{{ resultados.status === 'success' ? 'Teste Concluído' : 'Erro no Teste' }}</h4>
                <p>{{ resultados.message || 'Verifique os logs para mais detalhes' }}</p>
              </div>
            </div>

            <!-- Detalhes dos Resultados -->
            <mat-expansion-panel *ngIf="resultados.detalhes">
              <mat-expansion-panel-header>
                <mat-panel-title>
                  <mat-icon>info</mat-icon>
                  Detalhes da Resposta da API
                </mat-panel-title>
              </mat-expansion-panel-header>
              
              <div class="api-details">
                <pre>{{ formatJson(resultados.detalhes) }}</pre>
              </div>
            </mat-expansion-panel>

            <!-- Logs do Console -->
            <mat-expansion-panel *ngIf="logs.length > 0">
              <mat-expansion-panel-header>
                <mat-panel-title>
                  <mat-icon>terminal</mat-icon>
                  Logs do Console ({{ logs.length }} entradas)
                </mat-panel-title>
              </mat-expansion-panel-header>
              
              <div class="logs-container">
                <div class="log-entry" 
                     *ngFor="let log of logs" 
                     [ngClass]="log.type">
                  <span class="log-time">{{ log.timestamp }}</span>
                  <span class="log-message">{{ log.message }}</span>
                </div>
              </div>
            </mat-expansion-panel>
          </div>

          <!-- Instruções -->
          <div class="instructions-section">
            <h3>📋 Como Interpretar os Resultados</h3>
            
            <div class="instruction-grid">
              <div class="instruction-item">
                <mat-icon>check_circle</mat-icon>
                <div>
                  <h4>✅ Sucesso</h4>
                  <p>API respondeu corretamente e dados foram encontrados</p>
                </div>
              </div>
              
              <div class="instruction-item">
                <mat-icon>warning</mat-icon>
                <div>
                  <h4>⚠️ Aviso</h4>
                  <p>API respondeu mas sem dados (normal se não houver transações)</p>
                </div>
              </div>
              
              <div class="instruction-item">
                <mat-icon>error</mat-icon>
                <div>
                  <h4>❌ Erro</h4>
                  <p>Problema de conectividade, credenciais ou configuração</p>
                </div>
              </div>
            </div>
          </div>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [`
    .test-container {
      max-width: 1200px;
      margin: 0 auto;
      padding: 20px;
    }

    .main-card {
      margin-bottom: 20px;
    }

    .test-section {
      margin: 20px 0;
    }

    .test-section h3 {
      color: #1976d2;
      margin-bottom: 16px;
    }

    .test-section ul {
      margin: 16px 0;
      padding-left: 20px;
    }

    .test-section li {
      margin: 8px 0;
      color: #666;
    }

    .test-actions {
      display: flex;
      gap: 16px;
      margin: 24px 0;
    }

    .results-section {
      margin: 32px 0;
      padding: 20px;
      background-color: #f9f9f9;
      border-radius: 8px;
    }

    .results-section h3 {
      color: #1976d2;
      margin-bottom: 16px;
    }

    .status-card {
      display: flex;
      align-items: center;
      gap: 16px;
      padding: 20px;
      border-radius: 8px;
      margin: 16px 0;
    }

    .status-card.success {
      background-color: #d4edda;
      border: 1px solid #c3e6cb;
    }

    .status-card.error {
      background-color: #f8d7da;
      border: 1px solid #f5c6cb;
    }

    .status-card mat-icon {
      font-size: 32px;
    }

    .status-card.success mat-icon {
      color: #28a745;
    }

    .status-card.error mat-icon {
      color: #dc3545;
    }

    .status-card h4 {
      margin: 0 0 8px 0;
    }

    .status-card p {
      margin: 0;
    }

    .api-details {
      background-color: #f8f9fa;
      border: 1px solid #e9ecef;
      border-radius: 4px;
      padding: 16px;
      margin: 16px 0;
      overflow-x: auto;
    }

    .api-details pre {
      margin: 0;
      font-family: 'Courier New', monospace;
      font-size: 12px;
      line-height: 1.4;
      white-space: pre-wrap;
      word-wrap: break-word;
    }

    .logs-container {
      max-height: 400px;
      overflow-y: auto;
      background-color: #1e1e1e;
      color: #d4d4d4;
      padding: 16px;
      border-radius: 4px;
      font-family: 'Courier New', monospace;
      font-size: 12px;
    }

    .log-entry {
      display: flex;
      margin: 4px 0;
      padding: 2px 0;
    }

    .log-entry.info {
      color: #4fc3f7;
    }

    .log-entry.warn {
      color: #ffb74d;
    }

    .log-entry.error {
      color: #f48fb1;
    }

    .log-time {
      margin-right: 12px;
      color: #888;
      min-width: 80px;
    }

    .log-message {
      flex: 1;
    }

    .instructions-section {
      margin: 32px 0;
    }

    .instructions-section h3 {
      color: #1976d2;
      margin-bottom: 16px;
    }

    .instruction-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
      gap: 20px;
      margin: 20px 0;
    }

    .instruction-item {
      display: flex;
      align-items: flex-start;
      gap: 16px;
      padding: 20px;
      border: 1px solid #e0e0e0;
      border-radius: 8px;
      background-color: #fff;
    }

    .instruction-item mat-icon {
      font-size: 32px;
      margin-top: 4px;
    }

    .instruction-item h4 {
      margin: 0 0 8px 0;
      color: #333;
    }

    .instruction-item p {
      margin: 0;
      color: #666;
      line-height: 1.5;
    }
  `]
})
export class TestMercadoPagoComponent implements OnInit {
  loading = false;
  resultados: any = null;
  logs: any[] = [];

  constructor(
    private bankApiService: BankApiService,
    private snackBar: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.adicionarLog('Componente de teste inicializado', 'info');
  }

  executarTeste(): void {
    this.loading = true;
    this.resultados = null;
    this.logs = [];
    
    this.adicionarLog('Iniciando teste da API do Mercado Pago...', 'info');
    
    // Simular chamada para o endpoint de teste
    this.bankApiService.testarMercadoPago().subscribe({
      next: (response) => {
        this.loading = false;
        this.resultados = response;
        this.adicionarLog('Teste concluído com sucesso', 'info');
        this.snackBar.open('Teste executado com sucesso!', 'Fechar', {
          duration: 3000
        });
      },
      error: (error) => {
        this.loading = false;
        this.resultados = {
          status: 'error',
          message: error.message || 'Erro ao executar teste',
          detalhes: error
        };
        this.adicionarLog(`Erro no teste: ${error.message}`, 'error');
        this.snackBar.open('Erro ao executar teste', 'Fechar', {
          duration: 5000
        });
      }
    });
  }

  limparResultados(): void {
    this.resultados = null;
    this.logs = [];
    this.adicionarLog('Resultados limpos', 'info');
  }

  adicionarLog(mensagem: string, tipo: 'info' | 'warn' | 'error' = 'info'): void {
    this.logs.push({
      timestamp: new Date().toLocaleTimeString(),
      message: mensagem,
      type: tipo
    });
  }

  formatJson(obj: any): string {
    return JSON.stringify(obj, null, 2);
  }
}
