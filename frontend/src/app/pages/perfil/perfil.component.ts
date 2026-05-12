import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { UsuarioService } from '../../services/usuario.service';
import { AuthService } from '../../services/auth.service';
import { ToastService } from '../../services/toast.service';
import { PreferenciaTratamentoJarvis, Usuario } from '../../models/usuario.model';
import { GoogleCalendarLinkService } from '../../services/google-calendar-link.service';
import { DespesaFixa, DespesasFixaService } from '../../services/despesas-fixa.service';

@Component({
  selector: 'app-perfil',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
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

  constructor(
    private usuarioService: UsuarioService,
    private authService: AuthService,
    private toastService: ToastService,
    private googleCalendarLink: GoogleCalendarLinkService,
    private despesasFixaService: DespesasFixaService
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
      },
      error: () => {
        this.carregando = false;
        this.toastService.error('Não foi possível carregar o perfil.');
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
    return { descricao: '', valor: 0, diaVencimento: 1, categoria: 'Obrigações fixas' };
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
    this.modalFixasAberto = true;
  }

  fecharModalFixas(): void {
    this.modalFixasAberto = false;
    this.editandoFixa = null;
  }

  editarFixa(f: DespesaFixa): void {
    this.editandoFixa = f;
    this.formFixa = {
      id: f.id,
      descricao: f.descricao,
      valor: f.valor,
      diaVencimento: f.diaVencimento,
      categoria: f.categoria || 'Obrigações fixas',
    };
    this.modalFixasAberto = true;
  }

  salvarFixa(): void {
    if (!this.formFixa.descricao?.trim() || !this.formFixa.valor || this.formFixa.valor <= 0) {
      this.toastService.error('Preencha descrição e valor válidos.');
      return;
    }
    const d = Math.min(31, Math.max(1, Math.floor(Number(this.formFixa.diaVencimento)) || 1));
    this.formFixa.diaVencimento = d;
    this.carregandoFixas = true;
    if (this.editandoFixa?.id != null) {
      this.despesasFixaService.atualizar(this.editandoFixa.id, this.formFixa).subscribe({
        next: () => {
          this.toastService.success('Obrigação fixa atualizada.');
          this.fecharModalFixas();
          this.carregarFixas();
        },
        error: () => {
          this.carregandoFixas = false;
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
      error: () => {
        this.carregandoFixas = false;
      },
    });
  }

  excluirFixa(f: DespesaFixa): void {
    if (f.id == null) {
      return;
    }
    if (!confirm(`Remover "${f.descricao}" das obrigações fixas?`)) {
      return;
    }
    this.carregandoFixas = true;
    this.despesasFixaService.excluir(f.id).subscribe({
      next: () => {
        this.toastService.success('Removido.');
        this.carregarFixas();
      },
      error: () => {
        this.carregandoFixas = false;
      },
    });
  }

  vinculandoCalendar = false;

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
}
