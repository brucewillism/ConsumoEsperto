import { Component, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { CE_DIALOG_IMPORTS } from '../ce-dialog-imports';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import {
  MetaFinanceiraRequest,
  MetaFinanceiraService,
  RendaMediaResponse,
} from '../../services/meta-financeira.service';
import { ToastService } from '../../services/toast.service';
import { CeInputMaskDirective } from '../directives/ce-input-mask.directive';
import { resolveHttpError } from '../utils/form.utils';

export interface NovaMetaDialogData {
  percentualSimulador: number;
  rendaInfo: RendaMediaResponse | null;
}

@Component({
  selector: 'app-nova-meta-dialog',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ...CE_DIALOG_IMPORTS,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    CeInputMaskDirective,
  ],
  templateUrl: './nova-meta-dialog.component.html',
  styleUrl: './nova-meta-dialog.component.scss',
})
export class NovaMetaDialogComponent {
  novaDescricao = '';
  novaValor = 0;
  novaPrioridade = 3;
  novaMetaAlerta = '';
  salvando = false;

  constructor(
    private metaService: MetaFinanceiraService,
    private toast: ToastService,
    private dialogRef: MatDialogRef<NovaMetaDialogComponent, boolean>,
    @Inject(MAT_DIALOG_DATA) public data: NovaMetaDialogData
  ) {}

  salvar(): void {
    this.novaMetaAlerta = '';
    if (!this.novaDescricao?.trim()) {
      this.novaMetaAlerta = 'Informe a descrição da meta (ex.: Geladeira, Viagem).';
      return;
    }
    if (!this.novaValor || this.novaValor <= 0) {
      this.novaMetaAlerta = 'Informe o valor total da meta, maior que zero.';
      return;
    }
    if (!this.data.rendaInfo?.calculadaDeLancamentos) {
      this.novaMetaAlerta =
        'Cadastre receitas nos últimos 3 meses para calcular a meta, ou informe sua renda pelo WhatsApp.';
      return;
    }
    const body: MetaFinanceiraRequest = {
      descricao: this.novaDescricao.trim(),
      valorTotal: this.novaValor,
      percentualComprometimento: this.data.percentualSimulador,
      prioridade: this.novaPrioridade,
    };
    this.salvando = true;
    this.metaService.criar(body).subscribe({
      next: (res) => {
        this.salvando = false;
        this.toast.success('Meta salva com sucesso.');
        if (res.alertaComprometimento) {
          this.toast.warning(res.alertaComprometimento);
        }
        this.dialogRef.close(true);
      },
      error: (e) => {
        this.salvando = false;
        this.novaMetaAlerta = resolveHttpError(e, 'Erro ao salvar meta.');
      },
    });
  }
}
