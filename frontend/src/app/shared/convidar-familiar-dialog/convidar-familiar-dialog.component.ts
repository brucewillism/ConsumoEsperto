import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
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
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatIconModule,
  ],
  templateUrl: './convidar-familiar-dialog.component.html',
  styleUrl: './convidar-familiar-dialog.component.scss',
})
export class ConvidarFamiliarDialogComponent {
  private readonly fb = inject(FormBuilder);
  private readonly familiaService = inject(FamiliaService);
  private readonly toast = inject(ToastService);
  private readonly dialogRef =
    inject(MatDialogRef<ConvidarFamiliarDialogComponent, ConvidarFamiliarDialogResult | null>);

  readonly form = this.fb.nonNullable.group({
    email: [''],
    whatsapp: [''],
  });

  conviteErro = '';
  salvando = false;

  onEmailInput(raw: string): void {
    this.form.controls.email.setValue(sanitizeEmailInput(raw), { emitEvent: false });
  }

  onWhatsappInput(raw: string): void {
    this.form.controls.whatsapp.setValue(formatPhoneBrDisplay(raw), { emitEvent: false });
  }

  enviar(): void {
    this.conviteErro = '';
    this.form.controls.email.setErrors(null);
    this.form.controls.whatsapp.setErrors(null);

    const email = sanitizeEmailInput(this.form.controls.email.value).trim();
    const whatsappDisplay = this.form.controls.whatsapp.value.trim();
    const temEmail = !!email;
    const temWhatsapp = isWhatsappBrValido(whatsappDisplay);

    if (!temEmail && !temWhatsapp) {
      this.conviteErro = 'Informe o e-mail ou o WhatsApp do convidado.';
      this.form.markAllAsTouched();
      return;
    }
    if (temEmail && !isEmailValido(email)) {
      this.form.controls.email.setErrors({ email: true });
      this.form.controls.email.markAsTouched();
      return;
    }
    if (!temEmail && whatsappDisplay && !temWhatsapp) {
      this.form.controls.whatsapp.setErrors({ telefone: true });
      this.form.controls.whatsapp.markAsTouched();
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
