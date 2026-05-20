import { Component, Inject, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Subscription, timer, of } from 'rxjs';
import { catchError, map, switchMap } from 'rxjs/operators';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { UsuarioService } from '../services/usuario.service';
import { ToastService } from '../services/toast.service';

/** Dados para o modal de QR Evolution. */
export interface WhatsappEvolutionQrDialogData {
  qrDataUri?: string | null;
  pairingCode?: string | null;
  instanceName?: string | null;
  /** Aviso da primeira resposta; pode atualizar‑se até o QR chegar pelo polling */
  evolutionWarning?: string | null;
}

@Component({
  selector: 'app-whatsapp-evolution-qr-dialog',
  standalone: true,
  imports: [CommonModule, MatDialogModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>Pareamento WhatsApp (Evolution)</h2>

    <mat-dialog-content class="content">
      <p class="hint" *ngIf="displayInstance">
        Instância: <strong>{{ displayInstance }}</strong>
      </p>

      <p class="warn-banner" *ngIf="bannerWarning">{{ bannerWarning }}</p>

      <p class="instructions">
        No celular: WhatsApp → Aparelhos associados → Associar um aparelho →
        quando solicitado, leia este código QR ou use o código alfanumérico abaixo.
      </p>

      <p class="spinner-line" *ngIf="waitingForQr">À obter o QR Code da Evolution (tentativa contínua)…</p>

      <div class="qr-wrap" *ngIf="displayQr">
        <img class="qr" [src]="displayQr" alt="QR Code Evolution" />
      </div>

      <p class="pairing" *ngIf="displayPairing">
        <strong>Código de associação (alternativa):</strong> {{ displayPairing }}
      </p>

      <p class="polling" *ngIf="pollingHeartbeat">
        A verificar estado na Evolution a cada 5 s — pode fechar e completar no Manager da Evolution se preferir.
      </p>
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-button type="button" (click)="fechar()" [disabled]="fechando">Ignorar por agora</button>
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
      .warn-banner {
        background: rgba(255, 152, 0, 0.12);
        border: 1px solid rgba(255, 152, 0, 0.45);
        border-radius: 8px;
        padding: 0.65rem 0.85rem;
        font-size: 0.88rem;
        line-height: 1.35;
      }
      .instructions {
        font-size: 0.92rem;
        line-height: 1.35;
      }
      .spinner-line {
        font-size: 0.9rem;
        opacity: 0.82;
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
  pollingHeartbeat = true;
  fechando = false;

  displayInstance: string | null | undefined;
  displayQr: string | null | undefined;
  displayPairing: string | null | undefined;
  bannerWarning: string | null | undefined;

  get waitingForQr(): boolean {
    return !this.displayQr && !this.displayPairing;
  }

  private pollSub?: Subscription;
  private finishAlready = false;

  constructor(
    public dialogRef: MatDialogRef<WhatsappEvolutionQrDialogComponent, 'connected' | 'dismissed'>,
    @Inject(MAT_DIALOG_DATA) public data: WhatsappEvolutionQrDialogData,
    private usuarioService: UsuarioService,
    private toastService: ToastService
  ) {
    this.displayInstance = data.instanceName ?? null;
    this.displayQr = data.qrDataUri ?? undefined;
    this.displayPairing = data.pairingCode ?? undefined;
    this.bannerWarning = data.evolutionWarning ?? null;

    this.pollSub = timer(0, 5000)
      .pipe(
        switchMap(() =>
          this.usuarioService.getEvolutionWhatsappConnectionStatus().pipe(
            switchMap((status) => {
              if (status?.connected) {
                return of({ kind: 'connected' as const });
              }
              return this.usuarioService.refreshEvolutionPairing().pipe(
                map((pair) => ({ kind: 'pair' as const, pair })),
                catchError(() => of({ kind: 'pair-error' as const }))
              );
            })
          )
        )
      )
      .subscribe((outcome) => {
        if (outcome.kind === 'connected') {
          this.finishConnected();
          return;
        }
        if (outcome.kind === 'pair-error') {
          return;
        }
        const p = outcome.pair;
        if (p?.evolutionInstanceName) {
          this.displayInstance = p.evolutionInstanceName;
        }
        if (p?.evolutionAlreadyConnected) {
          this.finishConnected();
          return;
        }
        if (p?.evolutionQrCodeDataUri) {
          this.displayQr = p.evolutionQrCodeDataUri;
        }
        if (p?.evolutionPairingCode) {
          this.displayPairing = p.evolutionPairingCode;
        }
        if (this.displayQr || this.displayPairing) {
          this.bannerWarning = null;
        } else if (p?.evolutionWarning) {
          this.bannerWarning = p.evolutionWarning;
        }
      });
  }

  ngOnDestroy(): void {
    this.pollSub?.unsubscribe();
  }

  fechar(): void {
    this.fechando = true;
    this.dialogRef.close('dismissed');
  }

  private finishConnected(): void {
    if (this.finishAlready) {
      return;
    }
    this.finishAlready = true;
    this.pollSub?.unsubscribe();
    this.pollSub = undefined;
    this.pollingHeartbeat = false;
    this.toastService.success('WhatsApp pareado com sucesso na Evolution.');
    this.dialogRef.close('connected');
  }
}
