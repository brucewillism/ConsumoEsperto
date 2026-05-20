import { Component, Inject, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Subscription, timer } from 'rxjs';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { UsuarioService } from '../services/usuario.service';
import { ToastService } from '../services/toast.service';

/** Dados para o modal de QR Evolution. */
export interface WhatsappEvolutionQrDialogData {
  qrDataUri?: string | null;
  pairingCode?: string | null;
  instanceName?: string | null;
}

@Component({
  selector: 'app-whatsapp-evolution-qr-dialog',
  standalone: true,
  imports: [CommonModule, MatDialogModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>Pareamento WhatsApp (Evolution)</h2>

    <mat-dialog-content class="content">
      <p class="hint" *ngIf="data.instanceName">
        Instância: <strong>{{ data.instanceName }}</strong>
      </p>

      <p class="instructions">
        No celular: WhatsApp → Aparelhos associados → Associar um aparelho →
        quando solicitado, leia este código QR ou use o código alfanumérico abaixo.
      </p>

      <div class="qr-wrap" *ngIf="data.qrDataUri">
        <img class="qr" [src]="data.qrDataUri" alt="QR Code Evolution" />
      </div>

      <p class="pairing" *ngIf="data.pairingCode">
        <strong>Código de associação (alternativa):</strong> {{ data.pairingCode }}
      </p>

      <p class="polling" *ngIf="polling">
        À aguardar confirmação na Evolution… (verificação a cada 5 s)
      </p>
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-button type="button" (click)="fechar()" [disabled]="fechando">
        Ignorar por agora
      </button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      .content {
        min-width: 280px;
        max-width: 420px;
      }
      .hint {
        margin-top: 0;
        opacity: 0.85;
        font-size: 0.9rem;
      }
      .instructions {
        font-size: 0.92rem;
        line-height: 1.35;
      }
      .qr-wrap {
        margin: 1rem 0;
        text-align: center;
      }
      .qr {
        max-width: 100%;
        width: min(288px, 90vw);
        height: auto;
        border-radius: 8px;
        border: 1px solid rgba(0, 0, 0, 0.12);
      }
      .pairing {
        word-break: break-all;
        font-size: 0.95rem;
      }
      .polling {
        font-size: 0.88rem;
        opacity: 0.75;
        margin-bottom: 0;
      }
    `,
  ],
})
export class WhatsappEvolutionQrDialogComponent implements OnDestroy {
  polling = true;
  fechando = false;

  private pollSub?: Subscription;

  constructor(
    public dialogRef: MatDialogRef<WhatsappEvolutionQrDialogComponent, 'connected' | 'dismissed'>,
    @Inject(MAT_DIALOG_DATA) public data: WhatsappEvolutionQrDialogData,
    private usuarioService: UsuarioService,
    private toastService: ToastService
  ) {
    this.pollSub = timer(0, 5000).subscribe(() => this.checarConectado());
  }

  ngOnDestroy(): void {
    this.pollSub?.unsubscribe();
  }

  fechar(): void {
    this.fechando = true;
    this.dialogRef.close('dismissed');
  }

  private checarConectado(): void {
    this.usuarioService.getEvolutionWhatsappConnectionStatus().subscribe({
      next: (r) => {
        if (r?.connected) {
          this.pollSub?.unsubscribe();
          this.pollSub = undefined;
          this.polling = false;
          this.toastService.success(
            'WhatsApp pareado com sucesso na Evolution. Pode fechar esta janela.'
          );
          this.dialogRef.close('connected');
        }
      },
      error: () => {
        /* silencioso: Evolution ou rede indisponível durante polling */
      },
    });
  }
}
