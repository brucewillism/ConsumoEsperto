import { Component, DestroyRef, OnInit, TemplateRef, ViewChild, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
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
import { openCeFormDialog } from '../../shared/ce-form-dialog.util';
import { TransferenciaModalComponent } from '../../shared/transferencia-modal/transferencia-modal.component';
import { CeInputMaskDirective } from '../../shared/directives/ce-input-mask.directive';
import { WhatsappParityHintComponent } from '../../shared/whatsapp-parity-hint/whatsapp-parity-hint.component';
import { markAllControlsTouched, parseValorBrasileiro, resolveHttpError } from '../../shared/utils/form.utils';
import { escutarAlteracoesFinanceiras } from '../../shared/utils/financa-alteracao-refresh.util';

@Component({
  selector: 'app-contas-bancarias',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatCheckboxModule,
    MatSnackBarModule,
    MatDialogModule,
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

  contas: ContaBancaria[] = [];
  historicoTransferencias: TransferenciaConta[] = [];
  patrimonio = 0;
  loading = false;
  loadingHistorico = false;
  salvando = false;
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
    this.dialog
      .open(TransferenciaModalComponent, {
        width: '480px',
        maxWidth: '96vw',
        disableClose: true,
        panelClass: 'transferencia-dialog',
      })
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
      });
      this.form.get('saldoAtual')?.disable();
    } else {
      this.form.reset({ tipo: 'CORRENTE', saldoAtual: 0, limiteChequeEspecial: 0, padrao: false });
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

    req$.subscribe({
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
