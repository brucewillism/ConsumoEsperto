import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatDialog } from '@angular/material/dialog';
import { CE_DIALOG_IMPORTS } from '../../shared/ce-dialog-imports';
import { MatIconModule } from '@angular/material/icon';
import { forkJoin, of } from 'rxjs';
import { openCeFormDialog } from '../../shared/ce-form-dialog.util';
import { CriarGrupoFamiliarDialogComponent } from '../../shared/criar-grupo-familiar-dialog/criar-grupo-familiar-dialog.component';
import {
  ConvidarFamiliarDialogComponent,
  ConvidarFamiliarDialogResult,
} from '../../shared/convidar-familiar-dialog/convidar-familiar-dialog.component';
import { NovoOrcamentoDialogComponent } from '../../shared/novo-orcamento-dialog/novo-orcamento-dialog.component';
import { catchError } from 'rxjs/operators';
import {
  BalancoGrupo,
  DebitoInterno,
  FamiliaService,
  GrupoFamiliar,
  GrupoFamiliarMembro,
} from '../../services/familia.service';
import { Orcamento } from '../../services/orcamento.service';
import { Categoria } from '../../models/categoria.model';
import { CategoriaService } from '../../services/categoria.service';
import { ToastService } from '../../services/toast.service';
import { LoadingIndicatorComponent } from '../../components/loading-indicator/loading-indicator.component';
import { WhatsappParityHintComponent } from '../../shared/whatsapp-parity-hint/whatsapp-parity-hint.component';
import { ChartMetodologiaComponent } from '../../shared/chart-metodologia/chart-metodologia.component';

@Component({
  selector: 'app-familia',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressBarModule,
    ...CE_DIALOG_IMPORTS,
    MatIconModule,
    LoadingIndicatorComponent,
    WhatsappParityHintComponent,
    ChartMetodologiaComponent,
  ],
  templateUrl: './familia.component.html',
  styleUrl: './familia.component.scss',
})
export class FamiliaComponent implements OnInit {
  grupo: GrupoFamiliar | null = null;
  convites: GrupoFamiliarMembro[] = [];
  orcamentos: Orcamento[] = [];
  balanco: BalancoGrupo | null = null;
  historico: DebitoInterno[] = [];
  categorias: Categoria[] = [];
  carregando = true;
  conviteVisual = '';
  liquidandoId: number | null = null;
  editandoNome = false;
  nomeEdicao = '';
  salvandoNome = false;
  saindoGrupo = false;
  historicoAberto = false;
  mes = new Date().getMonth() + 1;
  ano = new Date().getFullYear();

  constructor(
    private familiaService: FamiliaService,
    private categoriaService: CategoriaService,
    private toast: ToastService,
    private dialog: MatDialog
  ) {}

  ngOnInit(): void {
    this.carregar();
  }

  carregar(): void {
    this.carregando = true;
    forkJoin({
      grupo: this.familiaService.meuGrupo().pipe(catchError(() => of(null))),
      convites: this.familiaService.convites().pipe(catchError(() => of([] as GrupoFamiliarMembro[]))),
      orcamentos: this.familiaService.orcamentosCompartilhados(this.mes, this.ano).pipe(catchError(() => of([] as Orcamento[]))),
      balanco: this.familiaService.balanco().pipe(catchError(() => of(null))),
      historico: this.familiaService.historicoDebitos().pipe(catchError(() => of([] as DebitoInterno[]))),
      categorias: this.categoriaService.buscarPorUsuario().pipe(catchError(() => of([] as Categoria[]))),
    }).subscribe(({ grupo, convites, orcamentos, balanco, historico, categorias }) => {
      this.grupo = grupo;
      this.convites = convites;
      this.orcamentos = orcamentos;
      this.balanco = balanco;
      this.historico = historico;
      this.categorias = categorias;
      this.carregando = false;
    });
  }

  membrosAceitos(): GrupoFamiliarMembro[] {
    return (this.grupo?.membros ?? []).filter((m) => m.status === 'ACEITO');
  }

  podeConvidar(): boolean {
    return this.membrosAceitos().length < 2;
  }

  totalRachasPendentes(): number {
    if (!this.balanco) {
      return 0;
    }
    return this.balanco.aReceber.length + this.balanco.devidos.length;
  }

  temBalanco(): boolean {
    return !!this.balanco && (this.balanco.aReceber.length > 0 || this.balanco.devidos.length > 0);
  }

  statusLabel(status: string): string {
    switch (status) {
      case 'ACEITO':
        return 'Aceito';
      case 'PENDENTE':
        return 'Pendente';
      case 'RECUSADO':
        return 'Recusado';
      case 'CANCELADO':
        return 'Cancelado';
      default:
        return status;
    }
  }

  primeiroNome(nome: string): string {
    if (!nome) {
      return 'Membro';
    }
    return nome.trim().split(' ')[0];
  }

  marcarPago(debito: DebitoInterno): void {
    if (this.liquidandoId) {
      return;
    }
    this.liquidandoId = debito.id;
    this.familiaService.liquidarDebito(debito.id).subscribe({
      next: () => {
        this.toast.success(`Débito de ${this.primeiroNome(debito.devedorNome)} marcado como pago.`);
        this.liquidandoId = null;
        this.carregar();
      },
      error: (e) => {
        this.liquidandoId = null;
        this.toast.error(e?.error?.message || 'Erro ao liquidar débito.');
      },
    });
  }

  abrirCriarGrupo(): void {
    openCeFormDialog(this.dialog, CriarGrupoFamiliarDialogComponent, { width: '440px' })
      .afterClosed()
      .subscribe((grupo) => {
        const criado = grupo as GrupoFamiliar | null | undefined;
        if (criado) {
          this.grupo = criado;
          this.carregar();
        }
      });
  }

  abrirConvidar(): void {
    openCeFormDialog(this.dialog, ConvidarFamiliarDialogComponent, {
      width: '520px',
      panelClass: 'convidar-familiar-dialog',
    })
      .afterClosed()
      .subscribe((res) => {
        const convite = res as ConvidarFamiliarDialogResult | null | undefined;
        if (convite?.conviteVisual) {
          this.conviteVisual = convite.conviteVisual;
          this.carregar();
        }
      });
  }

  abrirNovoOrcamentoCompartilhado(): void {
    if (!this.categorias.length) {
      this.toast.warning('Cadastre categorias antes de criar um orçamento compartilhado.');
      return;
    }
    openCeFormDialog(this.dialog, NovoOrcamentoDialogComponent, {
      width: '520px',
      data: {
        categorias: this.categorias,
        mes: this.mes,
        ano: this.ano,
        compartilhadoDefault: true,
      },
    })
      .afterClosed()
      .subscribe((salvo) => {
        if (salvo) {
          this.carregar();
        }
      });
  }

  iniciarRenomear(): void {
    if (!this.grupo) {
      return;
    }
    this.nomeEdicao = this.grupo.nome;
    this.editandoNome = true;
  }

  cancelarRenomear(): void {
    this.editandoNome = false;
    this.nomeEdicao = '';
  }

  salvarRenomear(): void {
    const nome = this.nomeEdicao.trim();
    if (!nome) {
      this.toast.warning('Informe um nome para o grupo.');
      return;
    }
    this.salvandoNome = true;
    this.familiaService.renomearGrupo(nome).subscribe({
      next: (grupo) => {
        this.grupo = grupo;
        this.editandoNome = false;
        this.salvandoNome = false;
        this.toast.success('Nome do grupo atualizado.');
      },
      error: (e) => {
        this.salvandoNome = false;
        this.toast.error(e?.error?.message || 'Erro ao renomear grupo.');
      },
    });
  }

  confirmarSairGrupo(): void {
    if (!this.grupo || this.saindoGrupo) {
      return;
    }
    const ok = window.confirm(
      'Sair do grupo familiar? Você deixa de ver orçamentos compartilhados e rachas deste grupo.'
    );
    if (!ok) {
      return;
    }
    this.saindoGrupo = true;
    this.familiaService.sairGrupo().subscribe({
      next: () => {
        this.saindoGrupo = false;
        this.grupo = null;
        this.orcamentos = [];
        this.balanco = null;
        this.historico = [];
        this.conviteVisual = '';
        this.toast.success('Você saiu do grupo familiar.');
        this.carregar();
      },
      error: (e) => {
        this.saindoGrupo = false;
        this.toast.error(e?.error?.message || 'Erro ao sair do grupo.');
      },
    });
  }

  copiarConvite(): void {
    if (!this.conviteVisual) {
      return;
    }
    navigator.clipboard.writeText(this.conviteVisual).then(
      () => this.toast.success('Link do convite copiado.'),
      () => this.toast.error('Não foi possível copiar o link.')
    );
  }

  responder(convite: GrupoFamiliarMembro, aceitar: boolean): void {
    this.familiaService.responderConvite(convite.id, aceitar).subscribe({
      next: () => {
        this.toast.success(aceitar ? 'Convite aceito.' : 'Convite recusado.');
        this.carregar();
      },
      error: (e) => this.toast.error(e?.error?.message || 'Erro ao responder convite.'),
    });
  }

  brl(v: number): string {
    return Number(v || 0).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  }

  progress(o: Orcamento): number {
    return Math.min(100, Math.max(0, Number(o.percentualUso) || 0));
  }

  corOrcamento(o: Orcamento): string {
    if (o.percentualUso >= 90) return 'danger';
    if (o.percentualUso >= 70) return 'warning';
    return 'success';
  }

  qrUrl(): string {
    return this.conviteVisual
      ? `https://api.qrserver.com/v1/create-qr-code/?size=140x140&data=${encodeURIComponent(this.conviteVisual)}`
      : '';
  }

  formatarData(iso?: string): string {
    if (!iso) {
      return '—';
    }
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) {
      return iso;
    }
    return d.toLocaleDateString('pt-BR', { day: '2-digit', month: 'short', year: 'numeric' });
  }
}
