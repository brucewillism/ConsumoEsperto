import { Component, Inject, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Subject, takeUntil } from 'rxjs';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { CreditCardInvoice } from '../../models/credit-card-invoice.model';
import { ContaBancaria } from '../../models/conta-bancaria.model';
import { ContaBancariaService } from '../../services/conta-bancaria.service';
import { FaturaService } from '../../services/fatura.service';
import { FinancaAlteracaoService } from '../../services/financa-alteracao.service';
import {
  PrevisaoFuturoChart,
  ProjecaoDashboardService,
} from '../../services/projecao-dashboard.service';
import { ChartMetodologiaComponent } from '../chart-metodologia/chart-metodologia.component';
import { getBancoNomeBr } from '../constants/bancos-brasil';
import { CeInputMaskDirective } from '../directives/ce-input-mask.directive';
import { parseValorBrasileiro } from '../utils/form.utils';

export interface PagamentoFaturaModalData {
  fatura: CreditCardInvoice;
}

/** Aliases de nome de conta que indicam o mesmo provedor da fatura. */
const ALIASES_PROVEDOR: Record<string, string[]> = {
  nubank: ['nubank', 'nu bank', 'nu '],
  itau: ['itaú', 'itau'],
  bradesco: ['bradesco'],
  santander: ['santander'],
  inter: ['inter', 'banco inter'],
  c6: ['c6', 'c6 bank'],
  bb: ['banco do brasil', 'bb '],
  caixa: ['caixa', 'cef'],
};

@Component({
  selector: 'app-pagamento-fatura-modal',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    ChartMetodologiaComponent,
    CeInputMaskDirective,
  ],
  templateUrl: './pagamento-fatura-modal.component.html',
  styleUrl: './pagamento-fatura-modal.component.scss',
})
export class PagamentoFaturaModalComponent implements OnInit, OnDestroy {
  form!: FormGroup;
  contas: ContaBancaria[] = [];
  carregandoContas = false;
  carregandoProjecao = false;
  enviando = false;
  projecaoPatrimonio: PrevisaoFuturoChart | null = null;

  private readonly destroy$ = new Subject<void>();

  constructor(
    private fb: FormBuilder,
    private contaService: ContaBancariaService,
    private faturaService: FaturaService,
    private financaAlteracao: FinancaAlteracaoService,
    private projecaoService: ProjecaoDashboardService,
    private snackBar: MatSnackBar,
    private router: Router,
    private dialogRef: MatDialogRef<PagamentoFaturaModalComponent, boolean>,
    @Inject(MAT_DIALOG_DATA) public data: PagamentoFaturaModalData
  ) {
    const valorTotal = Number(data.fatura.amount) || 0;
    this.form = this.fb.group({
      contaBancariaId: [null, Validators.required],
      valor: [valorTotal, [Validators.required, Validators.min(0.01)]],
    });
  }

  ngOnInit(): void {
    this.carregarContas();
    this.form.valueChanges.pipe(takeUntil(this.destroy$)).subscribe(() => {
      this.atualizarProjecaoSeMesmoProvedor();
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  get fatura(): CreditCardInvoice {
    return this.data.fatura;
  }

  carregarContas(): void {
    this.carregandoContas = true;
    this.contaService.listarContasAtivas().subscribe({
      next: (contas) => {
        this.contas = contas ?? [];
        this.carregandoContas = false;
      },
      error: () => {
        this.carregandoContas = false;
        this.snackBar.open('Erro ao carregar contas bancárias', 'Fechar', { duration: 3500 });
      },
    });
  }

  brl(v: number | null | undefined): string {
    return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(Number(v) || 0);
  }

  getBancoNome(bankName: string): string {
    return getBancoNomeBr(bankName) || 'Cartão';
  }

  mesReferencia(): string {
    const d = new Date(this.fatura.closingDate);
    if (Number.isNaN(d.getTime())) {
      return '—';
    }
    return d.toLocaleDateString('pt-BR', { month: 'long', year: 'numeric' });
  }

  saldoConta(id: number | null): number {
    if (id == null) {
      return 0;
    }
    return this.contas.find((c) => c.id === id)?.saldoAtual ?? 0;
  }

  get contaSelecionada(): ContaBancaria | undefined {
    const id = this.form.get('contaBancariaId')?.value;
    if (id == null) {
      return undefined;
    }
    return this.contas.find((c) => c.id === Number(id));
  }

  get valorPagamento(): number {
    return parseValorBrasileiro(this.form.get('valor')?.value) ?? 0;
  }

  /** Saldo total disponível da conta (saldo + cheque especial). */
  saldoDisponivelConta(conta: ContaBancaria | undefined): number {
    if (!conta) {
      return 0;
    }
    if (conta.saldoDisponivel != null && !Number.isNaN(Number(conta.saldoDisponivel))) {
      return Number(conta.saldoDisponivel);
    }
    return Number(conta.saldoAtual ?? 0) + Number(conta.limiteChequeEspecial ?? 0);
  }

  limiteChequeConta(conta: ContaBancaria | undefined): number {
    return Number(conta?.limiteChequeEspecial ?? 0);
  }

  /** Quanto falta de limite de cheque especial para cobrir o pagamento (0 se já cobre). */
  deficitChequeEspecial(): number {
    const conta = this.contaSelecionada;
    if (!conta || !this.saldoInsuficiente) {
      return 0;
    }
    return Math.max(0, this.valorPagamento - this.saldoDisponivelConta(conta));
  }

  /** Limite mínimo de cheque especial para cobrir o pagamento atual (só saldo nominal). */
  limiteMinimoParaPagamento(): number {
    const conta = this.contaSelecionada;
    if (!conta) {
      return 0;
    }
    return Math.max(0, this.valorPagamento - Number(conta.saldoAtual ?? 0));
  }

  exemploDisponivelComLimite(limite: number): number {
    const conta = this.contaSelecionada;
    if (!conta) {
      return limite;
    }
    return Number(conta.saldoAtual ?? 0) + limite;
  }

  get saldoInsuficiente(): boolean {
    const conta = this.contaSelecionada;
    if (!conta) {
      return false;
    }
    return this.valorPagamento > this.saldoDisponivelConta(conta);
  }

  /** Débito cabe no saldo + cheque especial, mas o saldo nominal ficará negativo. */
  get usaraChequeEspecial(): boolean {
    const conta = this.contaSelecionada;
    if (!conta || this.saldoInsuficiente) {
      return false;
    }
    const saldo = Number(conta.saldoAtual ?? 0);
    const limite = Number(conta.limiteChequeEspecial ?? 0);
    return limite > 0 && this.valorPagamento > saldo;
  }

  valorUsoChequeEspecial(): number {
    const conta = this.contaSelecionada;
    if (!conta) {
      return 0;
    }
    const saldo = Number(conta.saldoAtual ?? 0);
    return Math.max(0, this.valorPagamento - saldo);
  }

  get mesmoProvedor(): boolean {
    const conta = this.contaSelecionada;
    return conta ? this.contaMesmoProvedorDaFatura(conta) : false;
  }

  contaMesmoProvedorDaFatura(conta: ContaBancaria): boolean {
    const slug = (this.fatura.bankName ?? '').toLowerCase().trim();
    if (!slug || !conta.nome) {
      return false;
    }
    const aliases = ALIASES_PROVEDOR[slug] ?? [slug];
    const nomeNormalizado = conta.nome.toLowerCase();
    return aliases.some((alias) => alias.trim() && nomeNormalizado.includes(alias.trim()));
  }

  saldoContaPosDebito(): number | null {
    const conta = this.contaSelecionada;
    if (!conta) {
      return null;
    }
    return Number(conta.saldoAtual ?? 0) - this.valorPagamento;
  }

  /** Projeção consolidada (SaldoService via /projecoes/previsao-futuro) quando mesmo provedor. */
  patrimonioProjetadoPosDebito(): number | null {
    if (!this.mesmoProvedor || !this.projecaoPatrimonio) {
      return null;
    }
    const base =
      this.projecaoPatrimonio.saldoProjetadoFimMes ?? this.projecaoPatrimonio.saldoAtual ?? 0;
    return Number(base) - this.valorPagamento;
  }

  private atualizarProjecaoSeMesmoProvedor(): void {
    if (!this.mesmoProvedor) {
      this.projecaoPatrimonio = null;
      return;
    }
    if (this.projecaoPatrimonio || this.carregandoProjecao) {
      return;
    }
    this.carregandoProjecao = true;
    this.projecaoService.previsaoFuturo().subscribe({
      next: (projecao) => {
        this.projecaoPatrimonio = projecao;
        this.carregandoProjecao = false;
      },
      error: () => {
        this.projecaoPatrimonio = null;
        this.carregandoProjecao = false;
      },
    });
  }

  usarSaldoDisponivel(): void {
    const conta = this.contaSelecionada;
    if (!conta) {
      return;
    }
    const max = Math.min(this.saldoDisponivelConta(conta), Number(this.fatura.amount) || 0);
    if (max <= 0) {
      return;
    }
    this.form.patchValue({ valor: max });
  }

  confirmar(): void {
    if (this.form.invalid || this.enviando) {
      this.form.markAllAsTouched();
      return;
    }
    if (this.saldoInsuficiente) {
      const conta = this.contaSelecionada;
      this.snackBar.open(
        `Saldo insuficiente em ${conta?.nome ?? 'conta'}. Disponível (incl. cheque especial): `
          + `${this.brl(this.saldoDisponivelConta(conta))}; pagamento: ${this.brl(this.valorPagamento)}. `
          + `Ajuste o valor ou escolha outra conta.`,
        'Fechar',
        { duration: 6000, panelClass: ['error-snackbar'] }
      );
      this.form.markAllAsTouched();
      return;
    }

    const raw = this.form.getRawValue();
    const faturaId = Number(this.fatura.id);
    if (!faturaId) {
      this.snackBar.open('Fatura inválida para pagamento', 'Fechar', { duration: 3500 });
      return;
    }

    this.enviando = true;
    this.faturaService
      .pagarFatura({
        faturaId,
        contaBancariaId: Number(raw.contaBancariaId),
        valor: parseValorBrasileiro(raw.valor) ?? 0,
      })
      .subscribe({
        next: () => {
          this.enviando = false;
          this.financaAlteracao.notificar();
          this.snackBar.open('Fatura paga e conciliada com sucesso', 'Fechar', {
            duration: 3500,
            panelClass: ['success-snackbar'],
          });
          this.dialogRef.close(true);
        },
        error: (err) => {
          this.enviando = false;
          const msg = err?.error?.message || 'Não foi possível concluir o pagamento da fatura';
          this.snackBar.open(msg, 'Fechar', { duration: 4500, panelClass: ['error-snackbar'] });
        },
      });
  }

  fechar(): void {
    if (!this.enviando) {
      this.dialogRef.close(false);
    }
  }

  irConfigurarChequeEspecial(): void {
    this.dialogRef.close(false);
    void this.router.navigate(['/contas']);
  }
}
