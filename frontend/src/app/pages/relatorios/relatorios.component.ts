import { Component, OnInit, ViewEncapsulation } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatDividerModule } from '@angular/material/divider';
import { MatTabsModule } from '@angular/material/tabs';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatChipsModule } from '@angular/material/chips';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { forkJoin } from 'rxjs';

import { RelatorioService } from '../../services/relatorio.service';
import { TransacaoService } from '../../services/transacao.service';
import { CartaoCreditoService } from '../../services/cartao-credito.service';
import { LoadingService } from '../../services/loading.service';
import { Transacao, TipoTransacao } from '../../models/transacao.model';
import {
  TOOLTIP_JUROS_TRANSACAO,
  buildGrupoParcelamentoTemJuros,
  descricaoComIndicadorParcela,
  transacaoMostraBadgeJuros
} from '../../utils/transacao-parcela.util';
import { CartaoCredito } from '../../models/cartao-credito.model';

@Component({
  selector: 'app-relatorios',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatDividerModule,
    MatTabsModule,
    MatProgressSpinnerModule,
    MatChipsModule,
    MatSnackBarModule,
    MatTooltipModule
  ],
  templateUrl: './relatorios.component.html',
  styleUrl: './relatorios.component.scss',
  encapsulation: ViewEncapsulation.Emulated
})
export class RelatoriosComponent implements OnInit {

  // Filtros
  formFiltros: FormGroup;
  
  // Dados
  transacoes: Transacao[] = [];
  /** Grupos de parcelamento com juros (metadado em qualquer parcela da lista). */
  gruposParcelamentoJuros = new Map<string, boolean>();
  readonly tooltipJurosTransacao = TOOLTIP_JUROS_TRANSACAO;
  cartoes: CartaoCredito[] = [];
  carregando = false;
  exportandoIr = false;
  dadosCarregados = false;
  /** Anos para o PDF de IR (declarar/rever anos anteriores). */
  readonly anosIrCalendario: number[];

  // Resumo
  resumo = {
    totalReceitas: 0,
    totalDespesas: 0,
    saldo: 0,
    totalTransacoes: 0
  };

  constructor(
    private relatorioService: RelatorioService,
    private transacaoService: TransacaoService,
    private cartaoService: CartaoCreditoService,
    private fb: FormBuilder,
    private loadingService: LoadingService,
    private snackBar: MatSnackBar
  ) {
    const y = new Date().getFullYear();
    this.anosIrCalendario = [y, y - 1, y - 2, y - 3, y - 4, y - 5];
    this.formFiltros = this.fb.group({
      periodo: ['mesAtual'],
      dataInicio: [null],
      dataFim: [null],
      tipoTransacao: [''],
      cartaoId: [''],
      anoIr: [y - 1],
    });
  }

  get isLoading$() {
    return this.loadingService.isLoading$;
  }

  ngOnInit(): void {
    this.carregarCartoes();
    this.gerarRelatorio();
  }

  carregarCartoes(): void {
    this.cartaoService.getCartoes().subscribe({
      next: (cartoes) => {
        this.cartoes = cartoes;
      },
      error: (error) => {
        console.error('Erro ao carregar cartões:', error);
      }
    });
  }

  baixarRelatorioIr(): void {
    const raw = this.formFiltros.get('anoIr')?.value;
    const ano = typeof raw === 'number' ? raw : Number(raw);
    const anoIr = Number.isFinite(ano) ? ano : new Date().getFullYear() - 1;
    this.exportandoIr = true;
    this.relatorioService.exportarIrPdf(anoIr).subscribe({
      next: (blob) => {
        this.exportandoIr = false;
        const nome = `consumo-esperto-ir-${anoIr}.pdf`;
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = nome;
        a.click();
        URL.revokeObjectURL(url);
        this.snackBar.open('PDF gerado. Confira a pasta de downloads.', 'Fechar', {
          duration: 4000,
          panelClass: ['success-snackbar']
        });
      },
      error: () => {
        this.exportandoIr = false;
        this.snackBar.open('Não foi possível gerar o PDF. Tente novamente.', 'Fechar', {
          duration: 4000,
          panelClass: ['error-snackbar']
        });
      }
    });
  }

  gerarRelatorio(): void {
    this.carregando = true;
    this.dadosCarregados = false;

    const filtros = this.formFiltros.value;
    let dataInicio: Date;
    let dataFim: Date;

    if (filtros.periodo === 'custom') {
      dataInicio = filtros.dataInicio ? new Date(filtros.dataInicio) : new Date();
      dataFim = filtros.dataFim ? new Date(filtros.dataFim) : new Date();
      dataInicio.setHours(0, 0, 0, 0);
      dataFim.setHours(23, 59, 59, 999);
    } else if (filtros.periodo === 'mesAtual') {
      const hoje = new Date();
      dataInicio = new Date(hoje.getFullYear(), hoje.getMonth(), 1, 0, 0, 0, 0);
      dataFim = new Date(hoje.getFullYear(), hoje.getMonth() + 1, 0, 23, 59, 59, 999);
    } else {
      const dias = parseInt(filtros.periodo, 10) || 30;
      dataFim = new Date();
      dataFim.setHours(23, 59, 59, 999);
      dataInicio = new Date();
      dataInicio.setDate(dataInicio.getDate() - dias);
      dataInicio.setHours(0, 0, 0, 0);
    }

    const ref = dataFim || new Date();
    const ano = ref.getFullYear();
    const mes = ref.getMonth() + 1;

    forkJoin({
      transacoes: this.transacaoService.getTransacoesPorPeriodo(
        TransacaoService.toYmdLocal(dataInicio),
        TransacaoService.toYmdLocal(dataFim)
      ),
      resumoMensal: this.relatorioService.getRelatorioMensal(ano, mes)
    }).subscribe({
      next: ({ transacoes, resumoMensal }) => {
        this.transacoes = transacoes;
        this.gruposParcelamentoJuros = buildGrupoParcelamentoTemJuros(this.transacoes ?? []);
        this.resumo.totalReceitas = Number(resumoMensal?.totalReceitas || 0);
        this.resumo.totalDespesas = Number(resumoMensal?.totalDespesas || 0);
        this.resumo.saldo = Number(resumoMensal?.saldo || 0);
        this.resumo.totalTransacoes = this.transacoes.length;
        this.carregando = false;
        this.dadosCarregados = true;
      },
      error: (error) => {
        console.error('Erro ao gerar relatório:', error);
        this.carregando = false;
        this.snackBar.open('Não foi possível gerar o relatório. Tente novamente.', 'Fechar', {
          duration: 4000,
          panelClass: ['error-snackbar']
        });
      }
    });
  }

  calcularResumo(): void {
    this.resumo.totalReceitas = this.transacoes
      .filter(t => t.tipoTransacao === 'RECEITA')
      .reduce((sum, t) => sum + t.valor, 0);

    this.resumo.totalDespesas = this.transacoes
      .filter(t => t.tipoTransacao === 'DESPESA')
      .reduce((sum, t) => sum + t.valor, 0);

    this.resumo.saldo = this.resumo.totalReceitas - this.resumo.totalDespesas;
    this.resumo.totalTransacoes = this.transacoes.length;
  }

  gerarGraficos(): void {}

  gerarGraficoPizza(): void {
    // Implementação simplificada
  }

  gerarGraficoLinha(): void {
    // Implementação simplificada
  }

  gerarGraficoBarras(): void {
    // Implementação simplificada
  }

  gerarGraficoRosca(): void {
    // Implementação simplificada
  }

  getNomeCartao(cartaoId: number | undefined): string {
    if (!cartaoId) return 'N/A';
    const cartao = this.cartoes.find(c => c.id === cartaoId);
    return cartao ? cartao.nome : 'N/A';
  }

  categoriaRotulo(transacao: Transacao): string {
    const nome = transacao.categoria?.nome ?? transacao.categoriaNome;
    return nome?.trim() ? nome : 'Sem categoria';
  }

  descricaoRelatorio(transacao: Transacao): string {
    return descricaoComIndicadorParcela(transacao);
  }

  mostrarBadgeJurosRelatorio(transacao: Transacao): boolean {
    return transacaoMostraBadgeJuros(transacao, this.gruposParcelamentoJuros);
  }
}
