import { Component, Inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

export interface ConfirmDialogData {
  title: string;
  message: string;
  confirmLabel?: string;
  cancelLabel?: string;
  /** true = botão de confirmação em tom de alerta (ex.: apagar) */
  destructive?: boolean;
}

@Component({
  selector: 'app-confirm-dialog',
  standalone: true,
  imports: [MatDialogModule, MatButtonModule, MatIconModule],
  template: `
    <div class="jarvis-confirm-shell">
      <div class="jarvis-confirm-icon" [class.destructive]="data.destructive">
        <mat-icon>{{ data.destructive ? 'warning_amber' : 'help_outline' }}</mat-icon>
      </div>
      <div class="jarvis-confirm-body">
        <h2 mat-dialog-title>{{ data.title }}</h2>
        <mat-dialog-content>
          <p class="msg">{{ data.message }}</p>
        </mat-dialog-content>
      </div>
      <mat-dialog-actions align="end" class="jarvis-confirm-actions">
        <button mat-stroked-button type="button" class="btn-cancel" [mat-dialog-close]="false">
          {{ data.cancelLabel || 'Cancelar' }}
        </button>
        <button
          mat-raised-button
          type="button"
          class="btn-confirm"
          [class.destructive]="data.destructive"
          [color]="data.destructive ? 'warn' : 'primary'"
          [mat-dialog-close]="true">
          {{ data.confirmLabel || 'Confirmar' }}
        </button>
      </mat-dialog-actions>
    </div>
  `,
  styles: [
    `
      .jarvis-confirm-shell {
        display: flex;
        flex-direction: column;
        gap: 4px;
        padding: 4px 2px 0;
      }

      .jarvis-confirm-icon {
        display: flex;
        align-items: center;
        justify-content: center;
        width: 48px;
        height: 48px;
        border-radius: 14px;
        background: rgba(0, 229, 255, 0.12);
        border: 1px solid rgba(0, 229, 255, 0.35);
        color: #00e5ff;
        margin-bottom: 4px;
      }

      .jarvis-confirm-icon.destructive {
        background: rgba(245, 158, 11, 0.12);
        border-color: rgba(245, 158, 11, 0.45);
        color: #fbbf24;
      }

      .jarvis-confirm-icon mat-icon {
        font-size: 28px;
        width: 28px;
        height: 28px;
      }

      .jarvis-confirm-body h2 {
        margin: 0 0 8px;
        font-size: 1.15rem;
        font-weight: 600;
        letter-spacing: 0.01em;
      }

      .msg {
        margin: 0;
        white-space: pre-wrap;
        line-height: 1.5;
        color: var(--text-secondary, #94a3b8);
        font-size: 0.95rem;
      }

      .jarvis-confirm-actions {
        margin-top: 8px;
        padding-top: 14px !important;
        gap: 10px;
      }

      .btn-cancel {
        border-color: rgba(148, 163, 184, 0.35) !important;
      }
    `
  ]
})
export class ConfirmDialogComponent {
  constructor(
    public dialogRef: MatDialogRef<ConfirmDialogComponent, boolean>,
    @Inject(MAT_DIALOG_DATA) public data: ConfirmDialogData
  ) {}
}
