import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { UsuarioService } from '../../services/usuario.service';
import { AuthService } from '../../services/auth.service';
import { ToastService } from '../../services/toast.service';
import { PreferenciaTratamentoJarvis, Usuario } from '../../models/usuario.model';

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
    private toastService: ToastService
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
}
