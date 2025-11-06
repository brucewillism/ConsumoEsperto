import { Component, OnInit, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule, FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatChipsModule } from '@angular/material/chips';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TransacaoService } from '../../services/transacao.service';
import { Transacao, TipoTransacao } from '../../models/transacao.model';
import { Categoria } from '../../models/categoria.model';

@Component({
  selector: 'app-transacoes',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    FormsModule,
    MatCardModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatPaginatorModule,
    MatSortModule,
    MatChipsModule
  ],
  template: `
    <div class="transacoes-container">
      <div class="header">
        <div class="header-left">
        <h1>Transações</h1>
          <div class="mes-atual-indicator" [class.mes-filtrado]="filtroDataInicio">
            <mat-icon>{{ filtroDataInicio ? 'filter_list' : 'calendar_today' }}</mat-icon>
            <span>{{ filtroDataInicio ? 'Mês Filtrado' : 'Mês Atual' }}: {{ obterNomeMesAtual() }}</span>
          </div>
        </div>
        <button mat-raised-button color="primary" (click)="abrirDialogoTransacao()" class="btn-nova-transacao">
          <mat-icon>add_circle</mat-icon>
          Nova Transação
        </button>
      </div>

      <!-- Filtros -->
      <mat-card class="filtros-card">
        <mat-card-content>
          <div class="filtros-row">
            <mat-form-field appearance="outline">
              <mat-label>Tipo</mat-label>
              <mat-select [(ngModel)]="filtroTipo" (selectionChange)="aplicarFiltros()">
                <mat-option value="">Todos</mat-option>
                <mat-option [value]="TipoTransacao.RECEITA">Receitas</mat-option>
                <mat-option [value]="TipoTransacao.DESPESA">Despesas</mat-option>
              </mat-select>
            </mat-form-field>

            <mat-form-field appearance="outline" class="date-field">
              <mat-label>Data Início</mat-label>
              <input matInput [matDatepicker]="dataInicioPicker" [(ngModel)]="filtroDataInicio" (dateChange)="onDataInicioChange($event)" placeholder="dd/mm/aaaa" readonly>
              <mat-datepicker-toggle matSuffix [for]="dataInicioPicker"></mat-datepicker-toggle>
              <mat-datepicker #dataInicioPicker [startAt]="filtroDataInicio" [touchUi]="false" [panelClass]="'custom-datepicker'" [opened]="false"></mat-datepicker>
            </mat-form-field>

            <mat-form-field appearance="outline" class="date-field">
              <mat-label>Data Fim</mat-label>
              <input matInput [matDatepicker]="dataFimPicker" [(ngModel)]="filtroDataFim" (dateChange)="onDataFimChange($event)" placeholder="dd/mm/aaaa" readonly>
              <mat-datepicker-toggle matSuffix [for]="dataFimPicker"></mat-datepicker-toggle>
              <mat-datepicker #dataFimPicker [startAt]="filtroDataFim" [touchUi]="false" [panelClass]="'custom-datepicker'" [opened]="false"></mat-datepicker>
            </mat-form-field>

            <button mat-stroked-button (click)="limparFiltros()" class="btn-limpar">
              <mat-icon>clear_all</mat-icon>
              Limpar
            </button>
          </div>
        </mat-card-content>
      </mat-card>

      <!-- Tabela de Transações -->
      <mat-card>
        <mat-card-content>
          <table mat-table [dataSource]="transacoesFiltradas" matSort (matSortChange)="ordenar($event)">
            <!-- Descrição -->
            <ng-container matColumnDef="descricao">
              <th mat-header-cell *matHeaderCellDef mat-sort-header> Descrição </th>
              <td mat-cell *matCellDef="let transacao"> {{ transacao.descricao }} </td>
            </ng-container>

            <!-- Valor -->
            <ng-container matColumnDef="valor">
              <th mat-header-cell *matHeaderCellDef mat-sort-header> Valor </th>
              <td mat-cell *matCellDef="let transacao" [class]="transacao.tipoTransacao === 'RECEITA' ? 'positive' : 'negative'">
                {{ transacao.tipoTransacao === 'RECEITA' ? '+' : '-' }}R$ {{ transacao.valor | number:'1.2-2' }}
              </td>
            </ng-container>

            <!-- Tipo -->
            <ng-container matColumnDef="tipo">
              <th mat-header-cell *matHeaderCellDef> Tipo </th>
              <td mat-cell *matCellDef="let transacao">
                <mat-chip [color]="transacao.tipoTransacao === 'RECEITA' ? 'accent' : 'warn'" selected>
                  {{ transacao.tipoTransacao === 'RECEITA' ? 'Receita' : 'Despesa' }}
                </mat-chip>
              </td>
            </ng-container>

            <!-- Categoria -->
            <ng-container matColumnDef="categoria">
              <th mat-header-cell *matHeaderCellDef> Categoria </th>
              <td mat-cell *matCellDef="let transacao"> {{ transacao.categoriaNome || 'Sem categoria' }} </td>
            </ng-container>

            <!-- Data -->
            <ng-container matColumnDef="data">
              <th mat-header-cell *matHeaderCellDef mat-sort-header> Data </th>
              <td mat-cell *matCellDef="let transacao"> {{ transacao.dataTransacao | date:'dd/MM/yyyy' }} </td>
            </ng-container>

            <!-- Ações -->
            <ng-container matColumnDef="acoes">
              <th mat-header-cell *matHeaderCellDef> Ações </th>
              <td mat-cell *matCellDef="let transacao">
                <button mat-icon-button color="primary" (click)="editarTransacao(transacao)">
                  <mat-icon>edit</mat-icon>
                </button>
                <button mat-icon-button color="warn" (click)="deletarTransacao(transacao.id!)">
                  <mat-icon>delete</mat-icon>
                </button>
              </td>
            </ng-container>

            <tr mat-header-row *matHeaderRowDef="colunasExibidas"></tr>
            <tr mat-row *matRowDef="let row; columns: colunasExibidas;"></tr>
          </table>

          <mat-paginator [length]="totalTransacoes" [pageSize]="tamanhoPagina" [pageSizeOptions]="[5, 10, 25, 50]" (page)="paginar($event)"></mat-paginator>
        </mat-card-content>
      </mat-card>
    </div>

    <!-- Dialog para Nova/Editar Transação -->
    <ng-template #dialogoTransacao>
      <h2 mat-dialog-title>{{ transacaoEditando ? 'Editar' : 'Nova' }} Transação</h2>
      <form [formGroup]="transacaoForm" (ngSubmit)="salvarTransacao()">
        <mat-dialog-content>
          <div class="form-row">
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Descrição</mat-label>
              <input matInput formControlName="descricao" placeholder="Digite a descrição">
              <mat-error *ngIf="transacaoForm.get('descricao')?.hasError('required')">
                Descrição é obrigatória
              </mat-error>
            </mat-form-field>
          </div>

          <div class="form-row">
            <mat-form-field appearance="outline">
              <mat-label>Valor</mat-label>
              <input matInput type="number" formControlName="valor" placeholder="0,00" step="0.01">
              <mat-error *ngIf="transacaoForm.get('valor')?.hasError('required')">
                Valor é obrigatório
              </mat-error>
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>Tipo</mat-label>
              <mat-select formControlName="tipoTransacao">
                <mat-option [value]="TipoTransacao.RECEITA">Receita</mat-option>
                <mat-option [value]="TipoTransacao.DESPESA">Despesa</mat-option>
              </mat-select>
            </mat-form-field>
          </div>

          <div class="form-row">
            <mat-form-field appearance="outline">
              <mat-label>Data</mat-label>
              <input matInput [matDatepicker]="dataPicker" formControlName="dataTransacao">
              <mat-datepicker-toggle matSuffix [for]="dataPicker"></mat-datepicker-toggle>
              <mat-datepicker #dataPicker></mat-datepicker>
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>Categoria</mat-label>
              <mat-select formControlName="categoriaId">
                <mat-option value="">Sem categoria</mat-option>
                <mat-option *ngFor="let categoria of categorias" [value]="categoria.id">
                  {{ categoria.nome }}
                </mat-option>
              </mat-select>
            </mat-form-field>
          </div>
        </mat-dialog-content>

        <mat-dialog-actions align="end">
          <button mat-button mat-dialog-close>Cancelar</button>
          <button mat-raised-button color="primary" type="submit" [disabled]="transacaoForm.invalid">
            {{ transacaoEditando ? 'Atualizar' : 'Salvar' }}
          </button>
        </mat-dialog-actions>
      </form>
    </ng-template>
  `,
  styles: [`
    .transacoes-container {
      padding: 24px;
      max-width: 1400px;
      margin: 0 auto;
      color: var(--text-primary);
      background-color: var(--bg-primary);
    }

    .header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 32px;
      flex-wrap: wrap;
      gap: 16px;

      .header-left {
        display: flex;
        flex-direction: column;
        gap: 8px;

      h1 {
        margin: 0;
        color: var(--text-primary);
        font-size: 2.5rem;
        font-weight: 700;
        }

        .mes-atual-indicator {
          display: flex;
          align-items: center;
          gap: 8px;
          color: var(--text-secondary);
          font-size: 0.9rem;
          background-color: var(--bg-secondary);
          padding: 6px 12px;
          border-radius: 16px;
          border: 1px solid var(--border-color);

          mat-icon {
            font-size: 18px;
            width: 18px;
            height: 18px;
          }

          &.mes-filtrado {
            background-color: #ff9800;
            color: white;
            border-color: #f57c00;
            
            mat-icon {
              color: white;
            }
          }
        }
      }
    }

    .btn-nova-transacao, .btn-limpar {
      display: flex !important;
      align-items: center !important;
      gap: 8px !important;
      min-width: 140px !important;
      
      mat-icon {
        font-size: 20px !important;
        width: 20px !important;
        height: 20px !important;
        margin-right: 4px !important;
      }
    }

    .filtros-card {
      margin-bottom: 24px;
      background-color: var(--bg-secondary);
      border: 1px solid var(--border-color);
      color: var(--text-primary);

      mat-card-content {
        padding: 24px;
      }
    }

    .filtros-row {
      display: flex;
      gap: 16px;
      flex-wrap: wrap;
      align-items: center;

      mat-form-field {
        min-width: 200px;
      }

      .date-field {
        position: relative;
        z-index: 1;
      }

      button {
        min-width: 120px;
      }
    }

    // Correção específica para datepickers - FORÇA z-index muito alto
    ::ng-deep .mat-datepicker-popup {
      z-index: 9999 !important;
      position: fixed !important;
    }

    ::ng-deep .mat-calendar {
      z-index: 9999 !important;
    }

    // Classe customizada para datepickers
    ::ng-deep .custom-datepicker {
      z-index: 9999 !important;
      position: fixed !important;
      
      .mat-calendar {
        z-index: 9999 !important;
        position: relative !important;
      }
    }

    // Garantir que o overlay container tenha z-index muito alto
    ::ng-deep .cdk-overlay-container {
      z-index: 9999 !important;
    }

    ::ng-deep .cdk-overlay-pane {
      z-index: 9999 !important;
    }

    // Forçar todos os overlays do Angular Material
    ::ng-deep .mat-datepicker-content {
      z-index: 9999 !important;
    }

    ::ng-deep .mat-datepicker-popup .mat-calendar {
      z-index: 9999 !important;
    }

    mat-card {
      background-color: var(--bg-secondary);
      border: 1px solid var(--border-color);
      color: var(--text-primary);
      margin-bottom: 24px;

      mat-card-content {
        padding: 24px;
      }
    }

    table {
      width: 100%;
      background-color: var(--bg-secondary);
      color: var(--text-primary);

      th {
        background-color: var(--bg-tertiary);
        color: var(--text-primary);
        font-weight: 600;
        padding: 16px;
        border-bottom: 1px solid var(--border-color);
      }

      td {
        padding: 16px;
        border-bottom: 1px solid var(--border-color);
        color: var(--text-primary);
      }

      .positive {
        color: var(--success-color);
        font-weight: 600;
      }

      .negative {
        color: var(--danger-color);
        font-weight: 600;
      }
    }

    .form-row {
      display: flex;
      gap: 16px;
      margin-bottom: 16px;

      .full-width {
        flex: 1;
      }

      mat-form-field {
        flex: 1;
      }
    }

    mat-paginator {
      background-color: var(--bg-secondary);
      color: var(--text-primary);
      border-top: 1px solid var(--border-color);
    }

    // Responsividade
    @media (max-width: 768px) {
      .transacoes-container {
        padding: 16px;
      }

      .header {
        flex-direction: column;
        align-items: flex-start;
        gap: 16px;

        h1 {
          font-size: 2rem;
        }
      }

      .filtros-row {
        flex-direction: column;
        align-items: stretch;

        mat-form-field, button {
          min-width: 100%;
        }
      }

      .form-row {
        flex-direction: column;
      }
    }
  `]
})
export class TransacoesComponent implements OnInit {
  @ViewChild('dialogoTransacao') dialogoTransacao: any;
  transacoes: Transacao[] = [];
  transacoesFiltradas: Transacao[] = [];
  categorias: Categoria[] = [];
  transacaoForm: FormGroup;
  transacaoEditando: Transacao | null = null;
  
  // Filtros
  filtroTipo: string = '';
  filtroDataInicio: Date | null = null;
  filtroDataFim: Date | null = null;
  
  // Paginação
  totalTransacoes = 0;
  tamanhoPagina = 10;
  paginaAtual = 0;
  
  // Colunas da tabela
  colunasExibidas = ['descricao', 'valor', 'tipo', 'categoria', 'data', 'acoes'];
  
  // Enum para uso no template
  TipoTransacao = TipoTransacao;

  constructor(
    private fb: FormBuilder,
    private transacaoService: TransacaoService,
    private dialog: MatDialog,
    private snackBar: MatSnackBar
  ) {
    this.transacaoForm = this.fb.group({
      descricao: ['', Validators.required],
      valor: ['', [Validators.required, Validators.min(0.01)]],
      tipoTransacao: [TipoTransacao.DESPESA, Validators.required],
      dataTransacao: [new Date(), Validators.required],
      categoriaId: ['']
    });
  }

  ngOnInit(): void {
    this.carregarTransacoes();
    this.carregarCategorias();
    this.configurarFormatoData();
  }

  configurarFormatoData(): void {
    // Configurar formato de data brasileiro para os datepickers
    this.configurarFormatoBrasileiro();
    console.log('📅 Formato de data configurado para dd/mm/yyyy');
    
    // Definir datas padrão do mês atual
    this.definirDatasPadraoMesAtual();
    
    // Configurar posicionamento dos datepickers
    this.configurarPosicionamentoDatepickers();
  }

  configurarFormatoBrasileiro(): void {
    // Configurar locale brasileiro para formatação de datas
    setTimeout(() => {
      // Aplicar configuração de locale brasileiro
      const style = document.createElement('style');
      style.textContent = `
        .mat-datepicker-input {
          font-family: 'Roboto', sans-serif;
        }
        .mat-form-field input {
          text-align: center;
        }
      `;
      document.head.appendChild(style);
      
      // Configurar formatação de data nos inputs
      this.configurarInputsData();
      
      // Forçar formatação brasileira nos datepickers
      this.forcarFormatacaoBrasileira();
    }, 100);
  }

  forcarFormatacaoBrasileira(): void {
    // Interceptar mudanças nos inputs de data e forçar formato brasileiro
    const observer = new MutationObserver(() => {
      this.atualizarFormatacaoDatas();
    });
    
    // Observar mudanças no DOM
    const container = document.querySelector('.filtros-row');
    if (container) {
      observer.observe(container, { childList: true, subtree: true });
    }
    
    // Aplicar formatação inicial
    setTimeout(() => {
      this.atualizarFormatacaoDatas();
      this.interceptarMudancasInputs();
    }, 300);
  }

  interceptarMudancasInputs(): void {
    // Interceptar mudanças diretamente nos inputs
    const inputs = document.querySelectorAll('input[matDatepicker]');
    inputs.forEach((input: any) => {
      // Interceptar evento de input
      input.addEventListener('input', (e: any) => {
        if (e.target.value) {
          setTimeout(() => {
            const date = new Date(e.target.value);
            if (!isNaN(date.getTime())) {
              const formatted = this.formatarDataBrasileira(date);
              if (e.target.value !== formatted) {
                e.target.value = formatted;
                console.log(`📅 Interceptado input: ${e.target.value} → ${formatted}`);
              }
            }
          }, 10);
        }
      });

      // Interceptar evento de blur
      input.addEventListener('blur', (e: any) => {
        if (e.target.value) {
          const date = new Date(e.target.value);
          if (!isNaN(date.getTime())) {
            const formatted = this.formatarDataBrasileira(date);
            if (e.target.value !== formatted) {
              e.target.value = formatted;
              console.log(`📅 Interceptado blur: ${e.target.value} → ${formatted}`);
            }
          }
        }
      });
    });
  }

  configurarInputsData(): void {
    // Aguardar um pouco para garantir que os inputs estejam no DOM
    setTimeout(() => {
      const inputs = document.querySelectorAll('input[matDatepicker]');
      inputs.forEach((input: any) => {
        if (input && input.value) {
          const date = new Date(input.value);
          if (!isNaN(date.getTime())) {
            input.value = date.toLocaleDateString('pt-BR');
          }
        }
      });
    }, 200);
  }

  configurarPosicionamentoDatepickers(): void {
    // Aguardar um pouco para garantir que o DOM esteja pronto
    setTimeout(() => {
      // Aplicar estilos globais para corrigir posicionamento com z-index muito alto
      const style = document.createElement('style');
      style.textContent = `
        .mat-datepicker-popup {
          z-index: 9999 !important;
          position: fixed !important;
        }
        .mat-calendar {
          z-index: 9999 !important;
        }
        .cdk-overlay-container {
          z-index: 9999 !important;
        }
        .cdk-overlay-pane {
          z-index: 9999 !important;
        }
        .mat-datepicker-content {
          z-index: 9999 !important;
        }
        .mat-datepicker-popup .mat-calendar {
          z-index: 9999 !important;
        }
        .custom-datepicker {
          z-index: 9999 !important;
        }
        .mat-datepicker-popup.custom-datepicker {
          z-index: 9999 !important;
          position: fixed !important;
        }
      `;
      document.head.appendChild(style);
      console.log('📅 Posicionamento dos datepickers configurado com z-index 9999');
      
      // Adicionar listener para fechar datepickers quando clicar fora
      this.adicionarListenerFechamentoDatepicker();
    }, 100);
  }

  adicionarListenerFechamentoDatepicker(): void {
    // Fechar datepickers quando clicar fora
    document.addEventListener('click', (event) => {
      const target = event.target as HTMLElement;
      if (!target.closest('.mat-datepicker-toggle') && !target.closest('.mat-calendar')) {
        // Fechar todos os datepickers abertos
        const datepickers = document.querySelectorAll('.mat-datepicker-popup');
        datepickers.forEach(dp => {
          if (dp instanceof HTMLElement) {
            dp.style.display = 'none';
          }
        });
      }
    });
  }

  definirDatasPadraoMesAtual(): void {
    const agora = new Date();
    const primeiroDia = new Date(agora.getFullYear(), agora.getMonth(), 1);
    const ultimoDia = new Date(agora.getFullYear(), agora.getMonth() + 1, 0);
    
    this.filtroDataInicio = primeiroDia;
    this.filtroDataFim = ultimoDia;
    
    console.log(`📅 Filtros de data definidos: ${primeiroDia.toLocaleDateString('pt-BR')} até ${ultimoDia.toLocaleDateString('pt-BR')}`);
    
    // Forçar formatação imediata
    setTimeout(() => {
      this.forcarFormatacaoImediata();
    }, 200);
  }

  forcarFormatacaoImediata(): void {
    // Forçar formatação imediata dos campos de data
    const inputs = document.querySelectorAll('input[matDatepicker]');
    inputs.forEach((input: any) => {
      if (input && input.value) {
        // Converter qualquer formato para brasileiro
        const date = new Date(input.value);
        if (!isNaN(date.getTime())) {
          const formatted = this.formatarDataBrasileira(date);
          input.value = formatted;
          console.log(`📅 Formatação forçada: ${input.value} → ${formatted}`);
        }
      }
    });
  }

  onDataInicioChange(event: any): void {
    if (event.value) {
      this.filtroDataInicio = event.value;
      // Forçar formatação brasileira
      setTimeout(() => {
        this.forcarFormatacaoCampoData('dataInicio');
      }, 10);
    }
    this.aplicarFiltros();
  }

  onDataFimChange(event: any): void {
    if (event.value) {
      this.filtroDataFim = event.value;
      // Forçar formatação brasileira
      setTimeout(() => {
        this.forcarFormatacaoCampoData('dataFim');
      }, 10);
    }
    this.aplicarFiltros();
  }

  forcarFormatacaoCampoData(tipo: 'dataInicio' | 'dataFim'): void {
    const input = document.querySelector(`input[matDatepicker="${tipo}"]`) as HTMLInputElement;
    if (input && input.value) {
      const date = new Date(input.value);
      if (!isNaN(date.getTime())) {
        const formatted = this.formatarDataBrasileira(date);
        input.value = formatted;
        console.log(`📅 Campo ${tipo} formatado: ${input.value} → ${formatted}`);
      }
    }
  }

  carregarTransacoes(): void {
    // Carregar TODAS as transações para permitir filtros por data
    this.transacaoService.buscarPorUsuario().subscribe({
      next: (transacoes) => {
        this.transacoes = transacoes;
        this.aplicarFiltros(); // Aplica filtros e paginação
        console.log(`📅 Carregadas ${transacoes.length} transações (todas as transações)`);
      },
      error: (error) => {
        console.error('Erro ao carregar transações:', error);
        this.snackBar.open('Erro ao carregar transações', 'Fechar', { duration: 3000 });
      }
    });
  }

  carregarCategorias(): void {
    // Por enquanto, categorias mockadas - será integrado com backend posteriormente
    this.categorias = [
      { id: 1, nome: 'Alimentação', cor: '#FF6B6B' },
      { id: 2, nome: 'Transporte', cor: '#4ECDC4' },
      { id: 3, nome: 'Moradia', cor: '#45B7D1' },
      { id: 4, nome: 'Saúde', cor: '#96CEB4' },
      { id: 5, nome: 'Educação', cor: '#FFEAA7' },
      { id: 6, nome: 'Lazer', cor: '#DDA0DD' },
      { id: 7, nome: 'Vestuário', cor: '#98D8C8' },
      { id: 8, nome: 'Tecnologia', cor: '#F7DC6F' }
    ];
  }

  aplicarFiltros(): void {
    let filtradas = [...this.transacoes];

    // Filtro por tipo
    if (this.filtroTipo) {
      filtradas = filtradas.filter(t => t.tipoTransacao === this.filtroTipo);
    }

    // Filtro por data - corrigido para funcionar corretamente
    if (this.filtroDataInicio) {
      const dataInicio = new Date(this.filtroDataInicio);
      dataInicio.setHours(0, 0, 0, 0); // Início do dia
      filtradas = filtradas.filter(t => {
        if (!t.dataTransacao) return false;
        const dataTransacao = new Date(t.dataTransacao);
        dataTransacao.setHours(0, 0, 0, 0); // Início do dia
        return dataTransacao >= dataInicio;
      });
    }

    if (this.filtroDataFim) {
      const dataFim = new Date(this.filtroDataFim);
      dataFim.setHours(23, 59, 59, 999); // Fim do dia
      filtradas = filtradas.filter(t => {
        if (!t.dataTransacao) return false;
        const dataTransacao = new Date(t.dataTransacao);
        return dataTransacao <= dataFim;
      });
    }

    // Atualizar indicador do mês baseado nos filtros
    this.atualizarIndicadorMes();

    // Atualizar formatação das datas nos inputs
    this.atualizarFormatacaoDatas();
    
    // Forçar atualização do indicador após um pequeno delay
    setTimeout(() => {
      this.atualizarIndicadorMes();
    }, 100);

    this.totalTransacoes = filtradas.length;
    this.aplicarPaginacao(filtradas);
    
    console.log(`🔍 Filtros aplicados: ${filtradas.length} transações encontradas`);
  }

  aplicarPaginacao(transacoes: Transacao[]): void {
    const inicio = this.paginaAtual * this.tamanhoPagina;
    const fim = inicio + this.tamanhoPagina;
    this.transacoesFiltradas = transacoes.slice(inicio, fim);
  }

  limparFiltros(): void {
    this.filtroTipo = '';
    this.filtroDataInicio = null;
    this.filtroDataFim = null;
    this.aplicarFiltros();
  }

  ordenar(sort: Sort): void {
    let data = [...this.transacoes];
    
    // Aplica filtros primeiro
    if (this.filtroTipo) {
      data = data.filter(t => t.tipoTransacao === this.filtroTipo);
    }
    if (this.filtroDataInicio) {
      const dataInicio = new Date(this.filtroDataInicio);
      dataInicio.setHours(0, 0, 0, 0);
      data = data.filter(t => {
        if (!t.dataTransacao) return false;
        const dataTransacao = new Date(t.dataTransacao);
        dataTransacao.setHours(0, 0, 0, 0);
        return dataTransacao >= dataInicio;
      });
    }
    if (this.filtroDataFim) {
      const dataFim = new Date(this.filtroDataFim);
      dataFim.setHours(23, 59, 59, 999);
      data = data.filter(t => {
        if (!t.dataTransacao) return false;
        const dataTransacao = new Date(t.dataTransacao);
        return dataTransacao <= dataFim;
      });
    }

    // Aplica ordenação
    if (sort.active && sort.direction !== '') {
      data = data.sort((a, b) => {
      const isAsc = sort.direction === 'asc';
      switch (sort.active) {
        case 'descricao': return this.compare(a.descricao, b.descricao, isAsc);
        case 'valor': return this.compare(a.valor, b.valor, isAsc);
        case 'data': return this.compare(new Date(a.dataTransacao!), new Date(b.dataTransacao!), isAsc);
        default: return 0;
      }
    });
    }

    this.totalTransacoes = data.length;
    this.aplicarPaginacao(data);
  }

  compare(a: any, b: any, isAsc: boolean): number {
    return (a < b ? -1 : 1) * (isAsc ? 1 : -1);
  }

  paginar(event: PageEvent): void {
    this.paginaAtual = event.pageIndex;
    this.tamanhoPagina = event.pageSize;
    this.aplicarFiltros(); // Reaplica os filtros com a nova paginação
  }

  abrirDialogoTransacao(transacao?: Transacao): void {
    this.transacaoEditando = transacao || null;
    
    if (transacao) {
      this.transacaoForm.patchValue({
        descricao: transacao.descricao,
        valor: transacao.valor,
        tipoTransacao: transacao.tipoTransacao,
        dataTransacao: transacao.dataTransacao,
        categoriaId: transacao.categoriaId || ''
      });
    } else {
      this.transacaoForm.reset({
        tipoTransacao: TipoTransacao.DESPESA,
        dataTransacao: new Date()
      });
    }

    const dialogRef = this.dialog.open(this.dialogoTransacao, {
      width: '500px',
      data: transacao || {}
    });

    dialogRef.afterClosed().subscribe(() => {
      this.transacaoEditando = null;
      this.transacaoForm.reset();
    });
  }

  salvarTransacao(): void {
    if (this.transacaoForm.valid) {
      const transacao: Transacao = this.transacaoForm.value;
      
      if (this.transacaoEditando) {
        this.transacaoService.atualizarTransacao(this.transacaoEditando.id!, transacao).subscribe({
          next: () => {
            this.snackBar.open('Transação atualizada com sucesso!', 'Fechar', { duration: 3000 });
            this.carregarTransacoes();
            this.dialog.closeAll();
          },
          error: (error) => {
            console.error('Erro ao atualizar transação:', error);
            this.snackBar.open('Erro ao atualizar transação', 'Fechar', { duration: 3000 });
          }
        });
      } else {
        this.transacaoService.criarTransacao(transacao).subscribe({
          next: () => {
            this.snackBar.open('Transação criada com sucesso!', 'Fechar', { duration: 3000 });
            this.carregarTransacoes();
            this.dialog.closeAll();
          },
          error: (error) => {
            console.error('Erro ao criar transação:', error);
            this.snackBar.open('Erro ao criar transação', 'Fechar', { duration: 3000 });
          }
        });
      }
    }
  }

  editarTransacao(transacao: Transacao): void {
    this.abrirDialogoTransacao(transacao);
  }

  deletarTransacao(id: number): void {
    if (confirm('Tem certeza que deseja excluir esta transação?')) {
      this.transacaoService.deletarTransacao(id).subscribe({
        next: () => {
          this.snackBar.open('Transação excluída com sucesso!', 'Fechar', { duration: 3000 });
          this.carregarTransacoes();
        },
        error: (error) => {
          console.error('Erro ao excluir transação:', error);
          this.snackBar.open('Erro ao excluir transação', 'Fechar', { duration: 3000 });
        }
      });
    }
  }

  obterNomeMesAtual(): string {
    // Se há filtros de data, mostrar todos os meses filtrados
    if (this.filtroDataInicio && this.filtroDataFim) {
      return this.obterMesesFiltrados();
    }
    
    // Senão, usar o mês atual
    const agora = new Date();
    const meses = [
      'Janeiro', 'Fevereiro', 'Março', 'Abril', 'Maio', 'Junho',
      'Julho', 'Agosto', 'Setembro', 'Outubro', 'Novembro', 'Dezembro'
    ];
    return `${meses[agora.getMonth()]} ${agora.getFullYear()}`;
  }

  obterMesesFiltrados(): string {
    if (!this.filtroDataInicio || !this.filtroDataFim) {
      return '';
    }

    const dataInicio = new Date(this.filtroDataInicio);
    const dataFim = new Date(this.filtroDataFim);
    
    const meses = [
      'Janeiro', 'Fevereiro', 'Março', 'Abril', 'Maio', 'Junho',
      'Julho', 'Agosto', 'Setembro', 'Outubro', 'Novembro', 'Dezembro'
    ];

    // Se é o mesmo mês e ano
    if (dataInicio.getMonth() === dataFim.getMonth() && dataInicio.getFullYear() === dataFim.getFullYear()) {
      return `${meses[dataInicio.getMonth()]} ${dataInicio.getFullYear()}`;
    }

    // Se é o mesmo ano
    if (dataInicio.getFullYear() === dataFim.getFullYear()) {
      const mesInicio = dataInicio.getMonth();
      const mesFim = dataFim.getMonth();
      
      // Se são meses consecutivos (ex: Julho e Agosto)
      if (mesFim - mesInicio === 1) {
        return `${meses[mesInicio]} e ${meses[mesFim]} ${dataInicio.getFullYear()}`;
      }
      
      // Se são 3+ meses, usar "a" (ex: Julho a Setembro)
      if (mesFim - mesInicio >= 2) {
        return `${meses[mesInicio]} a ${meses[mesFim]} ${dataInicio.getFullYear()}`;
      }
      
      // Fallback para outros casos
      return `${meses[mesInicio]} e ${meses[mesFim]} ${dataInicio.getFullYear()}`;
    }

    // Se são anos diferentes
    return `${meses[dataInicio.getMonth()]} ${dataInicio.getFullYear()} a ${meses[dataFim.getMonth()]} ${dataFim.getFullYear()}`;
  }

  formatarDataParaExibicao(data: Date | null): string {
    if (!data) return '';
    return data.toLocaleDateString('pt-BR');
  }

  formatarDataBrasileira(data: Date | null): string {
    if (!data) return '';
    
    // Forçar formatação brasileira dd/mm/yyyy
    const dia = data.getDate().toString().padStart(2, '0');
    const mes = (data.getMonth() + 1).toString().padStart(2, '0');
    const ano = data.getFullYear();
    
    return `${dia}/${mes}/${ano}`;
  }

  atualizarIndicadorMes(): void {
    // Força a atualização do indicador do mês
    console.log(`📅 Indicador do mês atualizado para: ${this.obterNomeMesAtual()}`);
  }

  atualizarFormatacaoDatas(): void {
    // Atualizar formatação das datas nos inputs após mudanças
    setTimeout(() => {
      const inputs = document.querySelectorAll('input[matDatepicker]');
      inputs.forEach((input: any) => {
        if (input) {
          // Se o input tem valor, formatar
          if (input.value) {
            // Verificar se está no formato americano (m/d/yyyy)
            const isAmericanFormat = /^\d{1,2}\/\d{1,2}\/\d{4}$/.test(input.value) && 
                                   !/^\d{2}\/\d{2}\/\d{4}$/.test(input.value);
            
            if (isAmericanFormat) {
              // Converter de formato americano para brasileiro
              const parts = input.value.split('/');
              if (parts.length === 3) {
                const [mes, dia, ano] = parts;
                const formatted = `${dia.padStart(2, '0')}/${mes.padStart(2, '0')}/${ano}`;
                input.value = formatted;
                console.log(`📅 Data convertida de americano para brasileiro: ${input.value} → ${formatted}`);
              }
            } else {
              // Usar formatação brasileira padrão
              const date = new Date(input.value);
              if (!isNaN(date.getTime())) {
                const formatted = this.formatarDataBrasileira(date);
                if (input.value !== formatted) {
                  input.value = formatted;
                  console.log(`📅 Data formatada: ${input.value} → ${formatted}`);
                }
              }
            }
          }
          
          // Adicionar listener para mudanças futuras
          if (!input._formatoBrasileiroAplicado) {
            input.addEventListener('change', () => {
              if (input.value) {
                const date = new Date(input.value);
                if (!isNaN(date.getTime())) {
                  const formatted = this.formatarDataBrasileira(date);
                  if (input.value !== formatted) {
                    input.value = formatted;
                  }
                }
              }
            });
            input._formatoBrasileiroAplicado = true;
          }
        }
      });
    }, 50);
  }
}
