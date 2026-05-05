import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBarModule, MatSnackBar } from '@angular/material/snack-bar';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators, FormsModule } from '@angular/forms';
import { Subject, takeUntil, catchError, of, forkJoin } from 'rxjs';

import { CreditCardInvoice } from '../../models/credit-card-invoice.model';
import { CartaoCredito } from '../../models/cartao-credito.model';
import { FaturaService } from '../../services/fatura.service';
import { CartaoCreditoService } from '../../services/cartao-credito.service';
import { FaturaMesGrupo } from './faturas-mes-grupo.model';

@Component({
  selector: 'app-faturas',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatChipsModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    MatDialogModule,
    ReactiveFormsModule,
    FormsModule
  ],
  templateUrl: './faturas.component.html',
  styleUrls: ['./faturas.component.scss']
})
export class FaturasComponent implements OnInit, OnDestroy {
  faturas: CreditCardInvoice[] = [];
  faturasFiltradas: CreditCardInvoice[] = [];
  /** Linha do tempo: mês/ano ascendente (atual → futuro). */
  gruposTimeline: FaturaMesGrupo[] = [];
  cartoes: CartaoCredito[] = [];
  /** '' = todos os cartões; senão id do cartão como string. */
  filtroCartaoId = '';
  loading = false;
  acaoEmAndamento = false;
  faturaProcessandoId: string | null = null;
  showForm = false;
  
  // Filtros
  filtroStatus = '';
  filtroBanco = '';
  filtroMes = '';
  
  // Formulário
  novaFaturaForm: FormGroup;
  
  // Resumos
  totalFaturas = 0;
  totalFaturasPagas = 0;
  totalFaturasPendentes = 0;
  totalFaturasVencidas = 0;
  
  private destroy$ = new Subject<void>();
  Math = Math;

  constructor(
    private faturaService: FaturaService,
    private cartaoCreditoService: CartaoCreditoService,
    private dialog: MatDialog,
    private snackBar: MatSnackBar,
    private fb: FormBuilder
  ) {
    this.novaFaturaForm = this.fb.group({
      banco: ['', Validators.required],
      valor: ['', [Validators.required, Validators.min(0.01)]],
      vencimento: ['', Validators.required],
      fechamento: ['', Validators.required],
      status: ['PENDING', Validators.required]
    });
  }

  ngOnInit(): void {
    this.loadData();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  public loadData(): void {
    this.loading = true;

    forkJoin({
      faturas: this.faturaService.getFaturasCartao().pipe(
        takeUntil(this.destroy$),
        catchError((error) => {
          console.error('Erro ao carregar faturas do backend:', error);
          this.snackBar.open('Erro ao carregar faturas. Verifique sua conexão.', 'Fechar', {
            duration: 3000,
            panelClass: ['error-snackbar']
          });
          return of<CreditCardInvoice[]>([]);
        })
      ),
      cartoes: this.cartaoCreditoService.getCartoes().pipe(
        takeUntil(this.destroy$),
        catchError(() => of<CartaoCredito[]>([]))
      )
    }).subscribe({
      next: ({ faturas, cartoes }) => {
        this.faturas = faturas || [];
        this.cartoes = (cartoes || []).filter((c) => c.id != null && c.ativo !== false);
        this.aplicarFiltros();
        this.calcularResumos();
        this.loading = false;
      },
      error: () => {
        this.faturas = [];
        this.cartoes = [];
        this.aplicarFiltros();
        this.calcularResumos();
        this.loading = false;
      }
    });
  }

  carregarFaturas(): void {
    this.aplicarFiltros();
    this.calcularResumos();
  }

  /** Resumo só das faturas exibidas na timeline (respeita filtros). */
  totalComprometidoPrevistoNoMes(grupo: FaturaMesGrupo): number {
    return grupo.faturas
      .filter((f) => f.status === 'PREVISTA')
      .reduce((s, f) => s + (Number(f.amount) || 0), 0);
  }

  adicionarFatura(): void {
    if (this.novaFaturaForm.valid) {
      this.acaoEmAndamento = true;
      const formValue = this.novaFaturaForm.value;
      const novaFatura: CreditCardInvoice = {
        id: Date.now().toString(),
        cardId: formValue.banco + '-card',
        bankName: formValue.banco,
        amount: formValue.valor,
        dueDate: formValue.vencimento,
        closingDate: formValue.fechamento,
        status: formValue.status,
        transactions: []
      };

      // Tenta salvar no backend primeiro
      this.faturaService.criarFaturaCartao(novaFatura)
        .pipe(
          takeUntil(this.destroy$),
          catchError(error => {
            console.warn('Erro ao salvar no backend, salvando localmente:', error);
            // Se falhar, adiciona localmente
            this.faturas.unshift(novaFatura);
            return of(novaFatura);
          })
        )
        .subscribe({
          next: (fatura) => {
            if (fatura.id !== novaFatura.id) {
              // Fatura foi salva no backend, atualiza a lista
              this.faturas.unshift(fatura);
            }
            this.snackBar.open('Fatura adicionada com sucesso!', 'Fechar', {
              duration: 3000,
              panelClass: ['success-snackbar']
            });
            this.novaFaturaForm.reset();
            this.showForm = false;
            this.acaoEmAndamento = false;
            this.carregarFaturas();
          },
          error: (error) => {
            console.error('Erro ao adicionar fatura:', error);
            this.acaoEmAndamento = false;
            this.snackBar.open('Erro ao adicionar fatura. Tente novamente.', 'Fechar', {
            duration: 3000,
            panelClass: ['error-snackbar']
          });
          }
        });
    }
  }

  editarFatura(fatura: CreditCardInvoice): void {
    // Implementar edição de fatura
    this.snackBar.open('Funcionalidade de edição em desenvolvimento', 'Fechar', {
      duration: 3000,
      panelClass: ['warning-snackbar']
    });
  }

  excluirFatura(fatura: CreditCardInvoice): void {
    if (confirm(`Tem certeza que deseja excluir a fatura ${fatura.bankName}?`)) {
      this.acaoEmAndamento = true;
      this.faturaProcessandoId = fatura.id;
      // Tenta excluir do backend primeiro
      this.faturaService.excluirFaturaCartao(fatura)
        .pipe(
          takeUntil(this.destroy$),
          catchError(error => {
            console.warn('Erro ao excluir do backend, removendo localmente:', error);
            // Se falhar, remove localmente
            this.faturas = this.faturas.filter(f => f.id !== fatura.id);
            return of(void 0);
          })
        )
        .subscribe({
          next: () => {
            this.snackBar.open('Fatura excluída com sucesso!', 'Fechar', {
            duration: 3000,
            panelClass: ['success-snackbar']
          });
            this.acaoEmAndamento = false;
            this.faturaProcessandoId = null;
            this.carregarFaturas();
          },
          error: (error) => {
            console.error('Erro ao excluir fatura:', error);
            this.acaoEmAndamento = false;
            this.faturaProcessandoId = null;
            this.snackBar.open('Erro ao excluir fatura. Tente novamente.', 'Fechar', {
            duration: 3000,
            panelClass: ['error-snackbar']
          });
          }
        });
    }
  }

  marcarComoPaga(fatura: CreditCardInvoice): void {
    this.acaoEmAndamento = true;
    this.faturaProcessandoId = fatura.id;
    const faturaAtualizada = { ...fatura, status: 'PAID' as const };

    // Tenta atualizar no backend primeiro
    this.faturaService.atualizarFaturaCartao(faturaAtualizada)
      .pipe(
        takeUntil(this.destroy$),
        catchError(error => {
          console.warn('Erro ao atualizar no backend, atualizando localmente:', error);
          // Se falhar, atualiza localmente
          const index = this.faturas.findIndex(f => f.id === fatura.id);
          if (index !== -1) {
            this.faturas[index] = faturaAtualizada;
          }
          return of(faturaAtualizada);
        })
      )
      .subscribe({
        next: (fatura) => {
          this.snackBar.open('Fatura marcada como paga!', 'Fechar', {
          duration: 3000,
          panelClass: ['success-snackbar']
        });
          this.acaoEmAndamento = false;
          this.faturaProcessandoId = null;
          this.carregarFaturas();
        },
        error: (error) => {
          console.error('Erro ao marcar fatura como paga:', error);
          this.acaoEmAndamento = false;
          this.faturaProcessandoId = null;
          this.snackBar.open('Erro ao atualizar fatura. Tente novamente.', 'Fechar', {
          duration: 3000,
          panelClass: ['error-snackbar']
        });
        }
      });
  }

  visualizarTransacoes(fatura: CreditCardInvoice): void {
    // Implementar visualização de transações
    this.snackBar.open('Funcionalidade de transações em desenvolvimento', 'Fechar', {
      duration: 3000,
      panelClass: ['warning-snackbar']
    });
  }

  aplicarFiltros(): void {
    let faturasFiltradas = [...this.faturas];

    if (this.filtroCartaoId) {
      faturasFiltradas = faturasFiltradas.filter(
        (f) => String(f.cardId ?? '') === String(this.filtroCartaoId)
      );
    }

    if (this.filtroStatus) {
      faturasFiltradas = faturasFiltradas.filter(f => f.status === this.filtroStatus);
    }

    if (this.filtroBanco) {
      faturasFiltradas = faturasFiltradas.filter(f => f.bankName === this.filtroBanco);
    }

    if (this.filtroMes) {
      faturasFiltradas = faturasFiltradas.filter(f => {
        const dataFatura = new Date(f.closingDate);
        const mesAno = `${dataFatura.getFullYear()}-${String(dataFatura.getMonth() + 1).padStart(2, '0')}`;
        return mesAno === this.filtroMes;
      });
    }

    faturasFiltradas.sort((a, b) => this.compararCronologico(a, b));
    this.faturasFiltradas = faturasFiltradas;
    this.reconstruirGruposTimeline();
  }

  onFiltroCartaoChange(): void {
    this.aplicarFiltros();
    this.calcularResumos();
  }

  onFiltrosSecundariosChange(): void {
    this.aplicarFiltros();
    this.calcularResumos();
  }

  limparFiltros(): void {
    this.filtroCartaoId = '';
    this.filtroStatus = '';
    this.filtroBanco = '';
    this.filtroMes = '';
    this.aplicarFiltros();
    this.calcularResumos();
  }

  /** Primeiro dia do mês de fechamento (eixo da timeline). */
  private dataMesReferencia(f: CreditCardInvoice): Date {
    const d = new Date(f.closingDate);
    return new Date(d.getFullYear(), d.getMonth(), 1);
  }

  private compararCronologico(a: CreditCardInvoice, b: CreditCardInvoice): number {
    const tA = this.dataMesReferencia(a).getTime();
    const tB = this.dataMesReferencia(b).getTime();
    if (tA !== tB) {
      return tA - tB;
    }
    return new Date(a.dueDate).getTime() - new Date(b.dueDate).getTime();
  }

  private reconstruirGruposTimeline(): void {
    const nomesMes = [
      'JANEIRO', 'FEVEREIRO', 'MARÇO', 'ABRIL', 'MAIO', 'JUNHO',
      'JULHO', 'AGOSTO', 'SETEMBRO', 'OUTUBRO', 'NOVEMBRO', 'DEZEMBRO'
    ];
    const mapa = new Map<string, CreditCardInvoice[]>();
    for (const f of this.faturasFiltradas) {
      const ref = this.dataMesReferencia(f);
      const chave = `${ref.getFullYear()}-${String(ref.getMonth() + 1).padStart(2, '0')}`;
      if (!mapa.has(chave)) {
        mapa.set(chave, []);
      }
      mapa.get(chave)!.push(f);
    }
    for (const lista of mapa.values()) {
      lista.sort((a, b) => new Date(a.dueDate).getTime() - new Date(b.dueDate).getTime());
    }
    const chavesOrdenadas = [...mapa.keys()].sort((a, b) => a.localeCompare(b));
    this.gruposTimeline = chavesOrdenadas.map((chave) => {
      const [ys, ms] = chave.split('-');
      const ano = Number(ys);
      const mes = Number(ms);
      const rotuloMes = `${nomesMes[mes - 1] ?? 'MÊS'} ${ano}`;
      return {
        chave,
        ano,
        mes,
        rotuloMes,
        faturas: mapa.get(chave)!
      };
    });
  }

  trackByGrupo(_index: number, g: FaturaMesGrupo): string {
    return g.chave;
  }

  trackByFaturaId(_index: number, f: CreditCardInvoice): string {
    return f.id;
  }

  trackByCartaoId(_index: number, c: CartaoCredito): string {
    return String(c.id ?? _index);
  }

  cartaoOpcaoValue(c: CartaoCredito): string {
    return String(c.id ?? '');
  }

  rotuloCartaoSelect(c: CartaoCredito): string {
    const nome = (c.nome || '').trim() || 'Cartão';
    const banco = (c.banco || '').trim();
    return banco ? `${nome} · ${banco}` : nome;
  }

  contagemTransacoes(f: CreditCardInvoice): number {
    return f.transactions?.length ?? 0;
  }

  calcularResumos(): void {
    this.totalFaturas = this.faturasFiltradas.reduce((total, f) => total + f.amount, 0);
    this.totalFaturasPagas = this.faturasFiltradas
      .filter(f => f.status === 'PAID')
      .reduce((total, f) => total + f.amount, 0);
    this.totalFaturasPendentes = this.faturasFiltradas
      .filter(f => f.status === 'PENDING' || f.status === 'PREVISTA')
      .reduce((total, f) => total + f.amount, 0);
    this.totalFaturasVencidas = this.faturasFiltradas
      .filter(f => f.status === 'OVERDUE')
      .reduce((total, f) => total + f.amount, 0);
  }

  // Métodos auxiliares
  formatarMoeda(valor: number): string {
    return new Intl.NumberFormat('pt-BR', {
      style: 'currency',
      currency: 'BRL'
    }).format(valor);
  }

  formatarData(data: Date): string {
    return new Intl.DateTimeFormat('pt-BR').format(data);
  }

  getDiasVencimento(fatura: CreditCardInvoice): number {
    const hoje = new Date();
    const vencimento = new Date(fatura.dueDate);
    const diffTime = vencimento.getTime() - hoje.getTime();
    return Math.ceil(diffTime / (1000 * 60 * 60 * 24));
  }

  isVencendo(fatura: CreditCardInvoice): boolean {
    if (fatura.status === 'PREVISTA') {
      return false;
    }
    const dias = this.getDiasVencimento(fatura);
    return dias >= 0 && dias <= 7;
  }

  isVencida(fatura: CreditCardInvoice): boolean {
    if (fatura.status === 'PREVISTA') {
      return false;
    }
    return this.getDiasVencimento(fatura) < 0;
  }

  getStatusColor(status: string): string {
    switch (status) {
      case 'PAID': return 'primary';
      case 'PENDING': return 'accent';
      case 'OVERDUE': return 'warn';
      case 'PREVISTA': return 'primary';
      default: return 'primary';
    }
  }

  getStatusText(status: string): string {
    switch (status) {
      case 'PAID': return 'Paga';
      case 'PENDING': return 'Pendente';
      case 'OVERDUE': return 'Vencida';
      case 'PREVISTA': return 'Prevista';
      default: return 'Pendente';
    }
  }

  getBancoColor(bankName: string): string {
    const cores = {
      'itau': '#EC7000',
      'nubank': '#8A05BE',
      'inter': '#FF7A00'
    };
    return cores[bankName as keyof typeof cores] || '#666';
  }

  getBancoNome(bankName: string): string {
    const nomes = {
      'itau': 'Itaú',
      'nubank': 'Nubank',
      'inter': 'Banco Inter'
    };
    return nomes[bankName as keyof typeof nomes] || bankName;
  }

  faturaEstaProcessando(fatura: CreditCardInvoice): boolean {
    return this.acaoEmAndamento && this.faturaProcessandoId === fatura.id;
  }
}
