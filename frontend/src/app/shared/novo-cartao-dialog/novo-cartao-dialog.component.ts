import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatNativeDateModule } from '@angular/material/core';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { CartaoCreditoService } from '../../services/cartao-credito.service';
import { CartaoCredito, TipoCartao } from '../../models/cartao-credito.model';
import { BANCOS_BRASIL } from '../constants/bancos-brasil';
import { CeInputMaskDirective } from '../directives/ce-input-mask.directive';
import { parseValorBrasileiro } from '../utils/form.utils';

@Component({
  selector: 'app-novo-cartao-dialog',
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
  templateUrl: './novo-cartao-dialog.component.html',
  styleUrl: './novo-cartao-dialog.component.scss',
})
export class NovoCartaoDialogComponent implements OnInit {
  readonly bancosBrasil = BANCOS_BRASIL;
  form!: FormGroup;
  salvando = false;

  constructor(
    private fb: FormBuilder,
    private cartaoCreditoService: CartaoCreditoService,
    private snackBar: MatSnackBar,
    private dialogRef: MatDialogRef<NovoCartaoDialogComponent, boolean>
  ) {}

  ngOnInit(): void {
    this.form = this.fb.group({
      banco: ['', Validators.required],
      numero: ['', [Validators.required, Validators.minLength(4), Validators.maxLength(19)]],
      titular: ['', Validators.required],
      limite: [0, [Validators.required, Validators.min(0.01)]],
      vencimento: ['', Validators.required],
    });
  }

  salvar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.salvando = true;
    const cartaoForm = this.form.value;
    const limite = parseValorBrasileiro(cartaoForm.limite) ?? 0;
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
      limite,
      limiteUtilizado: 0,
    };

    this.cartaoCreditoService.criarCartaoCredito(payload).subscribe({
      next: () => {
        this.salvando = false;
        this.snackBar.open('Cartão cadastrado com sucesso!', 'Fechar', {
          duration: 3000,
          panelClass: ['success-snackbar'],
        });
        this.dialogRef.close(true);
      },
      error: (err) => {
        this.salvando = false;
        this.snackBar.open(this.mensagemErro(err), 'Fechar', {
          duration: 4000,
          panelClass: ['error-snackbar'],
        });
      },
    });
  }

  private mensagemErro(err: unknown): string {
    const body = (err as { error?: unknown })?.error;
    if (typeof body === 'string' && body.trim()) {
      return body;
    }
    if (body && typeof body === 'object' && 'message' in body) {
      return String((body as { message: string }).message);
    }
    return 'Erro ao cadastrar cartão.';
  }
}
