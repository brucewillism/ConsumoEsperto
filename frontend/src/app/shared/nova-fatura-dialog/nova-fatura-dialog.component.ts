import { Component, Inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatNativeDateModule } from '@angular/material/core';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { CreditCardInvoice } from '../../models/credit-card-invoice.model';
import { CartaoCredito } from '../../models/cartao-credito.model';
import { FaturaService } from '../../services/fatura.service';
import { CeInputMaskDirective } from '../directives/ce-input-mask.directive';
import { markAllControlsTouched, parseValorBrasileiro, resolveHttpError } from '../utils/form.utils';

export interface NovaFaturaDialogData {
  cartoes: CartaoCredito[];
}

@Component({
  selector: 'app-nova-fatura-dialog',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    CeInputMaskDirective,
  ],
  templateUrl: './nova-fatura-dialog.component.html',
  styleUrl: './nova-fatura-dialog.component.scss',
})
export class NovaFaturaDialogComponent implements OnInit {
  form!: FormGroup;
  salvando = false;

  constructor(
    private fb: FormBuilder,
    private faturaService: FaturaService,
    private snackBar: MatSnackBar,
    private dialogRef: MatDialogRef<NovaFaturaDialogComponent, boolean>,
    @Inject(MAT_DIALOG_DATA) public data: NovaFaturaDialogData
  ) {}

  ngOnInit(): void {
    const cartaoPadrao =
      this.data.cartoes[0]?.id != null ? String(this.data.cartoes[0].id) : '';
    this.form = this.fb.group({
      cartaoCreditoId: [cartaoPadrao, Validators.required],
      valor: ['', [Validators.required, Validators.min(0.01)]],
      vencimento: ['', Validators.required],
      fechamento: ['', Validators.required],
      status: ['PENDING', Validators.required],
    });
  }

  rotuloCartao(c: CartaoCredito): string {
    const nome = (c.nome || '').trim() || 'Cartão';
    const banco = (c.banco || '').trim();
    return banco ? `${nome} · ${banco}` : nome;
  }

  salvar(): void {
    if (this.form.invalid) {
      markAllControlsTouched(this.form);
      return;
    }

    const formValue = this.form.value;
    const cartaoCreditoId = Number(formValue.cartaoCreditoId);
    if (!cartaoCreditoId || Number.isNaN(cartaoCreditoId)) {
      this.snackBar.open('Selecione o cartão de crédito da fatura.', 'Fechar', {
        duration: 3500,
        panelClass: ['warning-snackbar'],
      });
      return;
    }

    const cartao = this.data.cartoes.find((c) => c.id === cartaoCreditoId);
    const payload: CreditCardInvoice = {
      cardId: String(cartaoCreditoId),
      bankName: cartao?.banco || cartao?.nome || '',
      amount: parseValorBrasileiro(formValue.valor) ?? formValue.valor,
      dueDate: formValue.vencimento,
      closingDate: formValue.fechamento,
      status: formValue.status,
      transactions: [],
    };

    this.salvando = true;
    this.faturaService.criarFaturaCartao(payload).subscribe({
      next: () => {
        this.salvando = false;
        this.snackBar.open('Fatura adicionada com sucesso!', 'Fechar', {
          duration: 3000,
          panelClass: ['success-snackbar'],
        });
        this.dialogRef.close(true);
      },
      error: (error) => {
        this.salvando = false;
        this.snackBar.open(
          resolveHttpError(error, 'Erro ao adicionar fatura. Verifique o cartão e tente novamente.'),
          'Fechar',
          { duration: 4000, panelClass: ['error-snackbar'] }
        );
      },
    });
  }
}
