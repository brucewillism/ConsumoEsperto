import { Component, OnInit, TemplateRef, ViewChild } from '@angular/core';
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
import { ContaBancaria, TIPOS_CONTA } from '../../models/conta-bancaria.model';
import { ContaBancariaService } from '../../services/conta-bancaria.service';
import { FinancaAlteracaoService } from '../../services/financa-alteracao.service';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog.component';

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
  ],
  templateUrl: './contas-bancarias.component.html',
  styleUrl: './contas-bancarias.component.scss',
})
export class ContasBancariasComponent implements OnInit {
  @ViewChild('formTpl') formTpl!: TemplateRef<unknown>;

  contas: ContaBancaria[] = [];
  patrimonio = 0;
  loading = false;
  salvando = false;
  tipos = TIPOS_CONTA;
  form!: FormGroup;
  editando: ContaBancaria | null = null;

  constructor(
    private fb: FormBuilder,
    private contaService: ContaBancariaService,
    private snackBar: MatSnackBar,
    private dialog: MatDialog,
    private financaAlteracao: FinancaAlteracaoService
  ) {
    this.form = this.fb.group({
      nome: ['', [Validators.required, Validators.maxLength(100)]],
      tipo: ['CORRENTE', Validators.required],
      saldoAtual: [0, [Validators.required, Validators.min(0)]],
      padrao: [false],
    });
  }

  ngOnInit(): void {
    this.carregar();
  }

  carregar(): void {
    this.loading = true;
    this.contaService.listar(true).subscribe({
      next: (contas) => {
        this.contas = contas ?? [];
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.snackBar.open('Erro ao carregar contas', 'Fechar', { duration: 3000 });
      },
    });
    this.contaService.patrimonio().subscribe({
      next: (v) => (this.patrimonio = Number(v) || 0),
      error: () => (this.patrimonio = 0),
    });
  }

  abrirForm(conta?: ContaBancaria): void {
    this.editando = conta ?? null;
    if (conta) {
      this.form.patchValue({
        nome: conta.nome,
        tipo: conta.tipo,
        saldoAtual: conta.saldoAtual,
        padrao: !!conta.padrao,
      });
      this.form.get('saldoAtual')?.disable();
    } else {
      this.form.reset({ tipo: 'CORRENTE', saldoAtual: 0, padrao: false });
      this.form.get('saldoAtual')?.enable();
    }
    this.dialog.open(this.formTpl, { width: '480px' });
  }

  salvar(): void {
    if (this.form.invalid) {
      return;
    }
    this.salvando = true;
    const raw = this.form.getRawValue();
    const payload: ContaBancaria = {
      nome: raw.nome,
      tipo: raw.tipo,
      saldoAtual: Number(raw.saldoAtual) || 0,
      padrao: !!raw.padrao,
      ativa: true,
    };

    const req$ = this.editando?.id
      ? this.contaService.atualizar(this.editando.id, { ...payload, saldoAtual: this.editando.saldoAtual })
      : this.contaService.criar(payload);

    req$.subscribe({
      next: () => {
        this.salvando = false;
        this.dialog.closeAll();
        this.financaAlteracao.notificar();
        this.carregar();
        this.snackBar.open(this.editando ? 'Conta atualizada' : 'Conta criada', 'Fechar', { duration: 2500 });
      },
      error: (err) => {
        this.salvando = false;
        const msg = err?.error?.message || 'Erro ao salvar conta';
        this.snackBar.open(msg, 'Fechar', { duration: 4000 });
      },
    });
  }

  inativar(conta: ContaBancaria): void {
    if (!conta.id) {
      return;
    }
    this.dialog
      .open(ConfirmDialogComponent, {
        data: {
          title: 'Inativar conta',
          message: `Inativar "${conta.nome}"? O histórico de transações permanece.`,
          confirmText: 'Inativar',
        },
      })
      .afterClosed()
      .subscribe((ok) => {
        if (!ok) {
          return;
        }
        this.contaService.inativar(conta.id!).subscribe({
          next: () => {
            this.financaAlteracao.notificar();
            this.carregar();
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
}
