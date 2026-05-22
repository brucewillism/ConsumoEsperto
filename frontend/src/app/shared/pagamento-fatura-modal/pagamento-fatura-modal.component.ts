import { Component, Inject, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
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
    const nomes: Record<string, string> = {
      itau: 'Itaú',
      nubank: 'Nubank',
      bradesco: 'Bradesco',
      santander: 'Santander',
      inter: 'Banco Inter',
      c6: 'C6 Bank',
      bb: 'Banco do Brasil',
      caixa: 'Caixa',
    };
    return nomes[bankName?.toLowerCase()] ?? bankName ?? 'Cartão';
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
    return Number(this.form.get('valor')?.value) || 0;
  }

  get saldoInsuficiente(): boolean {
    const conta = this.contaSelecionada;
    if (!conta) {
      return false;
    }
    return this.valorPagamento > Number(conta.saldoAtual ?? 0);
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

  confirmar(): void {
    if (this.form.invalid || this.enviando || this.saldoInsuficiente) {
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
        valor: Number(raw.valor),
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
}
