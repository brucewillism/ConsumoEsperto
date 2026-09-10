import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import { MatTabsModule } from '@angular/material/tabs';
import { RouterLink } from '@angular/router';
import { Subscription, interval } from 'rxjs';
import {
  UsuarioService,
  VincularWhatsappResponse,
} from '../../services/usuario.service';
import {
  WhatsappConexaoService,
  WhatsappConexaoStatus,
} from '../../services/whatsapp-conexao.service';
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
import { EdithAdminStatus, EdithService } from '../../services/edith.service';

@Component({
  selector: 'app-whatsapp-config',
  standalone: true,
  imports: [CommonModule, FormsModule, CeInputMaskDirective, PageLoadingComponent, RouterLink, MatTabsModule],
  templateUrl: './whatsapp-config.component.html',
  styleUrl: './whatsapp-config.component.scss'
})
export class WhatsappConfigComponent implements OnInit, OnDestroy {
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
  /** true somente quando o backend informa papel ADMIN persistido. */
  isAdmin = false;

  edithAdmin: EdithAdminStatus | null = null;
  edithToggleLoading = false;

  conexaoStatus: WhatsappConexaoStatus | null = null;
  reconectandoAgora = false;
  pairingCode = '';
  pairingSegundosRestantes = 0;
  pairingGerando = false;
  abaPareamento = 0;

  private pairingTick?: Subscription;
  private statusPoll?: Subscription;

  constructor(
    private usuarioService: UsuarioService,
    private whatsappConexaoService: WhatsappConexaoService,
    private toastService: ToastService,
    private dialog: MatDialog,
    private paridadeService: WhatsappParidadeService,
    private edithService: EdithService,
  ) {}

  ngOnInit(): void {
    this.carregarPerfil();
    this.carregarParidade();
  }

  ngOnDestroy(): void {
    this.pairingTick?.unsubscribe();
    this.statusPoll?.unsubscribe();
  }

  carregarConexaoStatus(): void {
    this.whatsappConexaoService.getStatus().subscribe({
      next: (st) => {
        this.conexaoStatus = st;
        if (st.instanceName) {
          this.evolutionInstanceName = st.instanceName;
        }
        if (st.estado === 'open') {
          this.evolutionWaConnected = true;
          this.pararPairingCountdown();
          this.statusPoll?.unsubscribe();
          this.statusPoll = undefined;
        }
      },
      error: () => {
        this.conexaoStatus = null;
      },
    });
  }

  reconectarAgora(): void {
    this.reconectandoAgora = true;
    this.whatsappConexaoService.reconectarAgora().subscribe({
      next: (res) => {
        this.reconectandoAgora = false;
        if (res.conexao) {
          this.conexaoStatus = res.conexao;
        }
        this.atualizarStatusEvolution();
        this.carregarConexaoStatus();
        this.toastService.info('Tentativa de reconexão enviada à Evolution (sem apagar o pareamento).');
      },
      error: (error) => {
        this.reconectandoAgora = false;
        this.toastService.error(error?.error?.message || 'Falha ao pedir reconexão.');
      },
    });
  }

  gerarCodigoPareamento(): void {
    const numero = this.numeroWhatsapp.trim();
    if (!numero) {
      this.toastService.warning('Indique o número de WhatsApp (DDI 55 se for Brasil).');
      return;
    }
    this.pairingGerando = true;
    this.whatsappConexaoService.gerarPairingCode(numero).subscribe({
      next: (res) => {
        this.pairingGerando = false;
        if (res.alreadyConnected) {
          this.evolutionWaConnected = true;
          this.toastService.success('WhatsApp já está ligado nesta instância.');
          this.carregarConexaoStatus();
          return;
        }
        const code = (res.pairingCode || '').trim();
        if (!code) {
          this.toastService.warning(
            res.evolutionWarning || 'A Evolution não devolveu código. Use a aba Escanear QR.'
          );
          return;
        }
        this.pairingCode = code;
        this.iniciarPairingCountdown(res.validadeSegundos || 90);
        this.iniciarPollStatus();
        this.toastService.info('Digite o código no telemóvel. O código expira em cerca de 90 s.');
      },
      error: (error) => {
        this.pairingGerando = false;
        this.toastService.error(error?.error?.message || 'Não foi possível gerar o código.');
      },
    });
  }

  get pairingCodeFormatado(): string {
    const raw = this.pairingCode.replace(/\s+/g, '');
    if (raw.length === 8) {
      return raw.slice(0, 4) + ' ' + raw.slice(4);
    }
    return this.pairingCode;
  }

  private iniciarPairingCountdown(segundos: number): void {
    this.pararPairingCountdown();
    this.pairingSegundosRestantes = Math.max(1, segundos);
    this.pairingTick = interval(1000).subscribe(() => {
      this.pairingSegundosRestantes -= 1;
      if (this.pairingSegundosRestantes <= 0) {
        this.pararPairingCountdown();
        this.toastService.warning('O código expirou. Gere um novo se ainda não ligou.');
      }
    });
  }

  private pararPairingCountdown(): void {
    this.pairingTick?.unsubscribe();
    this.pairingTick = undefined;
    this.pairingSegundosRestantes = 0;
  }

  private iniciarPollStatus(): void {
    this.statusPoll?.unsubscribe();
    this.statusPoll = interval(5000).subscribe(() => {
      this.atualizarStatusEvolution();
      this.carregarConexaoStatus();
    });
  }

  estadoBadgeLabel(estado?: string): string {
    switch (estado) {
      case 'open':
        return 'Ligada';
      case 'connecting':
        return 'A ligar…';
      case 'close':
        return 'Desligada';
      case 'missing':
        return 'Instância ausente';
      case 'error':
        return 'Erro de rede';
      case 'suppressed':
        return 'Desligada na app';
      default:
        return estado || 'Desconhecido';
    }
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
        this.isAdmin = usuario.role === 'ADMIN';
        this.carregando = false;
        this.atualizarStatusEvolution();
        this.carregarConexaoStatus();
        this.carregarEdithAdmin();
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
        if (this.evolutionWaConnected) {
          this.pararPairingCountdown();
          this.statusPoll?.unsubscribe();
          this.statusPoll = undefined;
        }
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

  carregarEdithAdmin(): void {
    this.edithService.adminStatus().subscribe({
      next: (st) => (this.edithAdmin = st),
      error: () => {
        this.edithAdmin = null;
      },
    });
  }

  alternarEdith(): void {
    if (!this.edithAdmin || this.edithToggleLoading) {
      return;
    }
    const novoEstado = !this.edithAdmin.enabled;
    this.edithToggleLoading = true;
    this.edithService.setEnabled(novoEstado).subscribe({
      next: (st) => {
        this.edithAdmin = st;
        this.edithToggleLoading = false;
        this.toastService.success(
          st.enabled ? 'E.D.I.T.H. ligada neste servidor.' : 'E.D.I.T.H. desligada neste servidor.'
        );
      },
      error: (error) => {
        this.edithToggleLoading = false;
        this.toastService.error(error?.error?.message || 'Não foi possível alterar a E.D.I.T.H.');
        this.carregarEdithAdmin();
      },
    });
  }

  edithStateLabel(state: string | undefined): string {
    switch (state) {
      case 'AVAILABLE':
        return 'Disponível';
      case 'UNAVAILABLE':
        return 'Indisponível';
      default:
        return 'Desligada';
    }
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
