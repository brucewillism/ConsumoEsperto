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
        <h1>Transações</h1>
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

            <mat-form-field appearance="outline">
              <mat-label>Data Início</mat-label>
              <input matInput [matDatepicker]="dataInicioPicker" [(ngModel)]="filtroDataInicio" (dateChange)="aplicarFiltros()">
              <mat-datepicker-toggle matSuffix [for]="dataInicioPicker"></mat-datepicker-toggle>
              <mat-datepicker #dataInicioPicker></mat-datepicker>
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>Data Fim</mat-label>
              <input matInput [matDatepicker]="dataFimPicker" [(ngModel)]="filtroDataFim" (dateChange)="aplicarFiltros()">
              <mat-datepicker-toggle matSuffix [for]="dataFimPicker"></mat-datepicker-toggle>
              <mat-datepicker #dataFimPicker></mat-datepicker>
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

      h1 {
        margin: 0;
        color: var(--text-primary);
        font-size: 2.5rem;
        font-weight: 700;
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

      button {
        min-width: 120px;
      }
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
  }

  carregarTransacoes(): void {
    this.transacaoService.buscarPorUsuario().subscribe({
      next: (transacoes) => {
        this.transacoes = transacoes;
        this.totalTransacoes = transacoes.length;
        this.aplicarFiltros();
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

    // Filtro por data
    if (this.filtroDataInicio) {
      filtradas = filtradas.filter(t => 
        t.dataTransacao && new Date(t.dataTransacao) >= this.filtroDataInicio!
      );
    }

    if (this.filtroDataFim) {
      filtradas = filtradas.filter(t => 
        t.dataTransacao && new Date(t.dataTransacao) <= this.filtroDataFim!
      );
    }

    this.transacoesFiltradas = filtradas;
    this.totalTransacoes = filtradas.length;
    this.paginaAtual = 0;
  }

  limparFiltros(): void {
    this.filtroTipo = '';
    this.filtroDataInicio = null;
    this.filtroDataFim = null;
    this.aplicarFiltros();
  }

  ordenar(sort: Sort): void {
    const data = this.transacoesFiltradas.slice();
    if (!sort.active || sort.direction === '') {
      this.transacoesFiltradas = data;
      return;
    }

    this.transacoesFiltradas = data.sort((a, b) => {
      const isAsc = sort.direction === 'asc';
      switch (sort.active) {
        case 'descricao': return this.compare(a.descricao, b.descricao, isAsc);
        case 'valor': return this.compare(a.valor, b.valor, isAsc);
        case 'data': return this.compare(new Date(a.dataTransacao!), new Date(b.dataTransacao!), isAsc);
        default: return 0;
      }
    });
  }

  compare(a: any, b: any, isAsc: boolean): number {
    return (a < b ? -1 : 1) * (isAsc ? 1 : -1);
  }

  paginar(event: PageEvent): void {
    this.paginaAtual = event.pageIndex;
    this.tamanhoPagina = event.pageSize;
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
}
