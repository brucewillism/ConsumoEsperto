import { Component, OnInit, TemplateRef, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule, FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatNativeDateModule } from '@angular/material/core';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { CartaoCreditoService } from '../../services/cartao-credito.service';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog.component';
import { FinancaAlteracaoService } from '../../services/financa-alteracao.service';
import { CartaoCredito, TipoCartao } from '../../models/cartao-credito.model';

@Component({
  selector: 'app-cartoes',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatChipsModule,
    MatDatepickerModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatNativeDateModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatSnackBarModule,
    MatDialogModule,
    FormsModule
  ],
  templateUrl: './cartoes.component.html',
  styleUrls: ['./cartoes.component.scss']
})
export class CartoesComponent implements OnInit {
  @ViewChild('editCartaoTpl') editCartaoTpl!: TemplateRef<unknown>;

  cartoes: CartaoCredito[] = [];
  totalCreditLimit = 0;
  totalAvailableCredit = 0;
  loading = false;
  acaoEmAndamento = false;
  cartaoProcessandoId: number | null = null;
  error: string | null = null;
  showForm = false;
  novoCartaoForm!: FormGroup;

  cartaoEmEdicao: CartaoCredito | null = null;
  editLimite = 0;

  constructor(
    private fb: FormBuilder,
    private cartaoCreditoService: CartaoCreditoService,
    private snackBar: MatSnackBar,
    private dialog: MatDialog,
    private financaAlteracao: FinancaAlteracaoService
  ) {
    this.initForm();
  }

  ngOnInit(): void {
    this.loadData();
  }

  private initForm(): void {
    this.novoCartaoForm = this.fb.group({
      banco: ['', Validators.required],
      numero: ['', [Validators.required, Validators.minLength(4), Validators.maxLength(19)]],
      titular: ['', Validators.required],
      limite: [0, [Validators.required, Validators.min(0.01)]],
      vencimento: ['', Validators.required]
    });
  }

  loadData(): void {
    this.loading = true;
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

  adicionarCartao(): void {
    if (this.novoCartaoForm.invalid) {
      this.novoCartaoForm.markAllAsTouched();
      this.snackBar.open('Revise os campos destacados antes de adicionar o cartão.', 'Fechar', {
        duration: 3500,
        panelClass: ['warning-snackbar']
      });
      return;
    }

    if (this.novoCartaoForm.valid) {
      this.acaoEmAndamento = true;
      const cartaoForm = this.novoCartaoForm.value;
      const limite = Number(cartaoForm.limite || 0);
      const dueDate = new Date(cartaoForm.vencimento);
      const numeroCartao = String(cartaoForm.numero).replace(/\D/g, '');
      const diaVencimento = Number.isNaN(dueDate.getTime()) ? undefined : dueDate.getDate();

      const payload: CartaoCredito = {
        nome: cartaoForm.titular,
        banco: cartaoForm.banco,
        numeroCartao,
        limiteCredito: limite,
        limiteDisponivel: limite,
        tipoCartao: TipoCartao.CREDITO,
        ativo: true,
        diaVencimento,
        dataVencimento: dueDate,
        limite: limite,
        limiteUtilizado: 0
      };

      this.cartaoCreditoService.criarCartaoCredito(payload).subscribe({
        next: () => {
          this.snackBar.open('Cartão cadastrado com sucesso!', 'Fechar', {
          duration: 3000,
          panelClass: ['success-snackbar']
        });
          this.showForm = false;
          this.novoCartaoForm.reset();
          this.acaoEmAndamento = false;
          this.loadData();
        },
        error: (err) => {
          this.acaoEmAndamento = false;
          this.snackBar.open(this.mensagemErroCadastroCartao(err), 'Fechar', {
          duration: 3000,
          panelClass: ['error-snackbar']
        });
        }
      });
    }
  }

  private mensagemErroCadastroCartao(err: any): string {
    const body = err?.error;
    if (typeof body === 'string' && body.trim()) {
      return body;
    }
    if (body?.message) {
      return body.message;
    }
    if (body?.errors && typeof body.errors === 'object') {
      const primeira = Object.values(body.errors)[0];
      if (typeof primeira === 'string') {
        return primeira;
      }
      if (Array.isArray(primeira) && primeira.length > 0) {
        return String(primeira[0]);
      }
    }
    if (err?.status === 400) {
      return 'Dados inválidos. Confira número, limite e vencimento.';
    }
    return 'Erro ao cadastrar cartão.';
  }

  atualizarDados(): void {
    this.loadData();
  }

  getBancoColor(banco: string): string {
    const cores: { [key: string]: string } = {
      'itau': '#EC7000',
      'nubank': '#8A05BE',
      'inter': '#FF7A00'
    };
    return cores[banco.toLowerCase()] || '#666';
  }

  /**
   * Obtém nome do banco
   */
  getBancoNome(banco: string): string {
    const nomes: { [key: string]: string } = {
      'itau': 'Itaú',
      'nubank': 'Nubank',
      'inter': 'Banco Inter'
    };
    return nomes[banco.toLowerCase()] || banco;
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
    return (this.getLimiteUtilizado(cartao) / limite) * 100;
  }

  abrirEdicaoCartao(cartao: CartaoCredito): void {
    if (!cartao.id) {
      return;
    }
    this.cartaoEmEdicao = { ...cartao };
    this.editLimite = cartao.limiteCredito ?? 0;
    this.dialog.open(this.editCartaoTpl, { width: '400px' });
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
        this.financaAlteracao.notificar();
        this.loadData();
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
    const ref = this.dialog.open(ConfirmDialogComponent, {
      width: '400px',
      data: {
        title: 'Excluir cartão',
        message: 'O cartão será desativado. Continuar?',
        confirmLabel: 'Excluir',
        destructive: true
      }
    });
    ref.afterClosed().subscribe((ok) => {
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
          this.financaAlteracao.notificar();
          this.loadData();
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
