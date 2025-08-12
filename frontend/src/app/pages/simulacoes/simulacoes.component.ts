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
import { CartaoCreditoService } from '../../services/cartao-credito.service';
import { CartaoCredito } from '../../models/cartao-credito.model';

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
        <mat-tab label="Cartão de Crédito">
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
    }

    .tab-content {
      padding: 20px 0;
    }

    .form-row {
      display: grid;
      grid-template-columns: 1fr 1fr;
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
      margin-top: 30px;
    }

    .resultado-simulacao h3 {
      margin: 20px 0;
      color: #333;
    }

    .resultado-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
      gap: 16px;
      margin-bottom: 20px;
    }

    .resultado-item {
      background: #f5f5f5;
      padding: 16px;
      border-radius: 8px;
      display: flex;
      justify-content: space-between;
      align-items: center;
    }

    .resultado-item .label {
      font-weight: 500;
      color: #666;
    }

    .resultado-item .valor {
      font-weight: bold;
      font-size: 18px;
    }

    .valor.positivo { color: #4caf50; }
    .valor.negativo { color: #f44336; }

    .utilizacao-container {
      margin-top: 20px;
    }

    .utilizacao-container h4 {
      margin-bottom: 16px;
      color: #333;
    }

    .utilizacao-container mat-progress-bar {
      margin-bottom: 8px;
    }

    .percentual {
      font-weight: bold;
      color: #666;
    }

    .projecao-container {
      margin-top: 20px;
    }

    .projecao-container h4 {
      margin-bottom: 16px;
      color: #333;
    }

    .table-container {
      overflow-x: auto;
    }

    .projecao-table {
      width: 100%;
      border-collapse: collapse;
    }

    .projecao-table th,
    .projecao-table td {
      padding: 12px;
      text-align: left;
      border-bottom: 1px solid #eee;
    }

    .projecao-table th {
      background-color: #f5f5f5;
      font-weight: 500;
    }

    mat-card {
      margin-bottom: 20px;
    }

    mat-form-field {
      width: 100%;
    }
  `]
})
export class SimulacoesComponent implements OnInit {
  // Formulários
  formCartao: FormGroup;
  formInvestimento: FormGroup;
  formFinanciamento: FormGroup;
  
  // Dados
  cartoes: CartaoCredito[] = [];
  
  // Resultados
  resultadoCartao: any = null;
  resultadoInvestimento: any = null;
  resultadoFinanciamento: any = null;

  constructor(
    private simulacaoService: SimulacaoService,
    private cartaoService: CartaoCreditoService,
    private fb: FormBuilder
  ) {
    this.formCartao = this.fb.group({
      cartaoId: ['', Validators.required],
      valorCompra: ['', [Validators.required, Validators.min(0)]],
      parcelas: ['1', Validators.required],
      taxaJuros: [0, [Validators.min(0), Validators.max(100)]]
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
    this.carregarCartoes();
  }

  carregarCartoes(): void {
    this.cartaoService.getCartoes().subscribe({
      next: (cartoes) => {
        this.cartoes = cartoes;
      },
      error: (error) => {
        console.error('Erro ao carregar cartões:', error);
      }
    });
  }

  simularCartao(): void {
    if (this.formCartao.valid) {
      const dados = this.formCartao.value;
      const cartao = this.cartoes.find(c => c.id === dados.cartaoId);
      
      if (cartao) {
        const valorCompra = dados.valorCompra;
        const parcelas = parseInt(dados.parcelas);
        const taxaJuros = dados.taxaJuros / 100;
        
        let valorParcela: number;
        let totalComJuros: number;
        let jurosTotais: number;
        
        if (parcelas <= 3) {
          // Sem juros
          valorParcela = valorCompra / parcelas;
          totalComJuros = valorCompra;
          jurosTotais = 0;
        } else {
          // Com juros
          valorParcela = valorCompra * (taxaJuros * Math.pow(1 + taxaJuros, parcelas)) / (Math.pow(1 + taxaJuros, parcelas) - 1);
          totalComJuros = valorParcela * parcelas;
          jurosTotais = totalComJuros - valorCompra;
        }
        
        const limiteDisponivel = (cartao.limite || 0) - (cartao.limiteUtilizado || 0);
        const percentualUtilizacao = ((cartao.limiteUtilizado || 0) + valorCompra) / (cartao.limite || 1) * 100;
        
        this.resultadoCartao = {
          valorCompra,
          parcelas,
          valorParcela,
          totalComJuros,
          jurosTotais,
          limiteDisponivel,
          percentualUtilizacao
        };
      }
    }
  }

  simularInvestimento(): void {
    if (this.formInvestimento.valid) {
      const dados = this.formInvestimento.value;
      const valorInicial = dados.valorInicial;
      const aporteMensal = dados.aporteMensal || 0;
      const taxaRetorno = dados.taxaRetorno / 100;
      const periodo = dados.periodo;
      
      const taxaMensal = Math.pow(1 + taxaRetorno, 1/12) - 1;
      const totalAportes = valorInicial + (aporteMensal * periodo * 12);
      
      // Cálculo do valor final com juros compostos
      let valorFinal = valorInicial;
      const projecaoAnual: any[] = [];
      
      for (let ano = 1; ano <= periodo; ano++) {
        const saldoInicial = valorFinal;
        const aportes = aporteMensal * 12;
        
        // Aplicar juros compostos mensalmente
        for (let mes = 1; mes <= 12; mes++) {
          valorFinal = (valorFinal + aporteMensal) * (1 + taxaMensal);
        }
        
        const juros = valorFinal - saldoInicial - aportes;
        
        projecaoAnual.push({
          ano,
          saldoInicial,
          aportes,
          juros,
          saldoFinal: valorFinal
        });
      }
      
      const jurosCompostos = valorFinal - totalAportes;
      
      this.resultadoInvestimento = {
        valorInicial,
        totalAportes,
        jurosCompostos,
        valorFinal,
        projecaoAnual
      };
    }
  }

  simularFinanciamento(): void {
    if (this.formFinanciamento.valid) {
      const dados = this.formFinanciamento.value;
      const valorBem = dados.valorBem;
      const entrada = dados.entrada || 0;
      const taxaJuros = dados.taxaJurosFinanciamento / 100;
      const prazo = dados.prazo;
      
      const valorFinanciado = valorBem - entrada;
      
      // Cálculo da parcela usando a fórmula de financiamento
      const valorParcela = valorFinanciado * (taxaJuros * Math.pow(1 + taxaJuros, prazo)) / (Math.pow(1 + taxaJuros, prazo) - 1);
      const totalPagar = valorParcela * prazo;
      const totalJuros = totalPagar - valorFinanciado;
      
      this.resultadoFinanciamento = {
        valorBem,
        entrada,
        valorFinanciado,
        valorParcela,
        totalPagar,
        totalJuros
      };
    }
  }
}
