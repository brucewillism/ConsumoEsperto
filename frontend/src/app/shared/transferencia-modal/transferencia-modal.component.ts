import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  AbstractControl,
  FormBuilder,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { ContaBancaria } from '../../models/conta-bancaria.model';
import { ContaBancariaService } from '../../services/conta-bancaria.service';
import { FinancaAlteracaoService } from '../../services/financa-alteracao.service';
import { TransferenciaService } from '../../services/transferencia.service';

function contasDistintas(group: AbstractControl): ValidationErrors | null {
  const origem = group.get('contaOrigemId')?.value;
  const destino = group.get('contaDestinoId')?.value;
  if (origem != null && destino != null && Number(origem) === Number(destino)) {
    return { contasIguais: true };
  }
  return null;
}

@Component({
  selector: 'app-transferencia-modal',
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
  templateUrl: './transferencia-modal.component.html',
  styleUrl: './transferencia-modal.component.scss',
})
export class TransferenciaModalComponent implements OnInit {
  form!: FormGroup;
  contas: ContaBancaria[] = [];
  carregandoContas = false;
  enviando = false;

  constructor(
    private fb: FormBuilder,
    private contaService: ContaBancariaService,
    private transferenciaService: TransferenciaService,
    private financaAlteracao: FinancaAlteracaoService,
    private snackBar: MatSnackBar,
    private dialogRef: MatDialogRef<TransferenciaModalComponent, boolean>
  ) {
    this.form = this.fb.group(
      {
        contaOrigemId: [null, Validators.required],
        contaDestinoId: [null, Validators.required],
        valor: [null, [Validators.required, Validators.min(0.01)]],
        descricao: ['', Validators.maxLength(200)],
      },
      { validators: contasDistintas }
    );
  }

  ngOnInit(): void {
    this.carregarContas();
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

  get contasIguais(): boolean {
    return !!this.form.errors?.['contasIguais'];
  }

  brl(v: number | null | undefined): string {
    return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(Number(v) || 0);
  }

  saldoConta(id: number | null): number {
    if (id == null) {
      return 0;
    }
    return this.contas.find((c) => c.id === id)?.saldoAtual ?? 0;
  }

  confirmar(): void {
    if (this.form.invalid || this.enviando) {
      this.form.markAllAsTouched();
      return;
    }

    const raw = this.form.getRawValue();
    this.enviando = true;

    this.transferenciaService
      .fazerTransferencia({
        contaOrigemId: Number(raw.contaOrigemId),
        contaDestinoId: Number(raw.contaDestinoId),
        valor: Number(raw.valor),
        descricao: raw.descricao?.trim() || undefined,
      })
      .subscribe({
        next: () => {
          this.enviando = false;
          this.financaAlteracao.notificar();
          this.snackBar.open('Transferência realizada com sucesso', 'Fechar', {
            duration: 3000,
            panelClass: ['success-snackbar'],
          });
          this.dialogRef.close(true);
        },
        error: (err) => {
          this.enviando = false;
          const msg = err?.error?.message || 'Não foi possível concluir a transferência';
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
