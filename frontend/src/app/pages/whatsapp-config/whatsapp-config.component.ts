import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { UsuarioService } from '../../services/usuario.service';
import { ToastService } from '../../services/toast.service';
import { Usuario } from '../../models/usuario.model';

@Component({
  selector: 'app-whatsapp-config',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './whatsapp-config.component.html',
  styleUrl: './whatsapp-config.component.scss'
})
export class WhatsappConfigComponent implements OnInit {
  numeroWhatsapp = '';
  numeroAtual = '';
  carregando = false;

  constructor(
    private usuarioService: UsuarioService,
    private toastService: ToastService
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
      next: (response) => {
        this.numeroAtual = response?.whatsappNumero || this.numeroWhatsapp.trim();
        this.numeroWhatsapp = this.numeroAtual;
        this.carregando = false;
        this.toastService.success('WhatsApp vinculado com sucesso.');
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
