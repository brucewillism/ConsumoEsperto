import { Component, Inject, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Subscription, timer, of } from 'rxjs';
import { catchError, map, switchMap } from 'rxjs/operators';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { CE_DIALOG_IMPORTS } from './ce-dialog-imports';
import { MatButtonModule } from '@angular/material/button';
import { UsuarioService } from '../services/usuario.service';
import { ToastService } from '../services/toast.service';
import {
  coerceEvolutionQrDataUri,
  coerceEvolutionUserFacingText,
} from './evolution-response-coerce';
import { LoadingIndicatorComponent } from '../components/loading-indicator/loading-indicator.component';

/** Dados para o modal de QR Evolution. */
export interface WhatsappEvolutionQrDialogData {
  qrDataUri?: string | null;
  pairingCode?: string | null;
  instanceName?: string | null;
  /** Aviso da primeira resposta; pode atualizar‑se até o QR chegar pelo polling */
  evolutionWarning?: string | null;
  evolutionManagerUrl?: string | null;
}

@Component({
  selector: 'app-whatsapp-evolution-qr-dialog',
  standalone: true,
  imports: [CommonModule, ...CE_DIALOG_IMPORTS, MatButtonModule, LoadingIndicatorComponent],
  template: `
    <h2 mat-dialog-title>Pareamento WhatsApp (Evolution)</h2>

    <mat-dialog-content class="content">
      <p class="hint" *ngIf="displayInstance">
        Instância: <strong>{{ displayInstance }}</strong>
      </p>

      <p class="warn-banner" *ngIf="safeBanner">{{ safeBanner }}</p>

      <p class="manager-link" *ngIf="managerUrl">
        <a [href]="managerUrl" target="_blank" rel="noopener noreferrer">Abrir Manager da Evolution</a>
        (instância <strong>{{ displayInstance }}</strong>).
        O QR desta app é o mesmo pedido REST do Manager — se lá ligar e aqui não, use <strong>Desligar Evolution</strong> e <strong>Atualizar vínculo</strong>.
      </p>

      <p class="instructions">
        No telemóvel: WhatsApp → <strong>Aparelhos ligados</strong> → <strong>Ligar um dispositivo</strong> (não use o menu do WhatsApp Web no PC).
        Leia este QR em até ~90 s sem fechar o modal. Se o scan falhar, use <strong>Ligar com número de telefone</strong> e o código abaixo.
      </p>
      <p class="instructions jarvis-hint">
        Depois do pareamento, fale com o J.A.R.V.I.S. na conversa <strong>consigo mesmo</strong> (chat «Eu»), com o número que vinculou na app.
      </p>

      <div class="qr-loading" *ngIf="waitingForQr">
        <app-loading-indicator mode="panel" size="md" message="A gerar QR Code na Evolution…"></app-loading-indicator>
      </div>

      <div class="qr-wrap" *ngIf="safeQrSrc">
        <img class="qr" [src]="safeQrSrc" alt="QR Code Evolution" />
      </div>

      <p class="pairing" *ngIf="safePairing">
        <strong>Código de associação (alternativa):</strong> {{ safePairing }}
      </p>

      <p class="polling" *ngIf="pollingHeartbeat">
        A verificar ligação a cada 5 s (o QR mantém-se ~30 s). Pode completar no Manager da Evolution se preferir.
      </p>
      <p class="polling slow-hint" *ngIf="pollAttempts >= 4 && waitingForQr">
        Se o QR não aparecer em ~1 minuto, use <strong>Desligar Evolution</strong> e <strong>Atualizar vínculo</strong> de novo,
        ou abra o Manager da Evolution (instância <code>{{ displayInstance || 'ce-u…' }}</code>).
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
      .manager-link {
        font-size: 0.9rem;
        line-height: 1.35;
        margin: 0 0 0.75rem;
      }
      .manager-link a {
        color: #7dd3fc;
      }
      .instructions {
        font-size: 0.92rem;
        line-height: 1.35;
      }
      .jarvis-hint {
        color: #7dd3fc;
        font-size: 0.88rem;
      }
      .qr-loading {
        margin: 0.5rem 0 1rem;
        min-height: 10rem;
      }
      .qr-wrap {
        margin: 1rem 0 1.25rem;
        text-align: center;
        display: inline-block;
        width: 100%;
      }
      .qr {
        max-width: 100%;
        width: min(288px, 90vw);
        height: auto;
        display: block;
        margin: 0 auto;
        border-radius: 8px;
        border: none;
        background: #fff;
        padding: 8px;
        box-sizing: content-box;
        image-rendering: pixelated;
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
      .slow-hint {
        opacity: 0.9;
        color: #fbbf24;
        line-height: 1.35;
      }
      .slow-hint code {
        font-size: 0.85em;
      }
    `,
  ],
})
export class WhatsappEvolutionQrDialogComponent implements OnDestroy {
  pollingHeartbeat = true;
  fechando = false;
  pollAttempts = 0;

  displayInstance: string | null | undefined;
  /** Só deve ser um data-uri ou URL string (nunca objeto). */
  displayQr: string | null | undefined;
  /** Estado bruto (object possível vindos da API); use safe* no template */
  displayPairingRaw: unknown = undefined;
  bannerRaw: unknown = null;
  managerUrl: string | null = null;

  get safeBanner(): string | undefined {
    return formatUnknownEvolutionBanner(this.bannerRaw);
  }

  get safePairing(): string | undefined {
    return coerceEvolutionUserFacingText(this.displayPairingRaw);
  }

  get safeQrSrc(): string | undefined {
    const d = typeof this.displayQr === 'string' ? this.displayQr.trim() : '';
    if (!d || d === '[object Object]' || d.includes('[object Object]')) {
      return undefined;
    }
    if (d.startsWith('data:image/') || d.startsWith('blob:') || d.startsWith('http')) {
      return d;
    }
    return undefined;
  }

  get waitingForQr(): boolean {
    return !this.safeQrSrc && !this.safePairing;
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
    this.displayQr = coerceEvolutionQrDataUri(data.qrDataUri ?? undefined);
    this.displayPairingRaw = data.pairingCode ?? undefined;
    this.bannerRaw = data.evolutionWarning ?? null;
    this.managerUrl =
      typeof data.evolutionManagerUrl === 'string' && data.evolutionManagerUrl.trim()
        ? data.evolutionManagerUrl.trim()
        : null;

    // Estado WA a cada 5 s; novo QR só se ainda não houver imagem ou a cada ~30 s (evita invalidar QR a meio do scan).
    this.pollSub = timer(0, 5000)
      .pipe(
        switchMap(() =>
          this.usuarioService.getEvolutionWhatsappConnectionStatus().pipe(
            switchMap((status) => {
              const reallyConnected =
                (status?.connected === true || status?.evolutionWaConnected === true) &&
                status?.sessionMarkedDisconnected !== true;
              if (reallyConnected) {
                return of({ kind: 'connected' as const });
              }
              const needPairingRefresh =
                !this.safeQrSrc && !this.safePairing
                  ? true
                  : this.pollAttempts > 0 && this.pollAttempts % 6 === 0;
              if (!needPairingRefresh) {
                return of({ kind: 'waiting' as const });
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
        this.pollAttempts += 1;
        if (outcome.kind === 'connected') {
          this.finishConnected();
          return;
        }
        if (outcome.kind === 'pair-error') {
          if (this.pollAttempts <= 2) {
            this.toastService.warning(
              'Falha temporária ao pedir QR à Evolution. A tentar de novo…'
            );
          }
          return;
        }
        if (outcome.kind === 'waiting') {
          return;
        }
        const p = outcome.pair;
        if (p?.evolutionInstanceName) {
          this.displayInstance = p.evolutionInstanceName;
        }
        const refreshConnected =
          p?.evolutionWaConnected === true &&
          p?.sessionMarkedDisconnected !== true;
        if (refreshConnected) {
          this.finishConnected();
          return;
        }
        const qrImg = coerceEvolutionQrDataUri(p?.evolutionQrCodeDataUri ?? undefined);
        if (qrImg && qrImg !== this.displayQr) {
          this.displayQr = qrImg;
        }
        const pairTxt =
          coerceEvolutionUserFacingText(p?.evolutionPairingCode ?? undefined);
        if (pairTxt) {
          this.displayPairingRaw = pairTxt;
        }
        if (this.safeQrSrc || this.safePairing) {
          this.bannerRaw = null;
        } else if (p?.evolutionWarning != null) {
          this.bannerRaw = p.evolutionWarning;
        }
        if (p?.evolutionManagerUrl?.trim()) {
          this.managerUrl = p.evolutionManagerUrl.trim();
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

/** Nunca devolve texto coercível a "[object Object]" no DOM. */
function formatUnknownEvolutionBanner(value: unknown): string | undefined {
  if (value == null || value === '') {
    return undefined;
  }
  if (typeof value === 'string') {
    const t = value.trim();
    if (!t.length || /^[\s]*\[object Object\][\s]*$/i.test(t)) {
      return undefined;
    }
    return t;
  }
  const coerced = coerceEvolutionUserFacingText(value);
  if (coerced?.trim() && coerced.trim() !== '[object Object]') {
    return coerced.trim();
  }
  try {
    return JSON.stringify(value);
  } catch {
    return 'A Evolution devolveu um aviso neste formato; abra os detalhes no Manager ou nos logs.';
  }
}
