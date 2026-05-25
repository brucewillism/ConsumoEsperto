import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import { UsuarioService, VincularWhatsappResponse } from '../../services/usuario.service';
import { ToastService } from '../../services/toast.service';
import { Usuario } from '../../models/usuario.model';
import {
  WhatsappEvolutionQrDialogComponent,
  WhatsappEvolutionQrDialogData,
} from '../../shared/whatsapp-evolution-qr-dialog.component';
import { CeInputMaskDirective } from '../../shared/directives/ce-input-mask.directive';

@Component({
  selector: 'app-whatsapp-config',
  standalone: true,
  imports: [CommonModule, FormsModule, CeInputMaskDirective],
  templateUrl: './whatsapp-config.component.html',
  styleUrl: './whatsapp-config.component.scss'
})
export class WhatsappConfigComponent implements OnInit {
  numeroWhatsapp = '';
  numeroAtual = '';
  carregando = false;

  constructor(
    private usuarioService: UsuarioService,
    private toastService: ToastService,
    private dialog: MatDialog,
  ) {}

  ngOnInit(): void {
    this.carregarPerfil();
  }

  carregarPerfil(): void {
    this.carregando = true;
    this.usuarioService.getUsuario().subscribe({
      next: (usuario: Usuario) => {
        this.numeroAtual = usuario.whatsappNumero || '';
        this.numeroWhatsapp = this.numeroAtual;
        this.carregando = false;
      },
      error: () => {
        this.carregando = false;
        this.toastService.error('Não foi possível carregar os dados do utilizador.');
      }
    });
  }

  vincular(): void {
    if (!this.numeroWhatsapp.trim()) {
      this.toastService.warning('Indique um número de WhatsApp para vincular.');
      return;
    }

    this.carregando = true;
    this.usuarioService.vincularWhatsapp(this.numeroWhatsapp.trim()).subscribe({
      next: (response: VincularWhatsappResponse) => {
        this.numeroAtual = response?.whatsappNumero || this.numeroWhatsapp.trim();
        this.numeroWhatsapp = this.numeroAtual;
        this.carregando = false;

        const baseMsg =
          response?.message || 'WhatsApp vinculado com sucesso.';
        this.toastService.success(baseMsg);

        const faltaEvolution = !response?.evolutionAlreadyConnected;
        /** Modal com QR ou polling até a Evolution responder (evita só um toast quando o QR vem mais tarde). */
        if (faltaEvolution) {
          const dados: WhatsappEvolutionQrDialogData = {
            qrDataUri: response.evolutionQrCodeDataUri ?? null,
            pairingCode: response.evolutionPairingCode ?? null,
            instanceName: response.evolutionInstanceName ?? null,
            evolutionWarning: response.evolutionWarning ?? null,
          };
          this.dialog.open(WhatsappEvolutionQrDialogComponent, {
            width: '480px',
            maxWidth: '95vw',
            data: dados,
          });
        } else if (response?.evolutionWarning) {
          this.toastService.warning(response.evolutionWarning);
        }
      },
      error: (error) => {
        this.carregando = false;
        const message = error?.error?.message || 'Falha ao vincular WhatsApp.';
        this.toastService.error(message);
      }
    });
  }

  desvincular(): void {
    if (!this.numeroAtual) {
      this.toastService.info('Nenhum número vinculado para remover.');
      return;
    }

    this.carregando = true;
    this.usuarioService.desvincularWhatsapp().subscribe({
      next: () => {
        this.numeroAtual = '';
        this.numeroWhatsapp = '';
        this.carregando = false;
        this.toastService.success('WhatsApp desvinculado com sucesso.');
      },
      error: (error) => {
        this.carregando = false;
        const message = error?.error?.message || 'Falha ao desvincular WhatsApp.';
        this.toastService.error(message);
      }
    });
  }
}
