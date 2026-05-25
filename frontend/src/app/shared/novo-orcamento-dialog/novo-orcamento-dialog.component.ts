import { Component, Inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { Categoria } from '../../models/categoria.model';
import { OrcamentoService } from '../../services/orcamento.service';
import { ToastService } from '../../services/toast.service';
import {
  parseValorBrasileiro,
  resolveHttpError,
  sanitizeDecimalInput,
  sanitizeIntegerInput,
} from '../utils/form.utils';

export interface NovoOrcamentoDialogData {
  categorias: Categoria[];
  mes?: number;
  ano?: number;
}

@Component({
  selector: 'app-novo-orcamento-dialog',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatCheckboxModule,
    MatIconModule,
  ],
  templateUrl: './novo-orcamento-dialog.component.html',
  styleUrl: './novo-orcamento-dialog.component.scss',
})
export class NovoOrcamentoDialogComponent implements OnInit {
  categoriaId: number | null = null;
  valorLimiteInput = '';
  mesInput = '';
  anoInput = '';
  mes = 1;
  ano = new Date().getFullYear();
  compartilhado = false;
  formAlerta = '';
  salvando = false;

  constructor(
    private orcamentoService: OrcamentoService,
    private toast: ToastService,
    private dialogRef: MatDialogRef<NovoOrcamentoDialogComponent, boolean>,
    @Inject(MAT_DIALOG_DATA) public data: NovoOrcamentoDialogData
  ) {}

  ngOnInit(): void {
    const hoje = new Date();
    this.mes = this.data.mes ?? hoje.getMonth() + 1;
    this.ano = this.data.ano ?? hoje.getFullYear();
    this.mesInput = String(this.mes);
    this.anoInput = String(this.ano);
  }

  onValorLimiteInput(raw: string): void {
    this.valorLimiteInput = sanitizeDecimalInput(raw);
  }

  onMesInput(raw: string): void {
    this.mesInput = sanitizeIntegerInput(raw, 2);
  }

  onAnoInput(raw: string): void {
    this.anoInput = sanitizeIntegerInput(raw, 4);
  }

  salvar(): void {
    this.formAlerta = '';
    if (!this.categoriaId) {
      this.formAlerta = 'Selecione uma categoria.';
      return;
    }
    const valorLimite = parseValorBrasileiro(this.valorLimiteInput);
    if (valorLimite == null || valorLimite <= 0) {
      this.formAlerta = 'Informe um limite mensal maior que zero.';
      return;
    }
    const mes = parseInt(this.mesInput, 10);
    const ano = parseInt(this.anoInput, 10);
    if (!this.mesInput || !Number.isFinite(mes) || mes < 1 || mes > 12) {
      this.formAlerta = 'Informe um mês válido (1 a 12).';
      return;
    }
    if (!this.anoInput || !Number.isFinite(ano) || ano < 2000 || ano > 2100) {
      this.formAlerta = 'Informe um ano válido (2000 a 2100).';
      return;
    }
    this.mes = mes;
    this.ano = ano;
    this.salvando = true;
    this.orcamentoService
      .salvar({
        categoriaId: this.categoriaId,
        valorLimite,
        mes: this.mes,
        ano: this.ano,
        compartilhado: this.compartilhado,
      })
      .subscribe({
        next: () => {
          this.salvando = false;
          this.toast.success('Orçamento salvo.');
          this.dialogRef.close(true);
        },
        error: (e) => {
          this.salvando = false;
          this.formAlerta = resolveHttpError(e, 'Erro ao salvar orçamento.');
        },
      });
  }
}
