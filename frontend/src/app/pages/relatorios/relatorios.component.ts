import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatDividerModule } from '@angular/material/divider';
import { MatTabsModule } from '@angular/material/tabs';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatChipsModule } from '@angular/material/chips';

import { RelatorioService } from '../../services/relatorio.service';
import { TransacaoService } from '../../services/transacao.service';
import { CartaoCreditoService } from '../../services/cartao-credito.service';
import { Transacao, TipoTransacao } from '../../models/transacao.model';
import { CartaoCredito } from '../../models/cartao-credito.model';

@Component({
  selector: 'app-relatorios',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatSelectModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatDividerModule,
    MatTabsModule,
    MatProgressSpinnerModule,
    MatChipsModule
  ],
  template: `
    <div class="relatorios-container">
      <!-- Filtros -->
      <mat-card class="filtros-card">
        <mat-card-header>
          <mat-card-title>
            <mat-icon style="margin-right: 12px;">filter_list</mat-icon>
            Filtros do Relatório
          </mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <form [formGroup]="formFiltros" (ngSubmit)="gerarRelatorio()">
            <div class="filtros-row">
              <mat-form-field appearance="outline">
                <mat-label>Período</mat-label>
                <mat-select formControlName="periodo">
                  <mat-option value="7">📅 Últimos 7 dias</mat-option>
                  <mat-option value="30">📅 Últimos 30 dias</mat-option>
                  <mat-option value="90">📅 Últimos 90 dias</mat-option>
                  <mat-option value="365">📅 Último ano</mat-option>
                  <mat-option value="custom">📅 Personalizado</mat-option>
                </mat-select>
              </mat-form-field>

              <mat-form-field appearance="outline" *ngIf="formFiltros.get('periodo')?.value === 'custom'">
                <mat-label>Data Início</mat-label>
                <input matInput [matDatepicker]="pickerInicio" formControlName="dataInicio" placeholder="dd/mm/aaaa">
                <mat-datepicker-toggle matSuffix [for]="pickerInicio"></mat-datepicker-toggle>
                <mat-datepicker #pickerInicio></mat-datepicker>
              </mat-form-field>

              <mat-form-field appearance="outline" *ngIf="formFiltros.get('periodo')?.value === 'custom'">
                <mat-label>Data Fim</mat-label>
                <input matInput [matDatepicker]="pickerFim" formControlName="dataFim" placeholder="dd/mm/aaaa">
                <mat-datepicker-toggle matSuffix [for]="pickerFim"></mat-datepicker-toggle>
                <mat-datepicker #pickerFim></mat-datepicker>
              </mat-form-field>

              <mat-form-field appearance="outline">
                <mat-label>Tipo de Transação</mat-label>
                <mat-select formControlName="tipoTransacao">
                  <mat-option value="">💰 Todos</mat-option>
                  <mat-option value="RECEITA">📈 Receitas</mat-option>
                  <mat-option value="DESPESA">📉 Despesas</mat-option>
                </mat-select>
              </mat-form-field>

              <mat-form-field appearance="outline">
                <mat-label>Cartão de Crédito</mat-label>
                <mat-select formControlName="cartaoId">
                  <mat-option value="">💳 Todos</mat-option>
                  <mat-option *ngFor="let cartao of cartoes" [value]="cartao.id">
                    💳 {{ cartao.nome }}
                  </mat-option>
                </mat-select>
              </mat-form-field>

              <button mat-raised-button color="primary" type="submit" [disabled]="carregando">
                <mat-icon>refresh</mat-icon>
                {{ carregando ? 'Gerando...' : 'Gerar Relatório' }}
              </button>
            </div>
          </form>
        </mat-card-content>
      </mat-card>

      <!-- Resumo Geral -->
      <div class="resumo-grid" *ngIf="!carregando && dadosCarregados">
        <mat-card class="resumo-card">
          <mat-card-content>
            <div class="resumo-item">
              <div class="resumo-icon" style="color: #4caf50; font-size: 2rem; margin-bottom: 8px;">📈</div>
              <span class="label">Total Receitas</span>
              <span class="valor positivo">R$ {{ resumo.totalReceitas | number:'1.2-2' }}</span>
            </div>
          </mat-card-content>
        </mat-card>

        <mat-card class="resumo-card">
          <mat-card-content>
            <div class="resumo-item">
              <div class="resumo-icon" style="color: #f44336; font-size: 2rem; margin-bottom: 8px;">📉</div>
              <span class="label">Total Despesas</span>
              <span class="valor negativo">R$ {{ resumo.totalDespesas | number:'1.2-2' }}</span>
            </div>
          </mat-card-content>
        </mat-card>

        <mat-card class="resumo-card">
          <mat-card-content>
            <div class="resumo-item">
              <div class="resumo-icon" [style.color]="resumo.saldo >= 0 ? '#4caf50' : '#f44336'" style="font-size: 2rem; margin-bottom: 8px;">
                {{ resumo.saldo >= 0 ? '💰' : '⚠️' }}
              </div>
              <span class="label">Saldo</span>
              <span class="valor" [class.positivo]="resumo.saldo >= 0" [class.negativo]="resumo.saldo < 0">
                R$ {{ resumo.saldo | number:'1.2-2' }}
              </span>
            </div>
          </mat-card-content>
        </mat-card>

        <mat-card class="resumo-card">
          <mat-card-content>
            <div class="resumo-item">
              <div class="resumo-icon" style="color: #2196f3; font-size: 2rem; margin-bottom: 8px;">📊</div>
              <span class="label">Total Transações</span>
              <span class="valor">{{ resumo.totalTransacoes }}</span>
            </div>
          </mat-card-content>
        </mat-card>
      </div>

      <!-- Tabs de Relatórios -->
      <mat-card *ngIf="!carregando && dadosCarregados">
        <mat-card-header>
          <mat-card-title>
            <mat-icon style="margin-right: 12px;">analytics</mat-icon>
            Análises e Gráficos
          </mat-card-title>
        </mat-card-header>
        <mat-tab-group>
          <!-- Gráfico de Receitas vs Despesas -->
          <mat-tab label="📊 Receitas vs Despesas">
            <div class="chart-container">
              <div style="text-align: center;">
                <div style="font-size: 4rem; margin-bottom: 16px;">📈</div>
                <p style="font-size: 18px; color: #666; margin-bottom: 8px;">Gráfico de Pizza</p>
                <p style="font-size: 14px; color: #999;">Comparativo entre receitas e despesas</p>
              </div>
            </div>
          </mat-tab>

          <!-- Gráfico de Evolução Temporal -->
          <mat-tab label="📅 Evolução Temporal">
            <div class="chart-container">
              <div style="text-align: center;">
                <div style="font-size: 4rem; margin-bottom: 16px;">📊</div>
                <p style="font-size: 18px; color: #666; margin-bottom: 8px;">Gráfico de Linha</p>
                <p style="font-size: 14px; color: #999;">Evolução financeira ao longo do tempo</p>
              </div>
            </div>
          </mat-tab>

          <!-- Gráfico de Categorias -->
          <mat-tab label="🏷️ Por Categoria">
            <div class="chart-container">
              <div style="text-align: center;">
                <div style="font-size: 4rem; margin-bottom: 16px;">📊</div>
                <p style="font-size: 18px; color: #666; margin-bottom: 8px;">Gráfico de Barras</p>
                <p style="font-size: 14px; color: #999;">Distribuição por categorias de gastos</p>
              </div>
            </div>
          </mat-tab>

          <!-- Gráfico de Cartões -->
          <mat-tab label="💳 Por Cartão">
            <div class="chart-container">
              <div style="text-align: center;">
                <div style="font-size: 4rem; margin-bottom: 16px;">📊</div>
                <p style="font-size: 18px; color: #666; margin-bottom: 8px;">Gráfico de Rosca</p>
                <p style="font-size: 14px; color: #999;">Distribuição por cartões de crédito</p>
              </div>
            </div>
          </mat-tab>
        </mat-tab-group>
      </mat-card>

      <!-- Tabela de Transações -->
      <mat-card *ngIf="!carregando && dadosCarregados">
        <mat-card-header>
          <mat-card-title>
            <mat-icon style="margin-right: 12px;">receipt</mat-icon>
            Transações do Período
          </mat-card-title>
          <mat-card-subtitle>
            <mat-icon style="font-size: 16px; margin-right: 4px;">info</mat-icon>
            {{ transacoes.length }} transações encontradas
          </mat-card-subtitle>
        </mat-card-header>
        <mat-card-content>
          <div class="table-container">
            <table class="transacoes-table">
              <thead>
                <tr>
                  <th>📅 Data</th>
                  <th>📝 Descrição</th>
                  <th>🏷️ Categoria</th>
                  <th>💳 Cartão</th>
                  <th>💰 Tipo</th>
                  <th>💵 Valor</th>
                </tr>
              </thead>
              <tbody>
                <tr *ngFor="let transacao of transacoes.slice(0, 20)">
                  <td>{{ transacao.data | date:'dd/MM/yyyy' }}</td>
                  <td>{{ transacao.descricao }}</td>
                  <td>
                    <mat-chip [color]="transacao.tipoTransacao === 'RECEITA' ? 'primary' : 'accent'" selected>
                      {{ transacao.categoria?.nome || 'Sem categoria' }}
                    </mat-chip>
                  </td>
                  <td>{{ getNomeCartao(transacao.cartaoCreditoId) }}</td>
                  <td>
                    <mat-chip [color]="transacao.tipoTransacao === 'RECEITA' ? 'primary' : 'warn'" selected>
                      {{ transacao.tipoTransacao === 'RECEITA' ? 'Receita' : 'Despesa' }}
                    </mat-chip>
                  </td>
                  <td [class.positivo]="transacao.tipoTransacao === 'RECEITA'" [class.negativo]="transacao.tipoTransacao === 'DESPESA'">
                    R$ {{ transacao.valor | number:'1.2-2' }}
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </mat-card-content>
      </mat-card>

      <!-- Loading -->
      <div class="loading-container" *ngIf="carregando">
        <mat-spinner diameter="50"></mat-spinner>
        <p>Gerando relatório...</p>
      </div>
    </div>
  `,
  styles: [`
    .relatorios-container {
      padding: 20px;
      max-width: 1400px;
      margin: 0 auto;
    }

    .filtros-card {
      margin-bottom: 20px;
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      color: white;
    }

    .filtros-card mat-card-header {
      color: white;
    }

    .filtros-card mat-card-title {
      color: white;
      font-size: 1.5rem;
      font-weight: 600;
    }

    .filtros-row {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
      gap: 16px;
      align-items: end;
    }

    /* Responsividade para telas pequenas */
    @media (max-width: 768px) {
      .filtros-row {
        grid-template-columns: 1fr;
        gap: 12px;
      }
      
      .relatorios-container {
        padding: 16px;
      }
    }

    @media (max-width: 480px) {
      .relatorios-container {
        padding: 12px;
      }
      
      .filtros-card mat-card-title {
        font-size: 1.2rem;
      }
    }

    .resumo-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
      gap: 20px;
      margin-bottom: 24px;
    }

    .resumo-card {
      text-align: center;
      transition: transform 0.2s ease, box-shadow 0.2s ease;
      border-radius: 12px;
      overflow: hidden;
    }

    .resumo-card:hover {
      transform: translateY(-2px);
      box-shadow: 0 8px 25px rgba(0,0,0,0.15);
    }

    .resumo-item .label {
      display: block;
      font-size: 14px;
      color: #666;
      margin-bottom: 8px;
      text-transform: uppercase;
      letter-spacing: 0.5px;
    }

    .resumo-item .valor {
      display: block;
      font-size: 28px;
      font-weight: bold;
      margin-bottom: 8px;
    }

    .valor.positivo { 
      color: #4caf50; 
      text-shadow: 0 1px 2px rgba(76, 175, 80, 0.3);
    }
    
    .valor.negativo { 
      color: #f44336; 
      text-shadow: 0 1px 2px rgba(244, 67, 54, 0.3);
    }

    .chart-container {
      position: relative;
      height: 400px;
      margin: 20px;
      display: flex;
      align-items: center;
      justify-content: center;
      background: #f8f9fa;
      border-radius: 8px;
      border: 2px dashed #dee2e6;
    }

    .chart-container p {
      color: #6c757d;
      font-size: 16px;
      text-align: center;
      margin: 0;
    }

    .table-container {
      overflow-x: auto;
      border-radius: 8px;
      box-shadow: 0 2px 8px rgba(0,0,0,0.1);
    }

    .transacoes-table {
      width: 100%;
      border-collapse: collapse;
      background: white;
    }

    .transacoes-table th,
    .transacoes-table td {
      padding: 16px 12px;
      text-align: left;
      border-bottom: 1px solid #e9ecef;
    }

    .transacoes-table th {
      background: linear-gradient(135deg, #f8f9fa 0%, #e9ecef 100%);
      font-weight: 600;
      color: #495057;
      text-transform: uppercase;
      letter-spacing: 0.5px;
      font-size: 12px;
    }

    .transacoes-table tbody tr {
      transition: background-color 0.2s ease;
    }

    .transacoes-table tbody tr:hover {
      background-color: #f8f9fa;
    }

    .transacoes-table tbody tr:nth-child(even) {
      background-color: #fafbfc;
    }

    /* Responsividade da tabela */
    @media (max-width: 768px) {
      .transacoes-table {
        font-size: 14px;
      }
      
      .transacoes-table th,
      .transacoes-table td {
        padding: 12px 8px;
      }
    }

    @media (max-width: 600px) {
      .transacoes-table {
        font-size: 12px;
      }
      
      .transacoes-table th,
      .transacoes-table td {
        padding: 8px 6px;
      }
    }

    .loading-container {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 60px 20px;
      text-align: center;
      background: #f8f9fa;
      border-radius: 12px;
      margin: 20px 0;
    }

    .loading-container p {
      margin-top: 20px;
      color: #6c757d;
      font-size: 16px;
      font-weight: 500;
    }

    mat-card {
      margin-bottom: 24px;
      border-radius: 12px;
      box-shadow: 0 4px 12px rgba(0,0,0,0.1);
      transition: box-shadow 0.2s ease;
    }

    mat-card:hover {
      box-shadow: 0 6px 20px rgba(0,0,0,0.15);
    }

    /* Melhorias nos campos de formulário */
    mat-form-field {
      width: 100%;
    }

    mat-form-field.mat-form-field-appearance-outline .mat-form-field-outline {
      color: rgba(255,255,255,0.3);
    }

    mat-form-field.mat-form-field-appearance-outline .mat-form-field-label {
      color: rgba(255,255,255,0.8);
    }

    mat-form-field.mat-form-field-appearance-outline .mat-form-field-outline-thick {
      color: rgba(255,255,255,0.8);
    }

    /* Botão de submit melhorado */
    button[type="submit"] {
      height: 56px;
      font-size: 16px;
      font-weight: 600;
      border-radius: 8px;
      text-transform: uppercase;
      letter-spacing: 0.5px;
      transition: all 0.3s ease;
    }

    button[type="submit"]:hover {
      transform: translateY(-1px);
      box-shadow: 0 6px 20px rgba(0,0,0,0.2);
    }

    /* Melhorias nos chips */
    mat-chip {
      border-radius: 20px;
      font-weight: 500;
      font-size: 12px;
    }

    /* Responsividade para os chips */
    @media (max-width: 480px) {
      mat-chip {
        font-size: 11px;
        padding: 4px 8px;
      }
    }

    /* Melhorias nos ícones */
    .resumo-icon {
      display: block;
      text-align: center;
      margin-bottom: 12px;
    }

    /* Melhorias nos tabs */
    .mat-tab-label {
      font-weight: 500;
      font-size: 14px;
    }

    .mat-tab-label-content {
      display: flex;
      align-items: center;
      gap: 8px;
    }

    /* Melhorias na tabela */
    .transacoes-table th {
      position: relative;
    }

    .transacoes-table th::after {
      content: '';
      position: absolute;
      bottom: 0;
      left: 0;
      right: 0;
      height: 2px;
      background: linear-gradient(90deg, #667eea, #764ba2);
    }

    /* Melhorias nos cards */
    .resumo-card mat-card-content {
      padding: 24px;
    }

    .resumo-item {
      text-align: center;
    }

    /* Animações suaves */
    .resumo-card,
    .filtros-card,
    mat-card {
      transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
    }

    /* Melhorias no loading */
    .loading-container mat-spinner {
      margin-bottom: 16px;
    }

    /* Melhorias nos filtros */
    .filtros-card mat-form-field {
      margin-bottom: 8px;
    }

    /* Melhorias na responsividade geral */
    @media (max-width: 1024px) {
      .resumo-grid {
        grid-template-columns: repeat(2, 1fr);
      }
    }

    @media (max-width: 600px) {
      .resumo-grid {
        grid-template-columns: 1fr;
      }
      
      .filtros-row {
        gap: 8px;
      }
    }
  `]
})
export class RelatoriosComponent implements OnInit {

  // Filtros
  formFiltros: FormGroup;
  
  // Dados
  transacoes: Transacao[] = [];
  cartoes: CartaoCredito[] = [];
  carregando = false;
  dadosCarregados = false;

  // Resumo
  resumo = {
    totalReceitas: 0,
    totalDespesas: 0,
    saldo: 0,
    totalTransacoes: 0
  };

  constructor(
    private relatorioService: RelatorioService,
    private transacaoService: TransacaoService,
    private cartaoService: CartaoCreditoService,
    private fb: FormBuilder
  ) {
    this.formFiltros = this.fb.group({
      periodo: ['30'],
      dataInicio: [null],
      dataFim: [null],
      tipoTransacao: [''],
      cartaoId: ['']
    });
  }

  ngOnInit(): void {
    this.carregarCartoes();
    this.gerarRelatorio();
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

  gerarRelatorio(): void {
    this.carregando = true;
    this.dadosCarregados = false;

    const filtros = this.formFiltros.value;
    let dataInicio: Date;
    let dataFim: Date = new Date();

    // Calcular datas baseado no período selecionado
    if (filtros.periodo === 'custom') {
      dataInicio = filtros.dataInicio;
      dataFim = filtros.dataFim;
    } else {
      const dias = parseInt(filtros.periodo);
      dataInicio = new Date();
      dataInicio.setDate(dataInicio.getDate() - dias);
    }

    // Buscar transações do período
    this.transacaoService.getTransacoesPorPeriodo(
      dataInicio?.toISOString() || new Date().toISOString(), 
      dataFim?.toISOString() || new Date().toISOString()
    ).subscribe({
      next: (transacoes) => {
        this.transacoes = transacoes;
        this.calcularResumo();
        this.gerarGraficos();
        this.carregando = false;
        this.dadosCarregados = true;
      },
      error: (error) => {
        console.error('Erro ao gerar relatório:', error);
        this.carregando = false;
      }
    });
  }

  calcularResumo(): void {
    this.resumo.totalReceitas = this.transacoes
      .filter(t => t.tipoTransacao === 'RECEITA')
      .reduce((sum, t) => sum + t.valor, 0);

    this.resumo.totalDespesas = this.transacoes
      .filter(t => t.tipoTransacao === 'DESPESA')
      .reduce((sum, t) => sum + t.valor, 0);

    this.resumo.saldo = this.resumo.totalReceitas - this.resumo.totalDespesas;
    this.resumo.totalTransacoes = this.transacoes.length;
  }

  gerarGraficos(): void {
    // Implementação simplificada sem gráficos por enquanto
    console.log('Gráficos desabilitados temporariamente');
  }

  gerarGraficoPizza(): void {
    // Implementação simplificada
  }

  gerarGraficoLinha(): void {
    // Implementação simplificada
  }

  gerarGraficoBarras(): void {
    // Implementação simplificada
  }

  gerarGraficoRosca(): void {
    // Implementação simplificada
  }

  getNomeCartao(cartaoId: number | undefined): string {
    if (!cartaoId) return 'N/A';
    const cartao = this.cartoes.find(c => c.id === cartaoId);
    return cartao ? cartao.nome : 'N/A';
  }
}
