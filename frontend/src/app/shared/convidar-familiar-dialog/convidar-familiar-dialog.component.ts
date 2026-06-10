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

  readonly sanitizeEmail = sanitizeEmailInput;
  readonly formatarWhatsapp = formatPhoneBrDisplay;

  onWhatsappInput(event: Event): void {
    const raw = (event.target as HTMLInputElement).value;
    this.conviteWhatsapp = formatPhoneBrDisplay(raw);
    (event.target as HTMLInputElement).value = this.conviteWhatsapp;
  }

  fechar(): void {
    this.dialogRef.close(null);
  }

  /** Lê o valor visível nos inputs (autofill/DOM) e sincroniza o modelo. */
  private lerCampos(): { email: string; whatsapp: string } {
    const emailEl = document.getElementById('convite-email') as HTMLInputElement | null;
    const whatsappEl = document.getElementById('convite-whatsapp') as HTMLInputElement | null;
    const email = sanitizeEmailInput(emailEl?.value ?? this.conviteEmail).trim();
    const whatsapp = formatPhoneBrDisplay(whatsappEl?.value ?? this.conviteWhatsapp).trim();
    this.conviteEmail = email;
    this.conviteWhatsapp = whatsapp;
    return { email, whatsapp };
  }

  enviar(): void {
    this.conviteErro = '';
    const { email, whatsapp: whatsappDisplay } = this.lerCampos();
    const temEmail = !!email;
    const temWhatsapp = isWhatsappBrValido(whatsappDisplay);

    if (!temEmail && !temWhatsapp) {
      this.conviteErro = 'Informe o e-mail ou o WhatsApp do convidado.';
      return;
    }
    if (temEmail && !isEmailValido(email)) {
      this.conviteErro = 'Digite um e-mail válido.';
      return;
    }
    if (!temEmail && whatsappDisplay && !temWhatsapp) {
      this.conviteErro = 'Informe o WhatsApp com DDD (mínimo 10 dígitos).';
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
        this.conviteErro = resolveHttpError(e, 'Erro ao enviar convite.');
      },
    });
  }
}
