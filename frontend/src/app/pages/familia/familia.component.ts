import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { FamiliaService, GrupoFamiliar, GrupoFamiliarMembro } from '../../services/familia.service';
import { Orcamento } from '../../services/orcamento.service';
import { ToastService } from '../../services/toast.service';
import { CeInputMaskDirective } from '../../shared/directives/ce-input-mask.directive';
import { isEmailValido, resolveHttpError } from '../../shared/utils/form.utils';

@Component({
  selector: 'app-familia',
  standalone: true,
  imports: [CommonModule, FormsModule, MatButtonModule, MatCardModule, MatFormFieldModule, MatInputModule, MatProgressBarModule, CeInputMaskDirective],
  templateUrl: './familia.component.html',
  styleUrl: './familia.component.scss'
})
export class FamiliaComponent implements OnInit {
  grupo: GrupoFamiliar | null = null;
  convites: GrupoFamiliarMembro[] = [];
  orcamentos: Orcamento[] = [];
  nomeGrupo = 'Orçamento do Casal';
  conviteEmail = '';
  conviteWhatsapp = '';
  carregando = true;
  conviteVisual = '';
  grupoErro = '';
  conviteErro = '';

  constructor(private familiaService: FamiliaService, private toast: ToastService) {}

  ngOnInit(): void {
    this.carregar();
  }

  carregar(): void {
    this.carregando = true;
    forkJoin({
      grupo: this.familiaService.meuGrupo().pipe(catchError(() => of(null))),
      convites: this.familiaService.convites().pipe(catchError(() => of([] as GrupoFamiliarMembro[]))),
      orcamentos: this.familiaService.orcamentosCompartilhados().pipe(catchError(() => of([] as Orcamento[])))
    }).subscribe(({ grupo, convites, orcamentos }) => {
      this.grupo = grupo;
      this.convites = convites;
      this.orcamentos = orcamentos;
      this.carregando = false;
    });
  }

  criarGrupo(): void {
    this.grupoErro = '';
    const nome = (this.nomeGrupo || '').trim();
    if (!nome) {
      this.grupoErro = 'Informe um nome para o grupo familiar.';
      return;
    }
    this.familiaService.criar(nome).subscribe({
      next: (grupo) => {
        this.grupo = grupo;
        this.toast.success('Grupo familiar criado.');
        this.carregar();
      },
      error: (e) => {
        this.grupoErro = resolveHttpError(e, 'Erro ao criar grupo.');
      }
    });
  }

  convidar(): void {
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
    this.familiaService.convidar(this.conviteEmail, this.conviteWhatsapp).subscribe({
      next: () => {
        this.toast.success('Convite enviado.');
        this.conviteVisual = `${window.location.origin}/familia?convite=${encodeURIComponent(this.conviteEmail || this.conviteWhatsapp)}`;
        this.conviteEmail = '';
        this.conviteWhatsapp = '';
        this.carregar();
      },
      error: (e) => {
        this.conviteErro = resolveHttpError(e, 'Erro ao enviar convite.');
      }
    });
  }

  responder(convite: GrupoFamiliarMembro, aceitar: boolean): void {
    this.familiaService.responderConvite(convite.id, aceitar).subscribe({
      next: () => {
        this.toast.success(aceitar ? 'Convite aceito.' : 'Convite recusado.');
        this.carregar();
      },
      error: (e) => this.toast.error(e?.error?.message || 'Erro ao responder convite.')
    });
  }

  brl(v: number): string {
    return Number(v || 0).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  }

  progress(o: Orcamento): number {
    return Math.min(100, Math.max(0, Number(o.percentualUso) || 0));
  }

  qrUrl(): string {
    return this.conviteVisual
      ? `https://api.qrserver.com/v1/create-qr-code/?size=140x140&data=${encodeURIComponent(this.conviteVisual)}`
      : '';
  }
}
