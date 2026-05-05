import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSliderModule } from '@angular/material/slider';
import { MatDividerModule } from '@angular/material/divider';
import { MatTabsModule } from '@angular/material/tabs';
import { MatTableModule } from '@angular/material/table';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatExpansionModule } from '@angular/material/expansion';

import { SimulacaoService } from '../../services/simulacao.service';

@Component({
  selector: 'app-simulacoes',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatSliderModule,
    MatDividerModule,
    MatTabsModule,
    MatTableModule,
    MatChipsModule,
    MatProgressBarModule,
    MatExpansionModule
  ],
  template: `
    <div class="simulacoes-container">
      <mat-card>
        <mat-card-header>
          <mat-card-title>Simulações Financeiras</mat-card-title>
          <mat-card-subtitle>Calcule diferentes cenários financeiros</mat-card-subtitle>
        </mat-card-header>
      </mat-card>

      <mat-tab-group>
        <!-- Simulação de Cartão de Crédito -->
        <mat-tab label="Cartão de Crédito" *ngIf="false">
          <div class="tab-content">
            <mat-card>
              <mat-card-header>
                <mat-card-title>Simulação de Compra no Cartão</mat-card-title>
                <mat-card-subtitle>Calcule o impacto de uma compra no seu cartão</mat-card-subtitle>
              </mat-card-header>
              <mat-card-content>
                <form [formGroup]="formCartao" (ngSubmit)="simularCartao()">
                  <div class="form-row">
                    <mat-form-field appearance="outline">
                      <mat-label>Cartão de Crédito</mat-label>
                      <mat-select formControlName="cartaoId" required>
                        <mat-option *ngFor="let cartao of cartoes" [value]="cartao.id">
                          {{ cartao.nome }} - Limite: R$ {{ cartao.limite | number:'1.2-2' }}
                        </mat-option>
                      </mat-select>
                      <mat-error *ngIf="formCartao.get('cartaoId')?.hasError('required')">
                        Cartão é obrigatório
                      </mat-error>
                    </mat-form-field>

                    <mat-form-field appearance="outline">
                      <mat-label>Valor da Compra</mat-label>
                      <input matInput type="number" formControlName="valorCompra" required min="0" step="0.01">
                      <mat-error *ngIf="formCartao.get('valorCompra')?.hasError('required')">
                        Valor é obrigatório
                      </mat-error>
                      <mat-error *ngIf="formCartao.get('valorCompra')?.hasError('min')">
                        Valor deve ser maior que zero
                      </mat-error>
                    </mat-form-field>
                  </div>

                  <div class="form-row">
                    <mat-form-field appearance="outline">
                      <mat-label>Número de Parcelas</mat-label>
                      <mat-select formControlName="parcelas" required>
                        <mat-option value="1">1x sem juros</mat-option>
                        <mat-option value="2">2x sem juros</mat-option>
                        <mat-option value="3">3x sem juros</mat-option>
                        <mat-option value="6">6x com juros</mat-option>
                        <mat-option value="12">12x com juros</mat-option>
                      </mat-select>
                      <mat-error *ngIf="formCartao.get('parcelas')?.hasError('required')">
                        Número de parcelas é obrigatório
                      </mat-error>
                    </mat-form-field>

                    <mat-form-field appearance="outline">
                      <mat-label>Taxa de Juros Mensal (%)</mat-label>
                      <input matInput type="number" formControlName="taxaJuros" min="0" max="100" step="0.01">
                      <mat-error *ngIf="formCartao.get('taxaJuros')?.hasError('min')">
                        Taxa deve ser maior ou igual a zero
                      </mat-error>
                      <mat-error *ngIf="formCartao.get('taxaJuros')?.hasError('max')">
                        Taxa deve ser menor ou igual a 100
                      </mat-error>
                    </mat-form-field>
                  </div>

                  <div class="form-actions">
                    <button mat-raised-button color="primary" type="submit" [disabled]="formCartao.invalid">
                      <mat-icon>calculate</mat-icon>
                      Simular
                    </button>
                  </div>
                </form>

                <!-- Resultado da Simulação -->
                <div class="resultado-simulacao" *ngIf="resultadoCartao">
                  <mat-divider></mat-divider>
                  <h3>Resultado da Simulação</h3>
                  
                  <div class="resultado-grid">
                    <div class="resultado-item">
                      <span class="label">Valor da Compra:</span>
                      <span class="valor">R$ {{ resultadoCartao.valorCompra | number:'1.2-2' }}</span>
                    </div>
                    <div class="resultado-item">
                      <span class="label">Parcelas:</span>
                      <span class="valor">{{ resultadoCartao.parcelas }}x</span>
                    </div>
                    <div class="resultado-item">
                      <span class="label">Valor da Parcela:</span>
                      <span class="valor">R$ {{ resultadoCartao.valorParcela | number:'1.2-2' }}</span>
                    </div>
                    <div class="resultado-item">
                      <span class="label">Total com Juros:</span>
                      <span class="valor">R$ {{ resultadoCartao.totalComJuros | number:'1.2-2' }}</span>
                    </div>
                    <div class="resultado-item">
                      <span class="label">Juros Totais:</span>
                      <span class="valor negativo">R$ {{ resultadoCartao.jurosTotais | number:'1.2-2' }}</span>
                    </div>
                    <div class="resultado-item">
                      <span class="label">Limite Disponível:</span>
                      <span class="valor">R$ {{ resultadoCartao.limiteDisponivel | number:'1.2-2' }}</span>
                    </div>
                  </div>

                  <!-- Barra de Utilização -->
                  <div class="utilizacao-container">
                    <h4>Utilização do Limite</h4>
                    <mat-progress-bar 
                      [value]="resultadoCartao.percentualUtilizacao" 
                      [color]="resultadoCartao.percentualUtilizacao > 80 ? 'warn' : 'primary'">
                    </mat-progress-bar>
                    <span class="percentual">{{ resultadoCartao.percentualUtilizacao | number:'1.1-1' }}%</span>
                  </div>
                </div>
              </mat-card-content>
            </mat-card>
          </div>
        </mat-tab>

        <!-- Simulação de Investimento -->
        <mat-tab label="Investimento">
          <div class="tab-content">
            <mat-card>
              <mat-card-header>
                <mat-card-title>Simulação de Investimento</mat-card-title>
                <mat-card-subtitle>Calcule o crescimento do seu dinheiro ao longo do tempo</mat-card-subtitle>
              </mat-card-header>
              <mat-card-content>
                <form [formGroup]="formInvestimento" (ngSubmit)="simularInvestimento()">
                  <div class="form-row">
                    <mat-form-field appearance="outline">
                      <mat-label>Valor Inicial</mat-label>
                      <input matInput type="number" formControlName="valorInicial" required min="0" step="0.01">
                      <mat-error *ngIf="formInvestimento.get('valorInicial')?.hasError('required')">
                        Valor inicial é obrigatório
                      </mat-error>
                    </mat-form-field>

                    <mat-form-field appearance="outline">
                      <mat-label>Aporte Mensal</mat-label>
                      <input matInput type="number" formControlName="aporteMensal" min="0" step="0.01">
                      <mat-error *ngIf="formInvestimento.get('aporteMensal')?.hasError('min')">
                        Aporte deve ser maior ou igual a zero
                      </mat-error>
                    </mat-form-field>
                  </div>

                  <div class="form-row">
                    <mat-form-field appearance="outline">
                      <mat-label>Taxa de Retorno Anual (%)</mat-label>
                      <input matInput type="number" formControlName="taxaRetorno" required min="0" max="100" step="0.01">
                      <mat-error *ngIf="formInvestimento.get('taxaRetorno')?.hasError('required')">
                        Taxa de retorno é obrigatória
                      </mat-error>
                    </mat-form-field>

                    <mat-form-field appearance="outline">
                      <mat-label>Período (anos)</mat-label>
                      <input matInput type="number" formControlName="periodo" required min="1" max="50" step="1">
                      <mat-error *ngIf="formInvestimento.get('periodo')?.hasError('required')">
                        Período é obrigatório
                      </mat-error>
                    </mat-form-field>
                  </div>

                  <div class="form-actions">
                    <button mat-raised-button color="primary" type="submit" [disabled]="formInvestimento.invalid">
                      <mat-icon>trending_up</mat-icon>
                      Simular
                    </button>
                  </div>
                </form>

                <!-- Resultado da Simulação de Investimento -->
                <div class="resultado-simulacao" *ngIf="resultadoInvestimento">
                  <mat-divider></mat-divider>
                  <h3>Resultado da Simulação de Investimento</h3>
                  
                  <div class="resultado-grid">
                    <div class="resultado-item">
                      <span class="label">Valor Inicial:</span>
                      <span class="valor">R$ {{ resultadoInvestimento.valorInicial | number:'1.2-2' }}</span>
                    </div>
                    <div class="resultado-item">
                      <span class="label">Total de Aportes:</span>
                      <span class="valor">R$ {{ resultadoInvestimento.totalAportes | number:'1.2-2' }}</span>
                    </div>
                    <div class="resultado-item">
                      <span class="label">Juros Compostos:</span>
                      <span class="valor positivo">R$ {{ resultadoInvestimento.jurosCompostos | number:'1.2-2' }}</span>
                    </div>
                    <div class="resultado-item">
                      <span class="label">Valor Final:</span>
                      <span class="valor positivo">R$ {{ resultadoInvestimento.valorFinal | number:'1.2-2' }}</span>
                    </div>
                  </div>

                  <!-- Tabela de Projeção -->
                  <div class="projecao-container">
                    <h4>Projeção Anual</h4>
                    <div class="table-container">
                      <table class="projecao-table">
                        <thead>
                          <tr>
                            <th>Ano</th>
                            <th>Saldo Inicial</th>
                            <th>Aportes</th>
                            <th>Juros</th>
                            <th>Saldo Final</th>
                          </tr>
                        </thead>
                        <tbody>
                          <tr *ngFor="let projecao of resultadoInvestimento.projecaoAnual">
                            <td>{{ projecao.ano }}</td>
                            <td>R$ {{ projecao.saldoInicial | number:'1.2-2' }}</td>
                            <td>R$ {{ projecao.aportes | number:'1.2-2' }}</td>
                            <td>R$ {{ projecao.juros | number:'1.2-2' }}</td>
                            <td>R$ {{ projecao.saldoFinal | number:'1.2-2' }}</td>
                          </tr>
                        </tbody>
                      </table>
                    </div>
                  </div>
                </div>
              </mat-card-content>
            </mat-card>
          </div>
        </mat-tab>

        <!-- Simulação de Financiamento -->
        <mat-tab label="Financiamento">
          <div class="tab-content">
            <mat-card>
              <mat-card-header>
                <mat-card-title>Simulação de Financiamento</mat-card-title>
                <mat-card-subtitle>Calcule o valor das parcelas de um financiamento</mat-card-subtitle>
              </mat-card-header>
              <mat-card-content>
                <form [formGroup]="formFinanciamento" (ngSubmit)="simularFinanciamento()">
                  <div class="form-row">
                    <mat-form-field appearance="outline">
                      <mat-label>Valor do Bem</mat-label>
                      <input matInput type="number" formControlName="valorBem" required min="0" step="0.01">
                      <mat-error *ngIf="formFinanciamento.get('valorBem')?.hasError('required')">
                        Valor do bem é obrigatório
                      </mat-error>
                    </mat-form-field>

                    <mat-form-field appearance="outline">
                      <mat-label>Entrada</mat-label>
                      <input matInput type="number" formControlName="entrada" min="0" step="0.01">
                      <mat-error *ngIf="formFinanciamento.get('entrada')?.hasError('min')">
                        Entrada deve ser maior ou igual a zero
                      </mat-error>
                    </mat-form-field>
                  </div>

                  <div class="form-row">
                    <mat-form-field appearance="outline">
                      <mat-label>Taxa de Juros Mensal (%)</mat-label>
                      <input matInput type="number" formControlName="taxaJurosFinanciamento" required min="0" max="100" step="0.01">
                      <mat-error *ngIf="formFinanciamento.get('taxaJurosFinanciamento')?.hasError('required')">
                        Taxa de juros é obrigatória
                      </mat-error>
                    </mat-form-field>

                    <mat-form-field appearance="outline">
                      <mat-label>Prazo (meses)</mat-label>
                      <input matInput type="number" formControlName="prazo" required min="1" max="360" step="1">
                      <mat-error *ngIf="formFinanciamento.get('prazo')?.hasError('required')">
                        Prazo é obrigatório
                      </mat-error>
                    </mat-form-field>
                  </div>

                  <div class="form-actions">
                    <button mat-raised-button color="primary" type="submit" [disabled]="formFinanciamento.invalid">
                      <mat-icon>account_balance</mat-icon>
                      Simular
                    </button>
                  </div>
                </form>

                <!-- Resultado da Simulação de Financiamento -->
                <div class="resultado-simulacao" *ngIf="resultadoFinanciamento">
                  <mat-divider></mat-divider>
                  <h3>Resultado da Simulação de Financiamento</h3>
                  
                  <div class="resultado-grid">
                    <div class="resultado-item">
                      <span class="label">Valor do Bem:</span>
                      <span class="valor">R$ {{ resultadoFinanciamento.valorBem | number:'1.2-2' }}</span>
                    </div>
                    <div class="resultado-item">
                      <span class="label">Entrada:</span>
                      <span class="valor">R$ {{ resultadoFinanciamento.entrada | number:'1.2-2' }}</span>
                    </div>
                    <div class="resultado-item">
                      <span class="label">Valor Financiado:</span>
                      <span class="valor">R$ {{ resultadoFinanciamento.valorFinanciado | number:'1.2-2' }}</span>
                    </div>
                    <div class="resultado-item">
                      <span class="label">Valor da Parcela:</span>
                      <span class="valor">R$ {{ resultadoFinanciamento.valorParcela | number:'1.2-2' }}</span>
                    </div>
                    <div class="resultado-item">
                      <span class="label">Total a Pagar:</span>
                      <span class="valor">R$ {{ resultadoFinanciamento.totalPagar | number:'1.2-2' }}</span>
                    </div>
                    <div class="resultado-item">
                      <span class="label">Total de Juros:</span>
                      <span class="valor negativo">R$ {{ resultadoFinanciamento.totalJuros | number:'1.2-2' }}</span>
                    </div>
                  </div>
                </div>
              </mat-card-content>
            </mat-card>
          </div>
        </mat-tab>
      </mat-tab-group>
    </div>
  `,
  styles: [`
    .simulacoes-container {
      padding: 20px;
      max-width: 1100px;
      margin: 0 auto;
      color: var(--text-primary);
    }

    .tab-content {
      padding: 20px 0;
    }

    .form-row {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
      gap: 16px;
      margin-bottom: 16px;
    }

    .form-row:last-child {
      margin-bottom: 0;
    }

    .form-actions {
      margin-top: 20px;
      text-align: center;
    }

    .resultado-simulacao {
      margin-top: 28px;
    }

    .resultado-simulacao h3 {
      margin: 20px 0;
      color: var(--text-primary);
    }

    .resultado-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
      gap: 16px;
      margin-bottom: 20px;
    }

    .resultado-item {
      background: var(--exec-surface, #1e293b);
      border: 1px solid var(--exec-border, #334155);
      padding: 14px 16px;
      border-radius: 10px;
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 12px;
      min-width: 0;
    }

    .resultado-item .label {
      font-weight: 500;
      color: var(--text-secondary);
      flex-shrink: 0;
    }

    .resultado-item .valor {
      font-weight: 700;
      font-size: 1rem;
      font-family: var(--font-mono-amount);
      font-variant-numeric: tabular-nums;
      text-align: right;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      min-width: 0;
    }

    .valor.positivo { color: var(--exec-emerald, #10b981); }
    .valor.negativo { color: #f87171; }

    .utilizacao-container {
      margin-top: 20px;
    }

    .utilizacao-container h4 {
      margin-bottom: 12px;
      color: var(--text-primary);
    }

    .utilizacao-container mat-progress-bar {
      margin-bottom: 8px;
    }

    .percentual {
      font-weight: 700;
      color: var(--text-secondary);
      font-family: var(--font-mono-amount);
    }

    .projecao-container {
      margin-top: 20px;
    }

    .projecao-container h4 {
      margin-bottom: 12px;
      color: var(--text-primary);
    }

    .table-container {
      overflow-x: auto;
      border-radius: 10px;
      border: 1px solid var(--exec-border, #334155);
    }

    .projecao-table {
      width: 100%;
      border-collapse: collapse;
      background: var(--exec-surface, #1e293b);
      min-width: 520px;
    }

    .projecao-table th,
    .projecao-table td {
      padding: 12px;
      text-align: left;
      border-bottom: 1px solid var(--exec-border, #334155);
      color: var(--text-primary);
    }

    .projecao-table th {
      background: rgba(15, 23, 42, 0.85);
      font-weight: 600;
      color: var(--text-secondary);
      font-size: 12px;
      text-transform: uppercase;
      letter-spacing: 0.04em;
    }

    .projecao-table td {
      font-family: var(--font-mono-amount);
      font-variant-numeric: tabular-nums;
      font-size: 0.875rem;
    }

    mat-card {
      margin-bottom: 20px;
    }

    mat-form-field {
      width: 100%;
    }

    @media (max-width: 600px) {
      .simulacoes-container {
        padding: 12px;
      }
    }
  `]
})
export class SimulacoesComponent implements OnInit {
  // Formulários
  formCartao: FormGroup;
  formInvestimento: FormGroup;
  formFinanciamento: FormGroup;

  cartoes: any[] = [];
  // Resultados
  resultadoCartao: any = null;
  resultadoInvestimento: any = null;
  resultadoFinanciamento: any = null;

  constructor(
    private simulacaoService: SimulacaoService,
    private fb: FormBuilder
  ) {
    this.formCartao = this.fb.group({
      cartaoId: [''],
      valorCompra: [0],
      parcelas: ['1'],
      taxaJuros: [0]
    });

    this.formInvestimento = this.fb.group({
      valorInicial: ['', [Validators.required, Validators.min(0)]],
      aporteMensal: [0, [Validators.min(0)]],
      taxaRetorno: ['', [Validators.required, Validators.min(0), Validators.max(100)]],
      periodo: ['', [Validators.required, Validators.min(1), Validators.max(50)]]
    });

    this.formFinanciamento = this.fb.group({
      valorBem: ['', [Validators.required, Validators.min(0)]],
      entrada: [0, [Validators.min(0)]],
      taxaJurosFinanciamento: ['', [Validators.required, Validators.min(0), Validators.max(100)]],
      prazo: ['', [Validators.required, Validators.min(1), Validators.max(360)]]
    });
  }

  ngOnInit(): void {
    // sem inicialização adicional
  }

  simularCartao(): void {
    // fluxo removido por limpeza funcional
  }

  simularInvestimento(): void {
    if (this.formInvestimento.valid) {
      this.simulacaoService.simularInvestimento(this.formInvestimento.value).subscribe({
        next: (resultado) => this.resultadoInvestimento = resultado,
        error: () => this.resultadoInvestimento = null
      });
    }
  }

  simularFinanciamento(): void {
    if (this.formFinanciamento.valid) {
      const payload = {
        ...this.formFinanciamento.value,
        taxaJuros: this.formFinanciamento.value.taxaJurosFinanciamento
      };
      this.simulacaoService.simularFinanciamento(payload).subscribe({
        next: (resultado) => this.resultadoFinanciamento = resultado,
        error: () => this.resultadoFinanciamento = null
      });
    }
  }
}
