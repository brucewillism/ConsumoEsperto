import { Component, Inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatRadioModule } from '@angular/material/radio';
import type { ModoParcelamentoDelete } from '../models/transacao.model';

export interface ParcelamentoDeleteChoiceDialogData {
  descricao: string;
  parcelaAtual: number;
  totalParcelas: number;
}

@Component({
  selector: 'app-parcelamento-delete-choice-dialog',
  standalone: true,
  imports: [MatDialogModule, MatButtonModule, MatRadioModule, FormsModule],
  template: `
    <h2 mat-dialog-title>Excluir parcela</h2>
    <mat-dialog-content>
      <p class="msg">
        Esta transação faz parte de um parcelamento:
        <strong>{{ data.descricao }}</strong> ({{ data.parcelaAtual }}/{{ data.totalParcelas }}).
      </p>
      <p class="msg sub">Escolha o escopo da exclusão:</p>
      <mat-radio-group [(ngModel)]="modo" class="radio-group">
        <mat-radio-button value="UNICA">Apenas esta parcela</mat-radio-button>
        <mat-radio-button value="ESTA_E_PROXIMAS">Esta e as próximas</mat-radio-button>
        <mat-radio-button value="TODAS">Todo o parcelamento</mat-radio-button>
      </mat-radio-group>
    </mat-dialog-content>
    <mat-dialog-actions align="end" class="footer-actions">
      <button mat-button type="button" (click)="dialogRef.close(false)">Cancelar</button>
      <button mat-raised-button color="warn" type="button" (click)="dialogRef.close(modo)">Excluir</button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      .msg {
        margin: 0 0 0.75rem;
        white-space: pre-wrap;
      }
      .msg.sub {
        margin-bottom: 0.5rem;
        font-weight: 500;
      }
      .radio-group {
        display: flex;
        flex-direction: column;
        align-items: flex-start;
        gap: 0.65rem;
      }
      .footer-actions {
        padding: 0 1rem 1rem;
        gap: 0.5rem;
      }
    `
  ]
})
export class ParcelamentoDeleteChoiceDialogComponent {
  modo: ModoParcelamentoDelete = 'UNICA';

  constructor(
    public dialogRef: MatDialogRef<ParcelamentoDeleteChoiceDialogComponent, ModoParcelamentoDelete | false>,
    @Inject(MAT_DIALOG_DATA) public data: ParcelamentoDeleteChoiceDialogData
  ) {}
}
