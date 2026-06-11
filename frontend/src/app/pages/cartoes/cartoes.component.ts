import { Component, DestroyRef, OnInit, TemplateRef, ViewChild, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { CartaoCreditoService } from '../../services/cartao-credito.service';
import { ConfirmDialogService } from '../../services/confirm-dialog.service';
import { openCeFormDialog } from '../../shared/ce-form-dialog.util';
import { NovoCartaoDialogComponent } from '../../shared/novo-cartao-dialog/novo-cartao-dialog.component';
import { FinancaAlteracaoService } from '../../services/financa-alteracao.service';
import { CartaoCredito } from '../../models/cartao-credito.model';
import { BANCOS_BRASIL, getBancoCorBr, getBancoNomeBr } from '../../shared/constants/bancos-brasil';
import { parseValorBrasileiro, sanitizeCardNumberInput } from '../../shared/utils/form.utils';
import { PageLoadingComponent } from '../../shared/page-loading/page-loading.component';
import { WhatsappParityHintComponent } from '../../shared/whatsapp-parity-hint/whatsapp-parity-hint.component';
import { escutarAlteracoesFinanceiras } from '../../shared/utils/financa-alteracao-refresh.util';

@Component({
  selector: 'app-cartoes',
  standalone: true,
  imports: [
    CommonModule,
    MatButtonModule,
    MatCardModule,
    MatChipsModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressBarModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    MatDialogModule,
    FormsModule,
    PageLoadingComponent,
    WhatsappParityHintComponent,
  ],
  templateUrl: './cartoes.component.html',
  styleUrls: ['./cartoes.component.scss']
})
export class CartoesComponent implements OnInit {
  private readonly destroyRef = inject(DestroyRef);

  @ViewChild('editCartaoTpl') editCartaoTpl!: TemplateRef<unknown>;

  readonly bancosBrasil = BANCOS_BRASIL;
  cartoes: CartaoCredito[] = [];
  totalCreditLimit = 0;
  totalAvailableCredit = 0;
  loading = false;
  acaoEmAndamento = false;
  cartaoProcessandoId: number | null = null;
  error: string | null = null;
  cartaoEmEdicao: CartaoCredito | null = null;
  editLimite = 0;

  constructor(
    private cartaoCreditoService: CartaoCreditoService,
    private snackBar: MatSnackBar,
    private dialog: MatDialog,
    private confirmDialog: ConfirmDialogService,
    private financaAlteracao: FinancaAlteracaoService
  ) {}

  ngOnInit(): void {
    escutarAlteracoesFinanceiras(
      this.financaAlteracao,
      this.destroyRef,
      () => this.loadData({ silent: true }),
      ['cartoes']
    );
    this.loadData();
  }

  loadData(options?: { silent?: boolean }): void {
    const silent = options?.silent === true;
    if (!silent) {
      this.loading = true;
    }
    this.error = null;
    this.cartaoCreditoService.buscarPorUsuario().subscribe({
      next: (cartoes) => {
        this.cartoes = cartoes ?? [];
        this.totalCreditLimit = this.cartoes.reduce((sum, c) => sum + (c.limiteCredito || 0), 0);
        this.totalAvailableCredit = this.cartoes.reduce((sum, c) => sum + (c.limiteDisponivel || 0), 0);
        this.loading = false;
      },
      error: () => {
        this.error = 'Erro ao carregar cartões';
        this.loading = false;
      }
    });
  }

  abrirNovoCartao(): void {
    openCeFormDialog(this.dialog, NovoCartaoDialogComponent, { width: '560px' })
      .afterClosed()
      .subscribe((criado) => {
        if (criado) {
          this.financaAlteracao.notificar('cartoes');
          this.loadData({ silent: true });
        }
      });
  }

  atualizarDados(): void {
    this.loadData();
  }

  getBancoColor(banco: string): string {
    return getBancoCorBr(banco);
  }

  getBancoNome(banco: string): string {
    return getBancoNomeBr(banco);
  }

  formatarMoeda(value: number): string {
    return new Intl.NumberFormat('pt-BR', {
      style: 'currency',
      currency: 'BRL'
    }).format(value);
  }

  getLimiteUtilizado(cartao: CartaoCredito): number {
    if (cartao.limiteUtilizado != null && !Number.isNaN(Number(cartao.limiteUtilizado))) {
      return Number(cartao.limiteUtilizado);
    }
    return Math.max(0, (cartao.limiteCredito || 0) - (cartao.limiteDisponivel || 0));
  }

  get totalLimiteUtilizadoResumo(): number {
    return this.cartoes.reduce((s, c) => s + this.getLimiteUtilizado(c), 0);
  }

  formatarPercentual(value: number): string {
    return `${value.toFixed(1)}%`;
  }

  getPercentualUso(cartao: CartaoCredito): number {
    const limite = cartao.limiteCredito || 0;
    if (limite <= 0) return 0;
    const pct = (this.getLimiteUtilizado(cartao) / limite) * 100;
    return Math.min(100, Math.max(0, pct));
  }

  corBarraUtilizacao(cartao: CartaoCredito): 'primary' | 'accent' | 'warn' {
    const pct = this.getPercentualUso(cartao);
    if (!cartao.ativo) {
      return 'warn';
    }
    if (pct >= 80) {
      return 'accent';
    }
    return 'primary';
  }

  abrirEdicaoCartao(cartao: CartaoCredito): void {
    if (!cartao.id) {
      return;
    }
    this.cartaoEmEdicao = { ...cartao };
    this.editLimite = cartao.limiteCredito ?? 0;
    openCeFormDialog(this.dialog, this.editCartaoTpl, { width: '400px' });
  }

  salvarEdicaoCartao(): void {
    if (!this.cartaoEmEdicao?.id) {
      return;
    }
    if (!this.editLimite || this.editLimite <= 0) {
      this.snackBar.open('Informe um limite válido.', 'Fechar', { duration: 3000, panelClass: ['warning-snackbar'] });
      return;
    }
    this.acaoEmAndamento = true;
    const payload: CartaoCredito = {
      ...this.cartaoEmEdicao,
      limiteCredito: this.editLimite,
      limite: this.editLimite
    };
    if (payload.limiteDisponivel != null && payload.limiteDisponivel > this.editLimite) {
      payload.limiteDisponivel = this.editLimite;
    }
    this.cartaoCreditoService.atualizarCartaoCredito(this.cartaoEmEdicao.id, payload).subscribe({
      next: () => {
        this.snackBar.open('Cartão atualizado.', 'Fechar', { duration: 3000, panelClass: ['success-snackbar'] });
        this.acaoEmAndamento = false;
        this.dialog.closeAll();
        this.cartaoEmEdicao = null;
        this.financaAlteracao.notificar('cartoes');
        this.loadData({ silent: true });
      },
      error: () => {
        this.acaoEmAndamento = false;
        this.snackBar.open('Erro ao atualizar cartão.', 'Fechar', { duration: 3000, panelClass: ['error-snackbar'] });
      }
    });
  }

  excluirCartao(cartao: CartaoCredito): void {
    const id = cartao.id;
    if (id == null) {
      this.snackBar.open('Cartão inválido para exclusão.', 'Fechar', {
        duration: 3000,
        panelClass: ['warning-snackbar']
      });
      return;
    }
    this.confirmDialog.ask({
      title: 'Excluir cartão',
      message: 'O cartão será desativado. Continuar?',
      confirmLabel: 'Excluir',
      destructive: true,
    }).subscribe((ok) => {
      if (!ok) {
        return;
      }
      this.acaoEmAndamento = true;
      this.cartaoProcessandoId = id;
      this.cartaoCreditoService.deletarCartaoCredito(id).subscribe({
        next: () => {
          this.snackBar.open('Cartão removido com sucesso!', 'Fechar', {
            duration: 3000,
            panelClass: ['success-snackbar']
          });
          this.acaoEmAndamento = false;
          this.cartaoProcessandoId = null;
          this.financaAlteracao.notificar('cartoes');
          this.loadData({ silent: true });
        },
        error: () => {
          this.acaoEmAndamento = false;
          this.cartaoProcessandoId = null;
          this.snackBar.open('Erro ao excluir cartão.', 'Fechar', {
            duration: 3000,
            panelClass: ['error-snackbar']
          });
        }
      });
    });
  }

  cartaoEstaProcessando(cartao: CartaoCredito): boolean {
    return this.acaoEmAndamento && this.cartaoProcessandoId !== null && this.cartaoProcessandoId === (cartao.id ?? null);
  }
}
