import { Component, DestroyRef, OnInit, TemplateRef, ViewChild, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators, FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDialog } from '@angular/material/dialog';
import { CE_DIALOG_IMPORTS } from '../../shared/ce-dialog-imports';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ContaBancaria, TIPOS_CONTA } from '../../models/conta-bancaria.model';
import { TransferenciaConta } from '../../models/transferencia.model';
import { ContaBancariaService } from '../../services/conta-bancaria.service';
import { FinancaAlteracaoService } from '../../services/financa-alteracao.service';
import { TransferenciaService } from '../../services/transferencia.service';
import { ConfirmDialogService } from '../../services/confirm-dialog.service';
import { openCeFormDialog, wireCeDialogBehavior } from '../../shared/ce-form-dialog.util';
import { TransferenciaModalComponent } from '../../shared/transferencia-modal/transferencia-modal.component';
import { CeInputMaskDirective } from '../../shared/directives/ce-input-mask.directive';
import { WhatsappParityHintComponent } from '../../shared/whatsapp-parity-hint/whatsapp-parity-hint.component';
import { markAllControlsTouched, parseValorBrasileiro, resolveHttpError } from '../../shared/utils/form.utils';
import { escutarAlteracoesFinanceiras } from '../../shared/utils/financa-alteracao-refresh.util';
import { Observable, of } from 'rxjs';
import { switchMap } from 'rxjs/operators';

@Component({
  selector: 'app-contas-bancarias',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    FormsModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatCheckboxModule,
    MatSnackBarModule,
    ...CE_DIALOG_IMPORTS,
    MatProgressSpinnerModule,
    CeInputMaskDirective,
    WhatsappParityHintComponent,
  ],
  templateUrl: './contas-bancarias.component.html',
  styleUrl: './contas-bancarias.component.scss',
})
export class ContasBancariasComponent implements OnInit {
  private readonly destroyRef = inject(DestroyRef);

  @ViewChild('formTpl') formTpl!: TemplateRef<unknown>;
  @ViewChild('correcaoTpl') correcaoTpl!: TemplateRef<unknown>;

  contas: ContaBancaria[] = [];
  historicoTransferencias: TransferenciaConta[] = [];
  patrimonio = 0;
  loading = false;
  loadingHistorico = false;
  salvando = false;
  reconciliandoId: number | null = null;
  salvandoCorrecao = false;
  correcaoSaldos: { id: number; nome: string; saldoAtual: number; saldoApp: string }[] = [];
  tipos = TIPOS_CONTA;
  form!: FormGroup;
  editando: ContaBancaria | null = null;

  constructor(
    private fb: FormBuilder,
    private contaService: ContaBancariaService,
    private snackBar: MatSnackBar,
    private dialog: MatDialog,
    private confirmDialog: ConfirmDialogService,
    private financaAlteracao: FinancaAlteracaoService,
    private transferenciaService: TransferenciaService
  ) {
    this.form = this.fb.group({
      nome: ['', [Validators.required, Validators.maxLength(100)]],
      tipo: ['CORRENTE', Validators.required],
      saldoAtual: [0, Validators.required],
      limiteChequeEspecial: [0],
      padrao: [false],
      saldoReferenciaApp: [''],
    });
  }

  ngOnInit(): void {
    escutarAlteracoesFinanceiras(
      this.financaAlteracao,
      this.destroyRef,
      () => {
        this.carregar({ silent: true });
        this.carregarHistorico({ silent: true });
      },
      ['contas', 'transferencia']
    );
    this.carregar();
    this.carregarHistorico();
  }

  carregarHistorico(options?: { silent?: boolean }): void {
    const silent = options?.silent === true;
    if (!silent) {
      this.loadingHistorico = true;
    }
    this.transferenciaService.listarHistorico().subscribe({
      next: (lista) => {
        this.historicoTransferencias = lista ?? [];
        this.loadingHistorico = false;
      },
      error: () => {
        this.loadingHistorico = false;
        this.historicoTransferencias = [];
      },
    });
  }

  abrirTransferencia(): void {
    const ref = this.dialog.open(TransferenciaModalComponent, {
      width: '480px',
      maxWidth: '96vw',
      disableClose: true,
      panelClass: 'transferencia-dialog',
    });
    wireCeDialogBehavior(ref, () => false);
    ref
      .afterClosed()
      .subscribe((ok) => {
        if (ok) {
          this.carregar({ silent: true });
          this.carregarHistorico({ silent: true });
        }
      });
  }

  formatarDataTransferencia(iso: string): string {
    if (!iso) {
      return '—';
    }
    const d = new Date(iso);
    return Number.isNaN(d.getTime()) ? iso : d.toLocaleString('pt-BR');
  }

  carregar(options?: { silent?: boolean }): void {
    const silent = options?.silent === true;
    if (!silent) {
      this.loading = true;
    }
    this.contaService.listar(true).subscribe({
      next: (contas) => {
        this.contas = contas ?? [];
        this.patrimonio = this.contas.reduce((acc, c) => acc + (Number(c.saldoAtual) || 0), 0);
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.snackBar.open('Erro ao carregar contas', 'Fechar', { duration: 3000 });
      },
    });
  }

  abrirForm(conta?: ContaBancaria): void {
    this.editando = conta ?? null;
    if (conta) {
      this.form.patchValue({
        nome: conta.nome,
        tipo: conta.tipo,
        saldoAtual: conta.saldoAtual,
        limiteChequeEspecial: conta.limiteChequeEspecial ?? 0,
        padrao: !!conta.padrao,
        saldoReferenciaApp: '',
      });
      this.form.get('saldoAtual')?.disable();
    } else {
      this.form.reset({ tipo: 'CORRENTE', saldoAtual: 0, limiteChequeEspecial: 0, padrao: false, saldoReferenciaApp: '' });
      this.form.get('saldoAtual')?.enable();
    }
    openCeFormDialog(this.dialog, this.formTpl, {
      width: '480px',
      panelClass: 'conta-form-dialog',
      autoFocus: 'first-titled-element',
    });
  }

  salvar(): void {
    if (this.form.invalid) {
      markAllControlsTouched(this.form);
      return;
    }
    this.salvando = true;
    const raw = this.form.getRawValue();
    const limiteCheque = Math.max(0, parseValorBrasileiro(raw.limiteChequeEspecial) ?? 0);
    const payload: ContaBancaria = {
      nome: raw.nome,
      tipo: raw.tipo,
      saldoAtual: parseValorBrasileiro(raw.saldoAtual) ?? 0,
      limiteChequeEspecial: limiteCheque,
      padrao: !!raw.padrao,
      ativa: true,
    };

    const req$ = this.editando?.id
      ? this.contaService.atualizar(this.editando.id, {
          nome: payload.nome,
          tipo: payload.tipo,
          limiteChequeEspecial: limiteCheque,
          padrao: payload.padrao,
          ativa: true,
        })
      : this.contaService.criar(payload);

    const saldoApp = parseValorBrasileiro(raw.saldoReferenciaApp);
    const syncSaldo$: Observable<unknown> =
      this.editando?.id && saldoApp != null
        ? this.contaService.sincronizarSaldo(this.editando.id, saldoApp)
        : of(null);

    syncSaldo$.pipe(switchMap(() => req$)).subscribe({
      next: () => {
        this.salvando = false;
        this.dialog.closeAll();
        this.financaAlteracao.notificar('contas');
        this.carregar({ silent: true });
        this.snackBar.open(this.editando ? 'Conta atualizada' : 'Conta criada', 'Fechar', { duration: 2500 });
      },
      error: (err) => {
        this.salvando = false;
        this.snackBar.open(resolveHttpError(err, 'Erro ao salvar conta'), 'Fechar', { duration: 4000 });
      },
    });
  }

  inativar(conta: ContaBancaria): void {
    if (!conta.id) {
      return;
    }
    this.confirmDialog.ask({
      title: 'Inativar conta',
      message: `Inativar "${conta.nome}"? O histórico de transações permanece.`,
      confirmLabel: 'Inativar',
      destructive: true,
    }).subscribe((ok) => {
        if (!ok) {
          return;
        }
        this.contaService.inativar(conta.id!).subscribe({
          next: () => {
            this.financaAlteracao.notificar('contas');
            this.carregar({ silent: true });
            this.snackBar.open('Conta inativada', 'Fechar', { duration: 2500 });
          },
          error: () => this.snackBar.open('Erro ao inativar', 'Fechar', { duration: 3000 }),
        });
      });
  }

  reconciliarSaldo(conta: ContaBancaria): void {
    if (!conta.id) {
      return;
    }
    this.reconciliandoId = conta.id;
    this.contaService.reconciliarSaldo(conta.id).subscribe({
      next: (r) => {
        this.reconciliandoId = null;
        this.financaAlteracao.notificar('contas');
        this.carregar({ silent: true });
        const msg =
          r.saldoAnterior !== r.saldoCalculado
            ? `Saldo recalculado: ${this.brl(r.saldoAnterior)} → ${this.brl(r.saldoCalculado)}`
            : 'Saldo já estava consistente com as transações';
        this.snackBar.open(msg, 'Fechar', { duration: 4500 });
      },
      error: (err) => {
        this.reconciliandoId = null;
        this.snackBar.open(resolveHttpError(err, 'Erro ao recalcular saldo'), 'Fechar', { duration: 4000 });
      },
    });
  }

  temSaldoAnomalo(): boolean {
    return this.contas.some((c) => this.contaPrecisaCorrecao(c));
  }

  contaPrecisaCorrecao(c: ContaBancaria): boolean {
    const saldo = Number(c.saldoAtual) || 0;
    const limite = Number(c.limiteChequeEspecial) || 0;
    return limite > 0 && saldo < -(limite + 0.01);
  }

  abrirCorrecaoSaldos(): void {
    this.correcaoSaldos = this.contas
      .filter((c) => c.id != null)
      .map((c) => ({
        id: c.id!,
        nome: c.nome,
        saldoAtual: Number(c.saldoAtual) || 0,
        saldoApp: '',
      }));
    openCeFormDialog(this.dialog, this.correcaoTpl, {
      width: '520px',
      panelClass: 'conta-correcao-dialog',
      autoFocus: 'first-titled-element',
    });
  }

  abrirCorrigirConta(conta: ContaBancaria): void {
    if (!conta.id) {
      return;
    }
    this.correcaoSaldos = [{
      id: conta.id,
      nome: conta.nome,
      saldoAtual: Number(conta.saldoAtual) || 0,
      saldoApp: '',
    }];
    openCeFormDialog(this.dialog, this.correcaoTpl, {
      width: '480px',
      panelClass: 'conta-correcao-dialog',
      autoFocus: 'first-titled-element',
    });
  }

  salvarCorrecaoSaldos(): void {
    const itens = this.correcaoSaldos
      .map((r) => ({ contaId: r.id, saldoAtual: parseValorBrasileiro(r.saldoApp) }))
      .filter((r): r is { contaId: number; saldoAtual: number } => r.saldoAtual != null);
    if (!itens.length) {
      this.snackBar.open('Informe o saldo de ao menos uma conta (como aparece no app do banco)', 'Fechar', { duration: 4000 });
      return;
    }
    this.salvandoCorrecao = true;
    this.contaService.sincronizarSaldosLote(itens).subscribe({
      next: () => {
        this.salvandoCorrecao = false;
        this.dialog.closeAll();
        this.financaAlteracao.notificar('contas');
        this.carregar({ silent: true });
        this.snackBar.open('Saldos atualizados conforme o app bancário', 'Fechar', { duration: 3500 });
      },
      error: (err) => {
        this.salvandoCorrecao = false;
        this.snackBar.open(resolveHttpError(err, 'Erro ao corrigir saldos'), 'Fechar', { duration: 4000 });
      },
    });
  }

  brl(v: number): string {
    return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(v ?? 0);
  }

  labelTipo(tipo: string): string {
    return this.tipos.find((t) => t.value === tipo)?.label ?? tipo;
  }

  /** True quando a conta possui cheque especial configurado (> 0). */
  temChequeEspecial(c: ContaBancaria): boolean {
    return (c.limiteChequeEspecial ?? 0) > 0;
  }

  /** Quanto do cheque especial está em uso (zero se saldo positivo). */
  chequeEspecialUtilizado(c: ContaBancaria): number {
    const saldo = Number(c.saldoAtual) || 0;
    return saldo < 0 ? Math.abs(saldo) : 0;
  }

  /** True quando o saldo está negativo (usando cheque especial ou negativo legado). */
  saldoNegativo(c: ContaBancaria): boolean {
    return (Number(c.saldoAtual) || 0) < 0;
  }

  /** Saldo + limite de cheque especial (mesma regra do pagamento de fatura). */
  saldoDisponivelConta(c: ContaBancaria): number {
    if (c.saldoDisponivel != null && !Number.isNaN(Number(c.saldoDisponivel))) {
      return Number(c.saldoDisponivel);
    }
    return (Number(c.saldoAtual) || 0) + (Number(c.limiteChequeEspecial) || 0);
  }
}
