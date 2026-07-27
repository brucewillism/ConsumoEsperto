import { Component, Inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { CE_DIALOG_IMPORTS } from '../ce-dialog-imports';
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
  /** Quando informada, o diálogo abre em modo edição. */
  fatura?: CreditCardInvoice;
}

@Component({
  selector: 'app-nova-fatura-dialog',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    ...CE_DIALOG_IMPORTS,
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
  readonly modoEdicao: boolean;

  constructor(
    private fb: FormBuilder,
    private faturaService: FaturaService,
    private snackBar: MatSnackBar,
    private dialogRef: MatDialogRef<NovaFaturaDialogComponent, boolean>,
    @Inject(MAT_DIALOG_DATA) public data: NovaFaturaDialogData
  ) {
    this.modoEdicao = !!data.fatura?.id;
  }

  ngOnInit(): void {
    const fatura = this.data.fatura;
    const cartaoPadrao = fatura?.cardId
      ? String(fatura.cardId)
      : this.data.cartoes[0]?.id != null
        ? String(this.data.cartoes[0].id)
        : '';
    this.form = this.fb.group({
      cartaoCreditoId: [cartaoPadrao, Validators.required],
      valor: [
        fatura ? this.formatarValorCampo(Number(fatura.amount) || 0) : '',
        [Validators.required, Validators.min(0.01)],
      ],
      vencimento: [fatura ? this.parseDataCampo(fatura.dueDate) : '', Validators.required],
      fechamento: [fatura ? this.parseDataCampo(fatura.closingDate) : '', Validators.required],
      status: [fatura?.status ?? 'PENDING', Validators.required],
    });
  }

  private parseDataCampo(raw: Date | string | undefined): Date | '' {
    if (!raw) {
      return '';
    }
    const d = raw instanceof Date ? raw : new Date(raw);
    return Number.isNaN(d.getTime()) ? '' : d;
  }

  private formatarValorCampo(valor: number): string {
    return valor.toLocaleString('pt-BR', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
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
      id: this.modoEdicao ? this.data.fatura!.id : undefined,
      numeroFatura: this.modoEdicao ? this.data.fatura!.numeroFatura : undefined,
      cardId: String(cartaoCreditoId),
      bankName: cartao?.banco || cartao?.nome || this.data.fatura?.bankName || '',
      amount: parseValorBrasileiro(formValue.valor) ?? formValue.valor,
      dueDate: formValue.vencimento,
      closingDate: formValue.fechamento,
      status: formValue.status,
      transactions: this.modoEdicao ? (this.data.fatura!.transactions ?? []) : [],
    };

    this.salvando = true;
    const req$ = this.modoEdicao
      ? this.faturaService.atualizarFaturaCartao(payload)
      : this.faturaService.criarFaturaCartao(payload);

    req$.subscribe({
      next: () => {
        this.salvando = false;
        this.snackBar.open(
          this.modoEdicao ? 'Fatura atualizada com sucesso!' : 'Fatura adicionada com sucesso!',
          'Fechar',
          {
            duration: 3000,
            panelClass: ['success-snackbar'],
          }
        );
        this.dialogRef.close(true);
      },
      error: (error) => {
        this.salvando = false;
        this.snackBar.open(
          resolveHttpError(
            error,
            this.modoEdicao
              ? 'Erro ao atualizar fatura. Tente novamente.'
              : 'Erro ao adicionar fatura. Verifique o cartão e tente novamente.'
          ),
          'Fechar',
          { duration: 4000, panelClass: ['error-snackbar'] }
        );
      },
    });
  }
}
