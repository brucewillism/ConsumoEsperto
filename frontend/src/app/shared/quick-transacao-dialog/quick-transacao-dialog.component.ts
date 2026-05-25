import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { forkJoin } from 'rxjs';
import { Categoria } from '../../models/categoria.model';
import { CartaoCredito } from '../../models/cartao-credito.model';
import { TipoTransacao, Transacao } from '../../models/transacao.model';
import { CategoriaService } from '../../services/categoria.service';
import { CartaoCreditoService } from '../../services/cartao-credito.service';
import { TransacaoService } from '../../services/transacao.service';
import { CeInputMaskDirective } from '../directives/ce-input-mask.directive';
import {
  markAllControlsTouched,
  parseValorBrasileiro,
  resolveHttpError,
  valorMonetarioBrValidator,
} from '../utils/form.utils';

@Component({
  selector: 'app-quick-transacao-dialog',
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
    CeInputMaskDirective,
  ],
  templateUrl: './quick-transacao-dialog.component.html',
  styleUrl: './quick-transacao-dialog.component.scss',
})
export class QuickTransacaoDialogComponent implements OnInit {
  readonly tipoTransacaoEnum = TipoTransacao;
  form!: FormGroup;
  categorias: Categoria[] = [];
  cartoes: CartaoCredito[] = [];
  salvando = false;

  constructor(
    private fb: FormBuilder,
    private transacaoService: TransacaoService,
    private categoriaService: CategoriaService,
    private cartaoCreditoService: CartaoCreditoService,
    private snackBar: MatSnackBar,
    private dialogRef: MatDialogRef<QuickTransacaoDialogComponent, boolean>
  ) {}

  ngOnInit(): void {
    this.form = this.fb.group({
      descricao: ['', Validators.required],
      valor: ['', [Validators.required, valorMonetarioBrValidator]],
      tipoTransacao: [TipoTransacao.DESPESA, Validators.required],
      categoriaId: [''],
      cartaoCreditoId: [''],
    });
    this.form.get('tipoTransacao')?.valueChanges.subscribe((tipo) => {
      if (tipo === TipoTransacao.RECEITA) {
        this.form.patchValue({ cartaoCreditoId: '' }, { emitEvent: false });
      }
    });
    forkJoin({
      categorias: this.categoriaService.buscarTodas(),
      cartoes: this.cartaoCreditoService.buscarPorUsuario(),
    }).subscribe({
      next: ({ categorias, cartoes }) => {
        this.categorias = categorias ?? [];
        this.cartoes = (cartoes ?? []).filter((c) => c.id != null && c.ativo !== false);
      },
    });
  }

  salvar(): void {
    if (this.salvando || this.form.invalid) {
      markAllControlsTouched(this.form);
      return;
    }
    const raw = this.form.getRawValue();
    const valorNum = parseValorBrasileiro(raw.valor);
    if (valorNum == null || valorNum <= 0) {
      this.form.get('valor')?.setErrors({ valorInvalido: true });
      this.form.get('valor')?.markAsTouched();
      return;
    }

    const body: Transacao = {
      descricao: String(raw.descricao).trim(),
      valor: valorNum,
      tipoTransacao: raw.tipoTransacao as TipoTransacao,
      dataTransacao: new Date(),
    };
    const cat = raw.categoriaId;
    if (cat !== '' && cat != null) {
      body.categoriaId = Number(cat);
    }
    if (raw.tipoTransacao === TipoTransacao.DESPESA && raw.cartaoCreditoId !== '' && raw.cartaoCreditoId != null) {
      body.cartaoCreditoId = Number(raw.cartaoCreditoId);
    }

    this.salvando = true;
    this.transacaoService.criarTransacao(body).subscribe({
      next: () => {
        this.salvando = false;
        this.snackBar.open('Transação registrada com sucesso.', 'Fechar', {
          duration: 3000,
          panelClass: ['success-snackbar'],
        });
        this.dialogRef.close(true);
      },
      error: (err) => {
        this.salvando = false;
        this.snackBar.open(
          resolveHttpError(err, 'Não foi possível salvar o lançamento. Tente novamente.'),
          'Fechar',
          { duration: 4000, panelClass: ['error-snackbar'] }
        );
      },
    });
  }
}
