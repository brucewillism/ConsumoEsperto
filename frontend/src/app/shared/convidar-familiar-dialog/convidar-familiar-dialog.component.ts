import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { FamiliaService } from '../../services/familia.service';
import { ToastService } from '../../services/toast.service';
import { CeInputMaskDirective } from '../directives/ce-input-mask.directive';
import { isEmailValido, resolveHttpError } from '../utils/form.utils';

export interface ConvidarFamiliarDialogResult {
  conviteVisual: string;
}

@Component({
  selector: 'app-convidar-familiar-dialog',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatIconModule,
    CeInputMaskDirective,
  ],
  templateUrl: './convidar-familiar-dialog.component.html',
  styleUrl: './convidar-familiar-dialog.component.scss',
})
export class ConvidarFamiliarDialogComponent {
  conviteEmail = '';
  conviteWhatsapp = '';
  conviteErro = '';
  salvando = false;

  constructor(
    private familiaService: FamiliaService,
    private toast: ToastService,
    private dialogRef: MatDialogRef<ConvidarFamiliarDialogComponent, ConvidarFamiliarDialogResult | null>
  ) {}

  enviar(): void {
    this.conviteErro = '';
    const email = (this.conviteEmail || '').trim();
    const whatsapp = (this.conviteWhatsapp || '').trim();
    if (!email && !whatsapp) {
      this.conviteErro = 'Informe o e-mail ou o WhatsApp do convidado.';
      return;
    }
    if (email && !isEmailValido(email)) {
      this.conviteErro = 'Digite um e-mail válido.';
      return;
    }
    this.salvando = true;
    this.familiaService.convidar(this.conviteEmail, this.conviteWhatsapp).subscribe({
      next: () => {
        this.salvando = false;
        this.toast.success('Convite enviado.');
        const conviteVisual = `${window.location.origin}/familia?convite=${encodeURIComponent(email || whatsapp)}`;
        this.dialogRef.close({ conviteVisual });
      },
      error: (e) => {
        this.salvando = false;
        this.conviteErro = resolveHttpError(e, 'Erro ao enviar convite.');
      },
    });
  }
}
