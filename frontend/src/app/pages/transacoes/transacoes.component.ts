import { Component, OnInit, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog.component';
import { ParcelamentoDeleteChoiceDialogComponent } from '../../shared/parcelamento-delete-choice-dialog.component';
import { FinancaAlteracaoService } from '../../services/financa-alteracao.service';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatNativeDateModule } from '@angular/material/core';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { PageEvent } from '@angular/material/paginator';
import { Sort } from '@angular/material/sort';
import { Categoria } from '../../models/categoria.model';
import { ModoParcelamentoDelete, StatusConferencia, TipoTransacao, Transacao } from '../../models/transacao.model';
import { CategoriaService } from '../../services/categoria.service';
import {
  OrdenacaoTransacao,
  TransacaoFiltros,
  TransacaoService
} from '../../services/transacao.service';
import { FiltroTransacaoComponent } from './components/filtro-transacao/filtro-transacao.component';
import { ListaTransacaoComponent } from './components/lista-transacao/lista-transacao.component';

@Component({
  selector: 'app-transacoes',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    FormsModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatDatepickerModule,
    MatNativeDateModule,
    FiltroTransacaoComponent,
    ListaTransacaoComponent
  ],
  templateUrl: './transacoes.component.html',
  styleUrls: ['./transacoes.component.scss']
})
export class TransacoesComponent implements OnInit {
  @ViewChild('dialogoTransacao') dialogoTransacao: unknown;

  transacoes: Transacao[] = [];
  transacoesFiltradas: Transacao[] = [];
  categorias: Categoria[] = [];
  transacaoForm: FormGroup;
  transacaoEditando: Transacao | null = null;

  filtroTipo: '' | 'RECEITA' | 'DESPESA' = '';
  filtroDataInicio: Date | null = null;
  filtroDataFim: Date | null = null;
  ordenacaoAtual: OrdenacaoTransacao = { active: '', direction: '' };

  totalTransacoes = 0;
  tamanhoPagina = 10;
  paginaAtual = 0;
  colunasExibidas = ['descricao', 'valor', 'tipo', 'categoria', 'data', 'acoes'];

  loadingTransacoes = false;
  carregandoCategorias = false;
  salvandoTransacao = false;
  /** IDs com confirmação em andamento (loading por linha) */
  confirmandoTransacaoIds: number[] = [];

  tipoTransacao = TipoTransacao;

  constructor(
    private readonly fb: FormBuilder,
    private readonly transacaoService: TransacaoService,
    private readonly categoriaService: CategoriaService,
    private readonly dialog: MatDialog,
    private readonly snackBar: MatSnackBar,
    private readonly financaAlteracao: FinancaAlteracaoService
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
    this.definirDatasPadraoMesAtual();
    this.carregarTransacoes();
    this.carregarCategorias();
  }

  abrirDialogoTransacao(transacao?: Transacao): void {
    this.transacaoEditando = transacao ?? null;

    if (transacao) {
      this.transacaoForm.patchValue({
        descricao: transacao.descricao,
        valor: transacao.valor,
        tipoTransacao: transacao.tipoTransacao,
        dataTransacao: transacao.dataTransacao ? new Date(transacao.dataTransacao) : new Date(),
        categoriaId: transacao.categoriaId ?? ''
      });
    } else {
      this.transacaoForm.reset({
        tipoTransacao: TipoTransacao.DESPESA,
        dataTransacao: new Date(),
        categoriaId: ''
      });
    }

    const ref = this.dialog.open(this.dialogoTransacao as any, { width: '520px' });
    ref.afterClosed().subscribe(() => {
      this.transacaoEditando = null;
      this.transacaoForm.reset({
        tipoTransacao: TipoTransacao.DESPESA,
        dataTransacao: new Date(),
        categoriaId: ''
      });
    });
  }

  salvarTransacao(): void {
    if (this.transacaoForm.invalid) {
      return;
    }

    const transacao: Transacao = this.transacaoForm.value;
    this.salvandoTransacao = true;

    const request$ = this.transacaoEditando?.id
      ? this.transacaoService.atualizarTransacao(this.transacaoEditando.id, transacao)
      : this.transacaoService.criarTransacao(transacao);

    request$.subscribe({
      next: () => {
        this.salvandoTransacao = false;
        this.dialog.closeAll();
        this.snackBar.open(
          this.transacaoEditando ? 'Transação atualizada com sucesso!' : 'Transação criada com sucesso!',
          'Fechar',
          { duration: 3000, panelClass: ['success-snackbar'] }
        );
        this.financaAlteracao.notificar();
        this.carregarTransacoes();
      },
      error: () => {
        this.salvandoTransacao = false;
        this.snackBar.open('Erro ao salvar transação', 'Fechar', { duration: 3000, panelClass: ['error-snackbar'] });
      }
    });
  }

  editarTransacao(transacao: Transacao): void {
    this.abrirDialogoTransacao(transacao);
  }

  confirmarTransacao(id: number): void {
    if (this.confirmandoTransacaoIds.includes(id)) {
      return;
    }
    this.confirmandoTransacaoIds = [...this.confirmandoTransacaoIds, id];
    this.transacaoService.confirmarTransacao(id).subscribe({
      next: (atualizada) => {
        const alvo = this.transacoes.find((t) => t.id === id);
        if (alvo) {
          alvo.statusConferencia = atualizada.statusConferencia ?? StatusConferencia.CONFIRMADA;
        }
        this.confirmandoTransacaoIds = this.confirmandoTransacaoIds.filter((i) => i !== id);
        this.recalcularLista();
        this.financaAlteracao.notificar();
        this.snackBar.open('Transação confirmada.', 'Fechar', { duration: 2500, panelClass: ['success-snackbar'] });
      },
      error: () => {
        this.confirmandoTransacaoIds = this.confirmandoTransacaoIds.filter((i) => i !== id);
        this.snackBar.open('Não foi possível confirmar. Tente novamente.', 'Fechar', {
          duration: 3500,
          panelClass: ['error-snackbar']
        });
      }
    });
  }

  deletarTransacao(transacao: Transacao): void {
    const id = transacao.id;
    if (id == null) {
      return;
    }

    const parcelada =
      !!transacao.grupoParcelaId &&
      transacao.totalParcelas != null &&
      transacao.totalParcelas > 1 &&
      transacao.parcelaAtual != null;

    if (parcelada) {
      const base =
        (transacao.descricao || '')
          .replace(/\s*\(\d+\/\d+\)\s*$/, '')
          .trim() || 'Compra';
      this.dialog
        .open(ParcelamentoDeleteChoiceDialogComponent, {
          width: '440px',
          data: {
            descricao: base,
            parcelaAtual: transacao.parcelaAtual!,
            totalParcelas: transacao.totalParcelas!
          }
        })
        .afterClosed()
        .subscribe((modo) => {
          if (modo === false || modo == null) {
            return;
          }
          this.confirmarEExcluirTransacao(id, modo as ModoParcelamentoDelete);
        });
      return;
    }

    const ref = this.dialog.open(ConfirmDialogComponent, {
      width: '400px',
      data: {
        title: 'Excluir transação',
        message: 'Tem certeza que deseja excluir esta transação?',
        confirmLabel: 'Excluir',
        destructive: true
      }
    });
    ref.afterClosed().subscribe((ok) => {
      if (!ok) {
        return;
      }
      this.confirmarEExcluirTransacao(id);
    });
  }

  private confirmarEExcluirTransacao(id: number, modoParcelamento?: ModoParcelamentoDelete): void {
    this.salvandoTransacao = true;
    this.transacaoService.deletarTransacao(id, modoParcelamento).subscribe({
      next: () => {
        this.salvandoTransacao = false;
        this.snackBar.open('Transação excluída com sucesso!', 'Fechar', { duration: 3000, panelClass: ['success-snackbar'] });
        this.financaAlteracao.notificar();
        this.carregarTransacoes();
      },
      error: () => {
        this.salvandoTransacao = false;
        this.snackBar.open('Erro ao excluir transação', 'Fechar', { duration: 3000, panelClass: ['error-snackbar'] });
      }
    });
  }

  atualizarTipo(tipo: '' | 'RECEITA' | 'DESPESA'): void {
    this.filtroTipo = tipo;
    this.paginaAtual = 0;
    this.recalcularLista();
  }

  atualizarDataInicio(data: Date | null): void {
    this.filtroDataInicio = data;
    this.paginaAtual = 0;
    this.recalcularLista();
  }

  atualizarDataFim(data: Date | null): void {
    this.filtroDataFim = data;
    this.paginaAtual = 0;
    this.recalcularLista();
  }

  limparFiltros(): void {
    this.filtroTipo = '';
    this.filtroDataInicio = null;
    this.filtroDataFim = null;
    this.paginaAtual = 0;
    this.recalcularLista();
  }

  onSortChange(sort: Sort): void {
    this.ordenacaoAtual = {
      active: sort.active,
      direction: (sort.direction || '') as OrdenacaoTransacao['direction']
    };
    this.paginaAtual = 0;
    this.recalcularLista();
  }

  onPageChange(event: PageEvent): void {
    this.paginaAtual = event.pageIndex;
    this.tamanhoPagina = event.pageSize;
    this.recalcularLista();
  }

  private carregarTransacoes(): void {
    this.loadingTransacoes = true;
    this.transacaoService.buscarPorUsuario().subscribe({
      next: (transacoes) => {
        this.transacoes = transacoes ?? [];
        this.loadingTransacoes = false;
        this.recalcularLista();
      },
      error: () => {
        this.loadingTransacoes = false;
        this.snackBar.open('Erro ao carregar transações', 'Fechar', { duration: 3000, panelClass: ['error-snackbar'] });
      }
    });
  }

  private carregarCategorias(): void {
    this.carregandoCategorias = true;
    this.categoriaService.buscarPorUsuario().subscribe({
      next: (categorias) => {
        this.categorias = categorias ?? [];
        this.carregandoCategorias = false;
      },
      error: () => {
        this.categorias = [];
        this.carregandoCategorias = false;
      }
    });
  }

  private recalcularLista(): void {
    const filtros: TransacaoFiltros = {
      tipo: this.filtroTipo,
      dataInicio: this.filtroDataInicio,
      dataFim: this.filtroDataFim
    };

    const filtradas = this.transacaoService.filtrarTransacoes(this.transacoes, filtros);
    const ordenadas = this.transacaoService.ordenarTransacoes(filtradas, this.ordenacaoAtual);
    this.totalTransacoes = ordenadas.length;
    this.transacoesFiltradas = this.transacaoService.paginarTransacoes(
      ordenadas,
      this.paginaAtual,
      this.tamanhoPagina
    );
  }

  private definirDatasPadraoMesAtual(): void {
    const hoje = new Date();
    this.filtroDataInicio = new Date(hoje.getFullYear(), hoje.getMonth(), 1);
    this.filtroDataFim = new Date(hoje.getFullYear(), hoje.getMonth() + 1, 0);
  }

  obterNomePeriodoAtual(): string {
    if (this.filtroDataInicio && this.filtroDataFim) {
      const inicio = this.filtroDataInicio.toLocaleDateString('pt-BR');
      const fim = this.filtroDataFim.toLocaleDateString('pt-BR');
      return `${inicio} até ${fim}`;
    }
    const hoje = new Date();
    return hoje.toLocaleDateString('pt-BR', { month: 'long', year: 'numeric' });
  }
}
