import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { StatusConferencia, Transacao } from '../../../../models/transacao.model';
import {
  TOOLTIP_JUROS_TRANSACAO,
  buildGrupoParcelamentoTemJuros,
  descricaoComIndicadorParcela,
  transacaoMostraBadgeJuros
} from '../../../../utils/transacao-parcela.util';

@Component({
  selector: 'app-lista-transacao',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatTableModule,
    MatSortModule,
    MatPaginatorModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatProgressSpinnerModule,
    MatTooltipModule
  ],
  templateUrl: './lista-transacao.component.html',
  styleUrls: ['./lista-transacao.component.scss']
})
export class ListaTransacaoComponent implements OnChanges {
  @Input() transacoes: Transacao[] = [];
  @Input() colunasExibidas: string[] = [];
  @Input() totalTransacoes = 0;
  @Input() tamanhoPagina = 10;
  @Input() paginaAtual = 0;
  @Input() loading = false;
  @Input() acoesDesabilitadas = false;
  /** IDs em processo de confirmação (feedback de loading por linha) */
  @Input() confirmandoIds: number[] = [];

  @Output() editar = new EventEmitter<Transacao>();
  @Output() deletar = new EventEmitter<Transacao>();
  @Output() confirmar = new EventEmitter<number>();
  @Output() ordenar = new EventEmitter<Sort>();
  @Output() paginar = new EventEmitter<PageEvent>();

  readonly tooltipJuros = TOOLTIP_JUROS_TRANSACAO;
  private gruposComJuros = new Map<string, boolean>();

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['transacoes']) {
      this.gruposComJuros = buildGrupoParcelamentoTemJuros(this.transacoes ?? []);
    }
  }

  isPendente(t: Transacao): boolean {
    return t.statusConferencia === StatusConferencia.PENDENTE;
  }

  isConfirmando(id: number | undefined): boolean {
    return id != null && this.confirmandoIds.includes(id);
  }

  tooltipPendente(t: Transacao): string {
    if (t.cnpj && t.cnpj.length > 0) {
      return `Aguardando confirmação · CNPJ: ${t.cnpj}`;
    }
    return 'Aguardando sua confirmação';
  }

  descricaoLinha(t: Transacao): string {
    return descricaoComIndicadorParcela(t);
  }

  mostrarBadgeJuros(t: Transacao): boolean {
    return transacaoMostraBadgeJuros(t, this.gruposComJuros);
  }
}
