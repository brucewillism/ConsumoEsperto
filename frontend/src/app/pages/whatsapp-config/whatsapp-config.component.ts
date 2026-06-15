import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import { RouterLink } from '@angular/router';
import {
  UsuarioService,
  VincularWhatsappResponse,
} from '../../services/usuario.service';
import { ToastService } from '../../services/toast.service';
import { Usuario } from '../../models/usuario.model';
import {
  WhatsappEvolutionQrDialogComponent,
  WhatsappEvolutionQrDialogData,
} from '../../shared/whatsapp-evolution-qr-dialog.component';
import { wireCeDialogBehavior } from '../../shared/ce-form-dialog.util';
import { CeInputMaskDirective } from '../../shared/directives/ce-input-mask.directive';
import { PageLoadingComponent } from '../../shared/page-loading/page-loading.component';
import { WhatsappParidadeService, WhatsappParityItem } from '../../services/whatsapp-paridade.service';

@Component({
  selector: 'app-whatsapp-config',
  standalone: true,
  imports: [CommonModule, FormsModule, CeInputMaskDirective, PageLoadingComponent, RouterLink],
  templateUrl: './whatsapp-config.component.html',
  styleUrl: './whatsapp-config.component.scss'
})
export class WhatsappConfigComponent implements OnInit {
  numeroWhatsapp = '';
  numeroAtual = '';
  /** Sessão real na Evolution (GET connectionState), distinto do número gravado na BD. */
  evolutionWaConnected: boolean | null = null;
  evolutionInstanceName = '';
  carregando = false;
  mensagemCarregamento = 'Carregando…';

  paridadeItens: WhatsappParityItem[] = [];
  paridadeCarregando = true;
  filtroParidade = '';

  constructor(
    private usuarioService: UsuarioService,
    private toastService: ToastService,
    private dialog: MatDialog,
    private paridadeService: WhatsappParidadeService,
  ) {}

  ngOnInit(): void {
    this.carregarPerfil();
    this.carregarParidade();
  }

  carregarParidade(): void {
    this.paridadeCarregando = true;
    this.paridadeService.listarTudo().subscribe({
      next: (res) => {
        this.paridadeItens = res.itens ?? [];
        this.paridadeCarregando = false;
      },
      error: () => {
        this.paridadeCarregando = false;
      },
    });
  }

  get paridadeFiltrada(): WhatsappParityItem[] {
    const q = this.filtroParidade.trim().toLowerCase();
    if (!q) {
      return this.paridadeItens;
    }
    return this.paridadeItens.filter((i) => {
      const blob = [
        i.titulo,
        i.menuApp,
        i.rotaApp,
        i.nota,
        ...(i.exemplosWhatsapp ?? []),
        ...(i.acoesApp ?? []),
      ]
        .join(' ')
        .toLowerCase();
      return blob.includes(q);
    });
  }

  canalLabel(canal: string): string {
    switch (canal) {
      case 'WHATSAPP_ONLY':
        return 'Só WhatsApp';
      case 'APP_ONLY':
        return 'Só no app';
      default:
        return 'App + WhatsApp';
    }
  }

  carregarPerfil(): void {
    this.mensagemCarregamento = 'Carregando dados do WhatsApp…';
    this.carregando = true;
    this.usuarioService.getUsuario().subscribe({
      next: (usuario: Usuario) => {
        this.numeroAtual = usuario.whatsappNumero || '';
        this.numeroWhatsapp = this.numeroAtual;
        this.carregando = false;
        this.atualizarStatusEvolution();
      },
      error: () => {
        this.carregando = false;
        this.toastService.error('Não foi possível carregar os dados do utilizador.');
      }
    });
  }

  atualizarStatusEvolution(): void {
    this.usuarioService.getEvolutionWhatsappConnectionStatus().subscribe({
      next: (st) => {
        const suppressed = st.sessionMarkedDisconnected === true;
        this.evolutionWaConnected = suppressed
          ? false
          : st.connected === true || st.evolutionWaConnected === true;
        if (st.instanceName) {
          this.evolutionInstanceName = st.instanceName;
        }
        if (st.whatsappNumero && !this.numeroAtual) {
          this.numeroAtual = st.whatsappNumero;
          this.numeroWhatsapp = st.whatsappNumero;
        }
      },
      error: () => {
        this.evolutionWaConnected = null;
      },
    });
  }

  vincular(): void {
    if (!this.numeroWhatsapp.trim()) {
      this.toastService.warning('Indique um número de WhatsApp para vincular.');
      return;
    }

    this.mensagemCarregamento = 'A vincular WhatsApp e preparar o QR Code…';
    this.carregando = true;
    this.usuarioService.vincularWhatsapp(this.numeroWhatsapp.trim()).subscribe({
      next: (response: VincularWhatsappResponse) => {
        this.numeroAtual = response?.whatsappNumero || this.numeroWhatsapp.trim();
        this.numeroWhatsapp = this.numeroAtual;
        const waOk = response?.evolutionWaConnected === true;
        const temQr =
          !!(response?.evolutionQrCodeDataUri?.trim() || response?.evolutionPairingCode?.trim());
        this.evolutionWaConnected = waOk;
        if (response?.evolutionInstanceName) {
          this.evolutionInstanceName = response.evolutionInstanceName;
        }
        this.carregando = false;

        if (waOk) {
          this.toastService.success(response?.message || 'WhatsApp vinculado com sucesso.');
        } else if (temQr) {
          this.toastService.info(
            response?.message || 'Escaneie o QR para ligar o WhatsApp à Evolution.'
          );
        } else {
          this.toastService.warning(
            response?.message || 'Número gravado; aguarde o QR ou tente de novo.'
          );
        }

        if (!waOk) {
          const dados: WhatsappEvolutionQrDialogData = {
            qrDataUri: response.evolutionQrCodeDataUri ?? null,
            pairingCode: response.evolutionPairingCode ?? null,
            instanceName: response.evolutionInstanceName ?? null,
            evolutionWarning: response.evolutionWarning ?? null,
            evolutionManagerUrl: response.evolutionManagerUrl ?? null,
          };
          const qrRef = this.dialog.open(WhatsappEvolutionQrDialogComponent, {
            width: '480px',
            maxWidth: '95vw',
            panelClass: 'ce-whatsapp-qr-dialog',
            data: dados,
            disableClose: true,
          });
          wireCeDialogBehavior(qrRef, () => 'dismissed' as const);
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

  desligarEvolution(): void {
    if (!this.numeroAtual) {
      this.toastService.info('Cadastre um número antes de desligar a Evolution.');
      return;
    }
    this.mensagemCarregamento = 'A desligar sessão Evolution…';
    this.carregando = true;
    this.usuarioService.desligarEvolutionWhatsapp().subscribe({
      next: (res) => {
        this.carregando = false;
        if (res.instanceName) {
          this.evolutionInstanceName = res.instanceName;
        }
        const parcial =
          res.status === 'warning' ||
          res.evolutionApiReportsOpen === true ||
          res.instanceDeleted === false ||
          res.instanceRotated === true;
        if (parcial) {
          this.evolutionWaConnected = false;
          this.toastService.warning(
            res.message ||
              'Sessão marcada como desligada na app; a Evolution pode precisar de Atualizar vínculo + QR novo.'
          );
        } else {
          this.evolutionWaConnected = false;
          this.toastService.success(res.message || 'Sessão Evolution desligada na app.');
        }
        this.atualizarStatusEvolution();
      },
      error: (error) => {
        this.carregando = false;
        this.toastService.error(error?.error?.message || 'Falha ao desligar Evolution.');
      },
    });
  }

  desvincular(): void {
    if (!this.numeroAtual) {
      this.toastService.info('Nenhum número vinculado para remover.');
      return;
    }

    this.mensagemCarregamento = 'A desvincular WhatsApp…';
    this.carregando = true;
    this.usuarioService.desvincularWhatsapp().subscribe({
      next: () => {
        this.numeroAtual = '';
        this.numeroWhatsapp = '';
        this.evolutionWaConnected = false;
        this.evolutionInstanceName = '';
        this.carregando = false;
        this.toastService.success('WhatsApp desvinculado. Número e sessão Evolution removidos.');
        this.atualizarStatusEvolution();
      },
      error: (error) => {
        this.carregando = false;
        const message = error?.error?.message || 'Falha ao desvincular WhatsApp.';
        this.toastService.error(message);
      }
    });
  }
}
