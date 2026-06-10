import { Component, inject, ViewEncapsulation } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { FamiliaService } from '../../services/familia.service';
import { ToastService } from '../../services/toast.service';
import {
  formatPhoneBrDisplay,
  isEmailValido,
  isWhatsappBrValido,
  normalizePhoneBrE164,
  resolveHttpError,
  sanitizeEmailInput,
} from '../utils/form.utils';

export interface ConvidarFamiliarDialogResult {
  conviteVisual: string;
}

@Component({
  selector: 'app-convidar-familiar-dialog',
  standalone: true,
  imports: [CommonModule, FormsModule, MatDialogModule],
  templateUrl: './convidar-familiar-dialog.component.html',
  styleUrl: './convidar-familiar-dialog.component.scss',
  encapsulation: ViewEncapsulation.None,
})
export class ConvidarFamiliarDialogComponent {
  private readonly familiaService = inject(FamiliaService);
  private readonly toast = inject(ToastService);
  private readonly dialogRef =
    inject(MatDialogRef<ConvidarFamiliarDialogComponent, ConvidarFamiliarDialogResult | null>);

  conviteEmail = '';
  conviteWhatsapp = '';
  conviteErro = '';
  salvando = false;

  onEmailInput(raw: string): void {
    this.conviteEmail = sanitizeEmailInput(raw);
  }

  onWhatsappInput(raw: string): void {
    this.conviteWhatsapp = formatPhoneBrDisplay(raw);
  }

  fechar(): void {
    this.dialogRef.close(null);
  }

  enviar(): void {
    this.conviteErro = '';

    const email = sanitizeEmailInput(this.conviteEmail).trim();
    const whatsappDisplay = (this.conviteWhatsapp || '').trim();
    const temEmail = !!email;
    const temWhatsapp = isWhatsappBrValido(whatsappDisplay);

    if (!temEmail && !temWhatsapp) {
      const msg = 'Informe o e-mail ou o WhatsApp do convidado.';
      this.conviteErro = msg;
      this.toast.warning(msg);
      return;
    }
    if (temEmail && !isEmailValido(email)) {
      const msg = 'Digite um e-mail válido.';
      this.conviteErro = msg;
      this.toast.warning(msg);
      return;
    }
    if (!temEmail && whatsappDisplay && !temWhatsapp) {
      const msg = 'Informe o WhatsApp com DDD (mínimo 10 dígitos).';
      this.conviteErro = msg;
      this.toast.warning(msg);
      return;
    }

    const whatsappApi = temWhatsapp ? normalizePhoneBrE164(whatsappDisplay) : '';

    this.salvando = true;
    this.familiaService.convidar(email, whatsappApi).subscribe({
      next: () => {
        this.salvando = false;
        this.toast.success('Convite enviado.');
        const conviteVisual = `${window.location.origin}/familia?convite=${encodeURIComponent(email || whatsappApi)}`;
        this.dialogRef.close({ conviteVisual });
      },
      error: (e) => {
        this.salvando = false;
        const msg = resolveHttpError(e, 'Erro ao enviar convite.');
        this.conviteErro = msg;
        this.toast.error(msg);
      },
    });
  }
}
