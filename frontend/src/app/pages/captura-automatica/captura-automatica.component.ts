import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import {
  IngestConfig,
  IngestFonte,
  IngestNotificacaoLog,
  IngestNotificacaoService,
  IngestTokenGerado,
} from '../../services/ingest-notificacao.service';
import { ContaBancariaService } from '../../services/conta-bancaria.service';
import { CartaoCreditoService } from '../../services/cartao-credito.service';
import { ContaBancaria } from '../../models/conta-bancaria.model';
import { CartaoCredito } from '../../models/cartao-credito.model';
import { ToastService } from '../../services/toast.service';
import { resolveHttpError } from '../../shared/utils/form.utils';

@Component({
  selector: 'app-captura-automatica',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './captura-automatica.component.html',
  styleUrl: './captura-automatica.component.scss',
})
export class CapturaAutomaticaComponent implements OnInit {
  carregando = true;
  config: IngestConfig | null = null;
  tokenNovo: IngestTokenGerado | null = null;
  contas: ContaBancaria[] = [];
  cartoes: CartaoCredito[] = [];
  logs: IngestNotificacaoLog[] = [];
  novaFonte: { app: string; canal: string; contaBancariaId: number | null; cartaoCreditoId: number | null } = {
    app: 'nubank',
    canal: 'CREDITO',
    contaBancariaId: null,
    cartaoCreditoId: null,
  };

  constructor(
    private ingest: IngestNotificacaoService,
    private contaService: ContaBancariaService,
    private cartaoService: CartaoCreditoService,
    private toast: ToastService
  ) {}

  ngOnInit(): void {
    this.contaService.listarContasAtivas().subscribe({ next: (c) => (this.contas = c) });
    this.cartaoService.getCartoes().subscribe({ next: (c) => (this.cartoes = c) });
    this.recarregar();
  }

  recarregar(): void {
    this.carregando = true;
    this.ingest.obter().subscribe({
      next: (cfg) => {
        this.config = cfg;
        this.carregando = false;
      },
      error: (err) => {
        this.carregando = false;
        this.toast.error(resolveHttpError(err, 'Não foi possível carregar a captura automática.'));
      },
    });
    this.ingest.listarNotificacoes().subscribe({
      next: (rows) => (this.logs = rows),
      error: () => (this.logs = []),
    });
  }

  gerarToken(): void {
    this.ingest.gerarToken().subscribe({
      next: (t) => {
        this.tokenNovo = t;
        this.toast.success('Token gerado. Copie agora — não será mostrado de novo.');
        this.recarregar();
      },
      error: (err) => this.toast.error(resolveHttpError(err, 'Falha ao gerar token.')),
    });
  }

  revogarToken(): void {
    this.ingest.revogarToken().subscribe({
      next: () => {
        this.tokenNovo = null;
        this.toast.success('Token revogado.');
        this.recarregar();
      },
      error: (err) => this.toast.error(resolveHttpError(err, 'Falha ao revogar token.')),
    });
  }

  async copiar(texto: string): Promise<void> {
    try {
      await navigator.clipboard.writeText(texto);
      this.toast.success('Copiado.');
    } catch {
      this.toast.error('Não foi possível copiar.');
    }
  }

  salvarPrefs(): void {
    if (!this.config) {
      return;
    }
    this.ingest.salvarPreferencias(this.config.preferencias).subscribe({
      next: (p) => {
        if (this.config) {
          this.config.preferencias = p;
        }
        this.toast.success('Preferências guardadas.');
      },
      error: (err) => this.toast.error(resolveHttpError(err, 'Falha ao guardar preferências.')),
    });
  }

  adicionarFonte(): void {
    this.ingest
      .upsertFonte({
        app: this.novaFonte.app,
        canal: this.novaFonte.canal,
        contaBancariaId: this.novaFonte.contaBancariaId,
        cartaoCreditoId: this.novaFonte.cartaoCreditoId,
      })
      .subscribe({
        next: () => {
          this.toast.success('Mapeamento guardado.');
          this.recarregar();
        },
        error: (err) => this.toast.error(resolveHttpError(err, 'Falha ao guardar mapeamento.')),
      });
  }

  removerFonte(f: IngestFonte): void {
    this.ingest.removerFonte(f.id).subscribe({
      next: () => {
        this.toast.success('Mapeamento removido.');
        this.recarregar();
      },
      error: (err) => this.toast.error(resolveHttpError(err, 'Falha ao remover mapeamento.')),
    });
  }

  nomeConta(id?: number | null): string {
    if (id == null) {
      return '—';
    }
    return this.contas.find((c) => c.id === id)?.nome || `#${id}`;
  }

  nomeCartao(id?: number | null): string {
    if (id == null) {
      return '—';
    }
    return this.cartoes.find((c) => c.id === id)?.nome || `#${id}`;
  }

  labelStatus(status: string): string {
    switch (status) {
      case 'LANCADA':
        return 'Lançada';
      case 'IGNORADA':
        return 'Ignorada';
      case 'NAO_RECONHECIDA':
        return 'Não reconhecida';
      case 'DUPLICADA':
        return 'Duplicada';
      case 'ESTORNADA':
        return 'Estorno';
      case 'ERRO':
        return 'Erro';
      default:
        return status;
    }
  }
}
