import { Component, DestroyRef, OnInit, OnDestroy, TemplateRef, inject } from '@angular/core';
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
import { FormsModule } from '@angular/forms';
import { Subject, takeUntil, catchError, of, forkJoin } from 'rxjs';

import { CreditCardInvoice } from '../../models/credit-card-invoice.model';
import { BANCOS_BRASIL, getBancoCorBr, getBancoNomeBr } from '../../shared/constants/bancos-brasil';
import { CartaoCredito } from '../../models/cartao-credito.model';
import { FaturaService } from '../../services/fatura.service';
import { CartaoCreditoService } from '../../services/cartao-credito.service';
import { OrcamentoService, Orcamento } from '../../services/orcamento.service';
import { FaturaMesGrupo } from './faturas-mes-grupo.model';
import { PagamentoFaturaModalComponent } from '../../shared/pagamento-fatura-modal/pagamento-fatura-modal.component';
import { NovaFaturaDialogComponent } from '../../shared/nova-fatura-dialog/nova-fatura-dialog.component';
import { FinancaAlteracaoService } from '../../services/financa-alteracao.service';
import { openCeFormDialog } from '../../shared/ce-form-dialog.util';
import { resolveHttpError } from '../../shared/utils/form.utils';
import { PageLoadingComponent } from '../../shared/page-loading/page-loading.component';
import { WhatsappParityHintComponent } from '../../shared/whatsapp-parity-hint/whatsapp-parity-hint.component';
import { ConfirmDialogService } from '../../services/confirm-dialog.service';
import { escutarAlteracoesFinanceiras } from '../../shared/utils/financa-alteracao-refresh.util';

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
    FormsModule,
    PageLoadingComponent,
    WhatsappParityHintComponent,
  ],
  templateUrl: './faturas.component.html',
  styleUrls: ['./faturas.component.scss']
})
export class FaturasComponent implements OnInit, OnDestroy {
  private readonly destroyRef = inject(DestroyRef);

  readonly bancosBrasil = BANCOS_BRASIL;
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
  faturaTransacoesSelecionada: CreditCardInvoice | null = null;
  /** Orçamentos do mês da fatura aberta no modal (scanner). */
  orcamentosModal: Orcamento[] = [];
  
  // Filtros
  filtroStatus = '';
  filtroBanco = '';
  filtroMes = '';
  
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
    private orcamentoService: OrcamentoService,
    private dialog: MatDialog,
    private confirmDialog: ConfirmDialogService,
    private snackBar: MatSnackBar,
    private financaAlteracao: FinancaAlteracaoService
  ) {}

  ngOnInit(): void {
    escutarAlteracoesFinanceiras(
      this.financaAlteracao,
      this.destroyRef,
      () => this.loadData({ silent: true }),
      ['faturas', 'pagamento-fatura']
    );
    this.loadData();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  public loadData(options?: { silent?: boolean }): void {
    const silent = options?.silent === true;
    if (!silent) {
      this.loading = true;
    }

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

  /** Recarrega faturas do backend (usado após pagamento/edição). */
  carregarFaturas(): void {
    this.loadData({ silent: true });
  }

  /** Resumo só das faturas exibidas na timeline (respeita filtros). */
  totalComprometidoPrevistoNoMes(grupo: FaturaMesGrupo): number {
    return grupo.faturas
      .filter((f) => f.status === 'PREVISTA')
      .reduce((s, f) => s + (Number(f.amount) || 0), 0);
  }

  abrirNovaFatura(): void {
    if (!this.cartoes.length) {
      this.snackBar.open(
        'Cadastre um cartão de crédito antes de adicionar uma fatura.',
        'Fechar',
        { duration: 4500, panelClass: ['warning-snackbar'] }
      );
      return;
    }
    openCeFormDialog(this.dialog, NovaFaturaDialogComponent, {
      width: '560px',
      data: { cartoes: this.cartoes },
    })
      .afterClosed()
      .pipe(takeUntil(this.destroy$))
      .subscribe((criada) => {
        if (criada) {
          this.financaAlteracao.notificar('faturas');
          this.loadData({ silent: true });
        }
      });
  }

  editarFatura(fatura: CreditCardInvoice): void {
    // Implementar edição de fatura
    this.snackBar.open('Funcionalidade de edição em desenvolvimento', 'Fechar', {
      duration: 3000,
      panelClass: ['warning-snackbar']
    });
  }

  excluirFatura(fatura: CreditCardInvoice): void {
    if (!fatura.id) {
      this.snackBar.open('Fatura inválida para exclusão.', 'Fechar', {
        duration: 3500,
        panelClass: ['warning-snackbar'],
      });
      return;
    }

    const banco = this.getBancoNome(fatura.bankName);
    this.confirmDialog.ask({
      title: 'Excluir fatura',
      message: `Deseja excluir a fatura de ${banco}? Esta ação não pode ser desfeita.`,
      confirmLabel: 'Excluir',
      destructive: true,
    }).pipe(takeUntil(this.destroy$)).subscribe((ok) => {
      if (!ok) {
        return;
      }

      this.acaoEmAndamento = true;
      this.faturaProcessandoId = fatura.id ?? null;

      this.faturaService
        .excluirFaturaCartao(fatura)
        .pipe(takeUntil(this.destroy$))
        .subscribe({
          next: () => {
            this.removerFaturaDaLista(fatura);
            this.aplicarFiltros();
            this.calcularResumos();
            this.financaAlteracao.notificar('faturas');
            this.snackBar.open('Fatura excluída com sucesso!', 'Fechar', {
              duration: 3000,
              panelClass: ['success-snackbar'],
            });
            this.acaoEmAndamento = false;
            this.faturaProcessandoId = null;
          },
          error: (error) => {
            console.error('Erro ao excluir fatura:', error);
            this.acaoEmAndamento = false;
            this.faturaProcessandoId = null;
            this.snackBar.open(
              resolveHttpError(error, 'Erro ao excluir fatura. Tente novamente.'),
              'Fechar',
              { duration: 4000, panelClass: ['error-snackbar'] }
            );
          },
        });
    });
  }

  private removerFaturaDaLista(fatura: CreditCardInvoice): void {
    this.faturas = this.faturas.filter((f) => !this.mesmoIdFatura(f, fatura));
    if (
      this.faturaTransacoesSelecionada &&
      this.mesmoIdFatura(this.faturaTransacoesSelecionada, fatura)
    ) {
      this.faturaTransacoesSelecionada = null;
    }
  }

  private mesmoIdFatura(a: CreditCardInvoice, b: CreditCardInvoice): boolean {
    if (a.id == null || b.id == null) {
      return false;
    }
    return String(a.id) === String(b.id);
  }

  marcarComoPaga(fatura: CreditCardInvoice): void {
    this.abrirPagamentoFatura(fatura);
  }

  abrirPagamentoFatura(fatura: CreditCardInvoice): void {
    if (fatura.status === 'PAID' || fatura.status === 'PREVISTA') {
      return;
    }
    this.dialog
      .open(PagamentoFaturaModalComponent, {
        width: '520px',
        maxWidth: '96vw',
        maxHeight: '90vh',
        disableClose: true,
        data: { fatura },
        panelClass: 'pagamento-fatura-dialog',
      })
      .afterClosed()
      .subscribe((ok) => {
        if (ok) {
          this.carregarFaturas();
        }
      });
  }

  visualizarTransacoes(fatura: CreditCardInvoice, template: TemplateRef<unknown>): void {
    this.faturaTransacoesSelecionada = fatura;
    this.orcamentosModal = [];
    const refDate = new Date(fatura.closingDate);
    if (!Number.isNaN(refDate.getTime())) {
      this.orcamentoService
        .listar(refDate.getMonth() + 1, refDate.getFullYear())
        .pipe(takeUntil(this.destroy$))
        .subscribe({
          next: (list) => {
            this.orcamentosModal = list || [];
          },
          error: () => {
            this.orcamentosModal = [];
          }
        });
    }
    openCeFormDialog(this.dialog, template, {
      width: 'min(920px, 94vw)',
      maxWidth: '94vw',
      maxHeight: '90vh',
      panelClass: 'fatura-transacoes-dialog',
      autoFocus: false,
    });
  }

  badgeCategoria(t: any): string {
    const nome = (t?.categoriaNome ?? t?.categoria?.nome) as string | undefined;
    const s = (nome || '').trim();
    return s || 'Sem categoria';
  }

  categoriaInstavel(t: any): boolean {
    const id = t?.categoriaId as number | undefined;
    const nome = this.badgeCategoria(t).toLowerCase();
    for (const o of this.orcamentosModal) {
      if (id != null && Number(o.categoriaId) === Number(id)) {
        return o.percentualUso > 100 || o.status === 'VERMELHO';
      }
      const on = (o.categoriaNome || '').trim().toLowerCase();
      if (on && on === nome) {
        return o.percentualUso > 100 || o.status === 'VERMELHO';
      }
    }
    return false;
  }

  formatarDataTransacao(t: any): string {
    const d = new Date(t?.dataTransacao || t?.date || 0);
    if (Number.isNaN(d.getTime())) {
      return '—';
    }
    const pad = (n: number) => String(n).padStart(2, '0');
    return `${pad(d.getDate())}/${pad(d.getMonth() + 1)}/${d.getFullYear()}`;
  }

  logTimestamp(t: any): string {
    const d = new Date(t?.dataTransacao || t?.date || 0);
    if (Number.isNaN(d.getTime())) {
      return '--:--:--';
    }
    const pad = (n: number) => String(n).padStart(2, '0');
    return `${pad(d.getDate())}/${pad(d.getMonth() + 1)} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
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
    return f.id ?? String(_index);
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

  textoContagemTransacoes(f: CreditCardInvoice): string {
    const total = this.contagemTransacoes(f);
    return `${total} ${total === 1 ? 'transação' : 'transações'}`;
  }

  transacoesDaFatura(f: CreditCardInvoice | null): any[] {
    return [...(f?.transactions || [])].sort((a: any, b: any) => {
      const dataA = new Date(a.dataTransacao || a.date || 0).getTime();
      const dataB = new Date(b.dataTransacao || b.date || 0).getTime();
      return dataA - dataB;
    });
  }

  parcelasDaFatura(f: CreditCardInvoice): any[] {
    return (f.transactions || [])
      .filter((t: any) => Number(t.totalParcelas) > 1 && Number(t.parcelaAtual) > 0)
      .slice(0, 4);
  }

  textoParcela(t: any): string {
    const atual = Number(t.parcelaAtual) || 0;
    const total = Number(t.totalParcelas) || 0;
    const restantes = Math.max(0, total - atual);
    return `${t.descricao || 'Compra'}: ${atual}/${total} (${restantes} restante${restantes !== 1 ? 's' : ''})`;
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
    if (!data) {
      return '-';
    }
    const d = new Date(data);
    if (Number.isNaN(d.getTime())) {
      return '-';
    }
    return new Intl.DateTimeFormat('pt-BR').format(d);
  }

  getDiasVencimento(fatura: CreditCardInvoice): number {
    const hoje = new Date();
    const vencimento = new Date(fatura.dueDate);
    const diffTime = vencimento.getTime() - hoje.getTime();
    return Math.ceil(diffTime / (1000 * 60 * 60 * 24));
  }

  isVencendo(fatura: CreditCardInvoice): boolean {
    if (fatura.status !== 'PENDING') {
      return false;
    }
    const dias = this.getDiasVencimento(fatura);
    return dias >= 0 && dias <= 7;
  }

  isVencida(fatura: CreditCardInvoice): boolean {
    if (fatura.status === 'PAID' || fatura.status === 'PARTIAL') {
      return false;
    }
    if (fatura.status === 'OVERDUE') {
      return true;
    }
    if (fatura.status !== 'PENDING') {
      return false;
    }
    return this.getDiasVencimento(fatura) < 0;
  }

  isPaga(fatura: CreditCardInvoice): boolean {
    return fatura.status === 'PAID';
  }

  isParcial(fatura: CreditCardInvoice): boolean {
    return fatura.status === 'PARTIAL';
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
      case 'PARTIAL': return 'Paga parcial';
      case 'PENDING': return 'Pendente';
      case 'OVERDUE': return 'Vencida';
      case 'PREVISTA': return 'Prevista';
      default: return 'Pendente';
    }
  }

  getBancoColor(bankName: string): string {
    return getBancoCorBr(bankName);
  }

  getBancoNome(bankName: string): string {
    return getBancoNomeBr(bankName);
  }

  faturaEstaProcessando(fatura: CreditCardInvoice): boolean {
    return this.acaoEmAndamento && this.faturaProcessandoId === fatura.id;
  }
}
