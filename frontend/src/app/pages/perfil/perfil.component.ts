import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { UsuarioService, JarvisNotificacaoPreferencias } from '../../services/usuario.service';
import { AuthService } from '../../services/auth.service';
import { ConfirmDialogService } from '../../services/confirm-dialog.service';
import { ToastService } from '../../services/toast.service';
import { PreferenciaTratamentoJarvis, Usuario } from '../../models/usuario.model';
import { GoogleCalendarLinkService } from '../../services/google-calendar-link.service';
import { DespesaFixa, DespesasFixaService } from '../../services/despesas-fixa.service';
import { ContaBancariaService } from '../../services/conta-bancaria.service';
import { ContaBancaria } from '../../models/conta-bancaria.model';
import { WhatsappParityHintComponent } from '../../shared/whatsapp-parity-hint/whatsapp-parity-hint.component';
import {
  MobileCaptureDevice,
  MobileCaptureService,
  MobileDeviceRegistration,
  MobilePlatform,
} from '../../services/mobile-capture.service';
import { resolveHttpError } from '../../shared/utils/form.utils';

@Component({
  selector: 'app-perfil',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, WhatsappParityHintComponent],
  templateUrl: './perfil.component.html',
  styleUrl: './perfil.component.scss',
})
export class PerfilComponent implements OnInit {
  usuario: Usuario | null = null;
  carregando = false;
  modalAberto = false;
  escolhaModal: PreferenciaTratamentoJarvis = 'AUTOMATICO';

  fixas: DespesaFixa[] = [];
  carregandoFixas = false;
  modalFixasAberto = false;
  editandoFixa: DespesaFixa | null = null;
  formFixa: DespesaFixa = this.novaFixaVazia();
  fixaErro = '';
  contas: ContaBancaria[] = [];

  readonly opcoes: { value: PreferenciaTratamentoJarvis; label: string; hint?: string }[] = [
    {
      value: 'AUTOMATICO',
      label: 'Automático (inferência pelo primeiro nome)',
      hint: 'O J.A.R.V.I.S. deduz o tratamento pelo nome até você definir uma opção fixa.',
    },
    { value: 'SENHOR', label: 'Senhor' },
    { value: 'SENHORA', label: 'Senhora' },
    { value: 'DOUTOR', label: 'Doutor' },
    { value: 'DOUTORA', label: 'Doutora' },
    { value: 'NENHUM', label: 'Sem título (apenas o primeiro nome)' },
  ];

  linhaTratamento = '';

  notifPrefs: JarvisNotificacaoPreferencias = {};
  notifPrefsCarregando = false;
  notifPrefsSalvando = false;

  readonly notifOpcoes: {
    key: keyof JarvisNotificacaoPreferencias;
    label: string;
    hint: string;
  }[] = [
    {
      key: 'alertaRiscoReativo',
      label: 'Alerta após despesa (Sentinela + forecast)',
      hint: 'Quando uma despesa confirmada indica risco no caixa — como o alerta financeiro proativo.',
    },
    {
      key: 'digestMensalSentinela',
      label: 'Digest mensal Sentinela (dia 1, 18h15)',
      hint: 'Panorama mensal: património, margem Sentinela e projeção de fechamento.',
    },
    {
      key: 'relatorioMensalScore',
      label: 'Relatório mensal de score (dia 1, 18h30)',
      hint: 'Resultado líquido do mês anterior e pontuação de saúde financeira.',
    },
    {
      key: 'sentinelaDia5',
      label: 'Disponibilidade real (dia 5, 09h30)',
      hint: 'Relatório Sentinela após obrigações do início do mês.',
    },
    {
      key: 'resumoSemanal',
      label: 'Resumo semanal (domingo 18h)',
      hint: 'Gastos da semana, maior despesa e orçamentos em atenção.',
    },
    {
      key: 'recorrenciasVencimento',
      label: 'Vencimentos e recorrências (diário 08h)',
      hint: 'Contas a vencer, assinaturas e alertas de liquidez.',
    },
    {
      key: 'conferenciaNotas',
      label: 'Lembretes de conferência (diário 10h)',
      hint: 'Lançamentos pendentes de confirmação no painel.',
    },
    {
      key: 'amortizacaoSazonal',
      label: 'Oportunidade debt snowball (segunda 10h)',
      hint: 'Sugestões de amortização antes de receitas fiscais (13º/IR).',
    },
    {
      key: 'modoViagemCronos',
      label: 'Modo Viagem — Cronos (segunda 10h)',
      hint: 'Sugestões com base na agenda Google, quando vinculada.',
    },
  ];

  vinculandoCalendar = false;

  mobileDevices: MobileCaptureDevice[] = [];
  mobileCarregando = false;
  mobileModalAberto = false;
  mobileCredenciais: MobileDeviceRegistration | null = null;
  mobileNomeNovo = '';
  mobilePlataforma: MobilePlatform = 'ANDROID_MACRODROID';

  constructor(
    private usuarioService: UsuarioService,
    private authService: AuthService,
    private toastService: ToastService,
    private confirmDialog: ConfirmDialogService,
    private googleCalendarLink: GoogleCalendarLinkService,
    private despesasFixaService: DespesasFixaService,
    private contaBancariaService: ContaBancariaService,
    private mobileCaptureService: MobileCaptureService
  ) {}

  ngOnInit(): void {
    this.carregar();
  }

  carregar(): void {
    this.carregando = true;
    this.usuarioService.getUsuario().subscribe({
      next: (u) => {
        this.usuario = u;
        this.linhaTratamento = u.jarvisTratamentoResumo || '';
        this.escolhaModal = u.preferenciaTratamentoJarvis || 'AUTOMATICO';
        this.carregando = false;
        this.carregarFixas();
        this.carregarNotifPrefs();
        this.carregarMobileDevices();
      },
      error: () => {
        this.carregando = false;
        this.toastService.error('Não foi possível carregar o perfil.');
      },
    });
  }

  carregarNotifPrefs(): void {
    this.notifPrefsCarregando = true;
    this.usuarioService.getJarvisNotificacoesPreferencias().subscribe({
      next: (prefs) => {
        this.notifPrefs = { canalEntrega: 'WHATSAPP', ...prefs };
        this.notifPrefsCarregando = false;
      },
      error: () => {
        this.notifPrefsCarregando = false;
      },
    });
  }

  salvarNotifPrefs(): void {
    this.notifPrefsSalvando = true;
    this.usuarioService.salvarJarvisNotificacoesPreferencias(this.notifPrefs).subscribe({
      next: (prefs) => {
        this.notifPrefs = { canalEntrega: 'WHATSAPP', ...prefs };
        this.notifPrefsSalvando = false;
        this.toastService.success('Preferências de notificação guardadas.');
      },
      error: () => {
        this.notifPrefsSalvando = false;
        this.toastService.error('Não foi possível guardar as preferências.');
      },
    });
  }

  abrirModal(): void {
    const u = this.authService.getCurrentUser();
    this.escolhaModal =
      u?.preferenciaTratamentoJarvis ||
      this.usuario?.preferenciaTratamentoJarvis ||
      'AUTOMATICO';
    this.modalAberto = true;
  }

  fecharModal(): void {
    this.modalAberto = false;
  }

  salvarPreferencia(): void {
    this.carregando = true;
    this.usuarioService.patchPreferenciaTratamento(this.escolhaModal).subscribe({
      next: (dto: Usuario) => {
        this.authService.applyPerfilResponse(dto);
        this.usuario = this.authService.getCurrentUser();
        this.linhaTratamento = dto.jarvisTratamentoResumo || '';
        this.modalAberto = false;
        this.carregando = false;
        this.toastService.success('Tratamento do J.A.R.V.I.S. atualizado.');
      },
      error: () => {
        this.carregando = false;
        this.toastService.error('Não foi possível salvar a preferência.');
      },
    });
  }

  textoCard(): string {
    const r = this.linhaTratamento || this.usuario?.jarvisTratamentoResumo || '…';
    return `Tratamento do J.A.R.V.I.S.: ${r}`;
  }

  private novaFixaVazia(): DespesaFixa {
    return {
      descricao: '',
      valor: 0,
      diaVencimento: 1,
      categoria: 'Obrigações fixas',
      debitoAutomatico: false,
      contaBancariaId: null,
    };
  }

  private carregarContas(): void {
    this.contaBancariaService.listarContasAtivas().subscribe({
      next: (list) => (this.contas = list),
      error: () => (this.contas = []),
    });
  }

  carregarFixas(): void {
    this.carregandoFixas = true;
    this.despesasFixaService.listar().subscribe({
      next: (list) => {
        this.fixas = list;
        this.carregandoFixas = false;
      },
      error: () => {
        this.carregandoFixas = false;
        this.toastService.error('Não foi possível carregar obrigações fixas.');
      },
    });
  }

  abrirModalFixas(): void {
    this.editandoFixa = null;
    this.formFixa = this.novaFixaVazia();
    this.fixaErro = '';
    this.modalFixasAberto = true;
    this.carregarContas();
  }

  fecharModalFixas(): void {
    this.modalFixasAberto = false;
    this.editandoFixa = null;
  }

  editarFixa(f: DespesaFixa): void {
    this.editandoFixa = f;
    this.fixaErro = '';
    this.formFixa = {
      id: f.id,
      descricao: f.descricao,
      valor: f.valor,
      diaVencimento: f.diaVencimento,
      categoria: f.categoria || 'Obrigações fixas',
      debitoAutomatico: !!f.debitoAutomatico,
      contaBancariaId: f.contaBancariaId ?? null,
    };
    this.modalFixasAberto = true;
    this.carregarContas();
  }

  salvarFixa(): void {
    this.fixaErro = '';
    if (!this.formFixa.descricao?.trim()) {
      this.fixaErro = 'Informe a descrição da obrigação (ex.: Aluguel, Internet).';
      return;
    }
    if (!this.formFixa.valor || this.formFixa.valor <= 0) {
      this.fixaErro = 'Informe um valor mensal maior que zero.';
      return;
    }
    const dia = Math.floor(Number(this.formFixa.diaVencimento)) || 1;
    if (dia < 1 || dia > 31) {
      this.fixaErro = 'O dia de vencimento deve estar entre 1 e 31.';
      return;
    }
    this.formFixa.diaVencimento = Math.min(31, Math.max(1, dia));
    this.carregandoFixas = true;
    if (this.editandoFixa?.id != null) {
      this.despesasFixaService.atualizar(this.editandoFixa.id, this.formFixa).subscribe({
        next: () => {
          this.toastService.success('Obrigação fixa atualizada.');
          this.fecharModalFixas();
          this.carregarFixas();
        },
        error: (err) => {
          this.carregandoFixas = false;
          this.fixaErro = resolveHttpError(err, 'Não foi possível atualizar a obrigação fixa.');
        },
      });
      return;
    }
    this.despesasFixaService.criar(this.formFixa).subscribe({
      next: () => {
        this.toastService.success('Obrigação fixa criada.');
        this.fecharModalFixas();
        this.carregarFixas();
      },
      error: (err) => {
        this.carregandoFixas = false;
        this.fixaErro = resolveHttpError(err, 'Não foi possível criar a obrigação fixa.');
      },
    });
  }

  excluirFixa(f: DespesaFixa): void {
    if (f.id == null) {
      return;
    }
    this.confirmDialog.ask({
      title: 'Remover obrigação fixa',
      message: `Remover "${f.descricao}" das suas obrigações fixas?`,
      confirmLabel: 'Remover',
      destructive: true,
    }).subscribe((ok) => {
      if (!ok) {
        return;
      }
      this.carregandoFixas = true;
      this.despesasFixaService.excluir(f.id!).subscribe({
        next: () => {
          this.toastService.success('Removido.');
          this.carregarFixas();
        },
        error: () => {
          this.carregandoFixas = false;
        },
      });
    });
  }

  vincularGoogleCalendar(): void {
    this.vinculandoCalendar = true;
    this.googleCalendarLink.iniciarVinculacao().subscribe({
      next: (r) => {
        this.vinculandoCalendar = false;
        if (r?.authorizationUrl) {
          window.location.href = r.authorizationUrl;
        } else {
          this.toastService.error('Resposta inválida ao iniciar vinculação.');
        }
      },
      error: () => {
        this.vinculandoCalendar = false;
        this.toastService.error('Não foi possível obter o link do Google Calendar.');
      },
    });
  }

  carregarMobileDevices(): void {
    this.mobileCarregando = true;
    this.mobileCaptureService.listDevices().subscribe({
      next: (list) => {
        this.mobileDevices = list;
        this.mobileCarregando = false;
      },
      error: () => {
        this.mobileDevices = [];
        this.mobileCarregando = false;
      },
    });
  }

  abrirModalMobile(platform: MobilePlatform): void {
    this.mobilePlataforma = platform;
    this.mobileNomeNovo = platform === 'ANDROID_MACRODROID' ? 'Android' : 'iPhone';
    this.mobileCredenciais = null;
    this.mobileModalAberto = true;
  }

  fecharModalMobile(): void {
    this.mobileModalAberto = false;
    this.mobileCredenciais = null;
  }

  registrarDispositivoMobile(): void {
    if (!this.mobileNomeNovo.trim()) {
      this.toastService.error('Informe um nome para o dispositivo.');
      return;
    }
    this.mobileCarregando = true;
    this.mobileCaptureService.registerDevice(this.mobileNomeNovo.trim(), this.mobilePlataforma).subscribe({
      next: (reg) => {
        this.mobileCredenciais = reg;
        this.mobileCarregando = false;
        this.carregarMobileDevices();
        this.toastService.success('Dispositivo criado. Guarde o token — ele não será exibido novamente.');
      },
      error: (err) => {
        this.mobileCarregando = false;
        this.toastService.error(resolveHttpError(err, 'Não foi possível registrar o dispositivo.'));
      },
    });
  }

  revogarDispositivoMobile(device: MobileCaptureDevice): void {
    this.confirmDialog
      .ask({
        title: 'Revogar dispositivo',
        message: `Revogar "${device.name}"? O token deixará de funcionar.`,
        confirmLabel: 'Revogar',
        destructive: true,
      })
      .subscribe((ok) => {
        if (!ok || device.id == null) {
          return;
        }
        this.mobileCaptureService.revokeDevice(device.id).subscribe({
          next: () => {
            this.toastService.success('Dispositivo revogado.');
            this.carregarMobileDevices();
          },
          error: (err) => this.toastService.error(resolveHttpError(err, 'Não foi possível revogar.')),
        });
      });
  }

  rotacionarTokenMobile(device: MobileCaptureDevice): void {
    if (device.id == null) {
      return;
    }
    this.mobileCarregando = true;
    this.mobileCaptureService.rotateToken(device.id).subscribe({
      next: (reg) => {
        this.mobileCredenciais = reg;
        this.mobileModalAberto = true;
        this.mobileCarregando = false;
        this.carregarMobileDevices();
        this.toastService.success('Novo token gerado. Atualize MacroDroid/Atalhos.');
      },
      error: (err) => {
        this.mobileCarregando = false;
        this.toastService.error(resolveHttpError(err, 'Não foi possível rotacionar o token.'));
      },
    });
  }

  copiarTextoMobile(valor: string): void {
    navigator.clipboard?.writeText(valor).then(() => this.toastService.success('Copiado.'));
  }

  labelPlataforma(platform: MobilePlatform): string {
    return platform === 'ANDROID_MACRODROID' ? 'Android (MacroDroid)' : 'iPhone (Atalhos)';
  }
}
