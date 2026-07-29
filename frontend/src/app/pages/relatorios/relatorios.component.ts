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
import { BaseChartDirective } from 'ng2-charts';
import { ChartConfiguration, ChartOptions } from 'chart.js';
import { forkJoin } from 'rxjs';

import { RelatorioService } from '../../services/relatorio.service';
import { ExportacaoDadosService } from '../../services/exportacao-dados.service';
import { TransacaoService } from '../../services/transacao.service';
import { CartaoCreditoService } from '../../services/cartao-credito.service';
import { ContaBancariaService } from '../../services/conta-bancaria.service';
import { CategoriaService } from '../../services/categoria.service';
import { Transacao, TipoTransacao, StatusConferencia } from '../../models/transacao.model';
import { ContaBancaria } from '../../models/conta-bancaria.model';
import { Categoria } from '../../models/categoria.model';
import { ExportacaoTransacaoFiltro } from '../../models/exportacao-transacao-filtro.model';
import { baixarBlobComRevogacao, blobCsvVazio, validarIntervaloDatas } from '../../utils/exportacao-csv.util';
import {
  TOOLTIP_JUROS_TRANSACAO,
  buildGrupoParcelamentoTemJuros,
  descricaoComIndicadorParcela,
  transacaoMostraBadgeJuros
} from '../../utils/transacao-parcela.util';
import { CartaoCredito } from '../../models/cartao-credito.model';
import { ChartMetodologiaComponent } from '../../shared/chart-metodologia/chart-metodologia.component';
import { PageLoadingComponent } from '../../shared/page-loading/page-loading.component';
import { WhatsappParityHintComponent } from '../../shared/whatsapp-parity-hint/whatsapp-parity-hint.component';

interface PeriodoResolvido {
  dataInicio: Date;
  dataFim: Date;
  mes: number;
  ano: number;
}

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
    MatTooltipModule,
    BaseChartDirective,
    ChartMetodologiaComponent,
    PageLoadingComponent,
    WhatsappParityHintComponent,
  ],
  templateUrl: './relatorios.component.html',
  styleUrl: './relatorios.component.scss',
  encapsulation: ViewEncapsulation.Emulated
})
export class RelatoriosComponent implements OnInit {

  formFiltros: FormGroup;
  transacoes: Transacao[] = [];
  transacoesFiltradas: Transacao[] = [];
  gruposParcelamentoJuros = new Map<string, boolean>();
  readonly tooltipJurosTransacao = TOOLTIP_JUROS_TRANSACAO;
  cartoes: CartaoCredito[] = [];
  contas: ContaBancaria[] = [];
  categorias: Categoria[] = [];
  readonly statusConferenciaOpcoes = Object.values(StatusConferencia);
  carregando = false;
  exportandoIr = false;
  exportandoPdfMensal = false;
  exportandoCsv = false;
  dadosCarregados = false;
  readonly anosIrCalendario: number[];

  resumo = {
    totalReceitas: 0,
    totalDespesas: 0,
    saldo: 0,
    fluxoMes: 0,
    totalTransacoes: 0
  };

  pizzaData: ChartConfiguration<'doughnut'>['data'] = { labels: [], datasets: [] };
  linhaData: ChartConfiguration<'line'>['data'] = { labels: [], datasets: [] };
  barrasData: ChartConfiguration<'bar'>['data'] = { labels: [], datasets: [] };
  roscaData: ChartConfiguration<'doughnut'>['data'] = { labels: [], datasets: [] };

  readonly chartOpts: ChartOptions = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: { labels: { color: '#e5e7eb' } },
      tooltip: {
        callbacks: {
          label: (ctx) => {
            const v = Number(ctx.raw ?? 0);
            return `${ctx.label}: ${v.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })}`;
          },
        },
      },
    },
  };

  readonly linhaOpts: ChartOptions<'line'> = {
    ...this.chartOpts,
    scales: {
      x: { ticks: { color: '#94a3b8' }, grid: { color: 'rgba(51,65,85,.35)' } },
      y: {
        ticks: {
          color: '#94a3b8',
          callback: (v) => Number(v).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL', maximumFractionDigits: 0 }),
        },
        grid: { color: 'rgba(51,65,85,.35)' },
      },
    },
  };

  readonly barrasOpts: ChartOptions<'bar'> = {
    ...this.chartOpts,
    indexAxis: 'y',
    scales: {
      x: {
        ticks: {
          color: '#94a3b8',
          callback: (v) => Number(v).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL', maximumFractionDigits: 0 }),
        },
        grid: { color: 'rgba(51,65,85,.35)' },
      },
      y: { ticks: { color: '#94a3b8' }, grid: { display: false } },
    },
  };

  periodoAtual: PeriodoResolvido | null = null;

  constructor(
    private relatorioService: RelatorioService,
    private exportacaoService: ExportacaoDadosService,
    private transacaoService: TransacaoService,
    private cartaoService: CartaoCreditoService,
    private contaService: ContaBancariaService,
    private categoriaService: CategoriaService,
    private fb: FormBuilder,
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
      contaId: [''],
      categoriaId: [''],
      statusConferencia: [''],
      descricaoContem: [''],
      anoIr: [y],
      mesPdf: [new Date().getMonth() + 1],
      anoPdf: [y],
    });
  }

  ngOnInit(): void {
    this.carregarCartoes();
    this.carregarContas();
    this.carregarCategorias();
    this.gerarRelatorio();
    this.formFiltros.get('tipoTransacao')?.valueChanges.subscribe(() => this.montarGraficos());
    this.formFiltros.get('cartaoId')?.valueChanges.subscribe(() => this.montarGraficos());
    this.formFiltros.get('contaId')?.valueChanges.subscribe(() => this.montarGraficos());
    this.formFiltros.get('categoriaId')?.valueChanges.subscribe(() => this.montarGraficos());
    this.formFiltros.get('statusConferencia')?.valueChanges.subscribe(() => this.montarGraficos());
    this.formFiltros.get('descricaoContem')?.valueChanges.subscribe(() => this.montarGraficos());
  }

  carregarCartoes(): void {
    this.cartaoService.getCartoes().subscribe({
      next: (cartoes) => { this.cartoes = cartoes; },
      error: () => { this.cartoes = []; },
    });
  }

  carregarContas(): void {
    this.contaService.listarContasAtivas().subscribe({
      next: (contas) => { this.contas = contas ?? []; },
      error: () => { this.contas = []; },
    });
  }

  carregarCategorias(): void {
    this.categoriaService.buscarPorUsuario().subscribe({
      next: (cats) => { this.categorias = cats ?? []; },
      error: () => { this.categorias = []; },
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
        this.baixarBlob(blob, `consumo-esperto-ir-${anoIr}.pdf`);
        this.snackBar.open('PDF gerado.', 'Fechar', { duration: 4000, panelClass: ['success-snackbar'] });
      },
      error: () => {
        this.exportandoIr = false;
        this.snackBar.open('Não foi possível gerar o PDF.', 'Fechar', { duration: 4000, panelClass: ['error-snackbar'] });
      },
    });
  }

  baixarPdfMensal(): void {
    const mes = Number(this.formFiltros.get('mesPdf')?.value);
    const ano = Number(this.formFiltros.get('anoPdf')?.value);
    if (!Number.isFinite(mes) || mes < 1 || mes > 12 || !Number.isFinite(ano)) {
      this.snackBar.open('Informe mês e ano válidos para o PDF mensal.', 'Fechar', { duration: 4000 });
      return;
    }
    this.exportandoPdfMensal = true;
    this.relatorioService.exportarMensalPdf(mes, ano).subscribe({
      next: (blob) => {
        this.exportandoPdfMensal = false;
        this.baixarBlob(blob, `Relatorio_Consumo_${String(mes).padStart(2, '0')}_${ano}.pdf`);
        this.snackBar.open('PDF mensal gerado.', 'Fechar', { duration: 4000, panelClass: ['success-snackbar'] });
      },
      error: (err) => {
        this.exportandoPdfMensal = false;
        const msg = err?.status === 404
          ? 'Sem dados para este mês. Lance transações ou configure a renda.'
          : 'Não foi possível gerar o PDF mensal.';
        this.snackBar.open(msg, 'Fechar', { duration: 5000, panelClass: ['error-snackbar'] });
      },
    });
  }

  baixarCsv(): void {
    if (this.exportandoCsv) return;
    const p = this.periodoAtual ?? this.resolverPeriodo();
    const dataInicio = TransacaoService.toYmdLocal(p.dataInicio);
    const dataFim = TransacaoService.toYmdLocal(p.dataFim);
    const erroIntervalo = validarIntervaloDatas(dataInicio, dataFim);
    if (erroIntervalo) {
      this.snackBar.open(erroIntervalo, 'Fechar', { duration: 4000, panelClass: ['error-snackbar'] });
      return;
    }
    const filtro = this.montarFiltroCsv(dataInicio, dataFim);
    this.exportandoCsv = true;
    this.exportacaoService.exportarTransacoesCsv(filtro).subscribe({
      next: ({ blob, nomeArquivo }) => {
        this.exportandoCsv = false;
        if (blobCsvVazio(blob)) {
          this.snackBar.open('Nenhum registro encontrado para os filtros informados.', 'Fechar', {
            duration: 5000,
            panelClass: ['error-snackbar'],
          });
          return;
        }
        baixarBlobComRevogacao(blob, nomeArquivo);
        this.snackBar.open('CSV exportado.', 'Fechar', { duration: 4000, panelClass: ['success-snackbar'] });
      },
      error: () => {
        this.exportandoCsv = false;
        this.snackBar.open('Erro ao exportar CSV.', 'Fechar', { duration: 4000, panelClass: ['error-snackbar'] });
      },
    });
  }

  private montarFiltroCsv(dataInicio: string, dataFim: string): ExportacaoTransacaoFiltro {
    const raw = this.formFiltros.getRawValue();
    return {
      dataInicio,
      dataFim,
      tipoTransacao: raw.tipoTransacao || undefined,
      cartaoId: raw.cartaoId ? Number(raw.cartaoId) : undefined,
      contaId: raw.contaId ? Number(raw.contaId) : undefined,
      categoriaId: raw.categoriaId ? Number(raw.categoriaId) : undefined,
      statusConferencia: raw.statusConferencia || undefined,
      descricaoContem: raw.descricaoContem?.trim() || undefined,
    };
  }

  private baixarBlob(blob: Blob, nome: string): void {
    baixarBlobComRevogacao(blob, nome);
  }

  gerarRelatorio(): void {
    this.carregando = true;
    this.dadosCarregados = false;
    const p = this.resolverPeriodo();
    this.periodoAtual = p;

    const ref = p.dataFim;
    const ano = ref.getFullYear();
    const mes = ref.getMonth() + 1;

    forkJoin({
      transacoes: this.transacaoService.getTransacoesPorPeriodo(
        TransacaoService.toYmdLocal(p.dataInicio),
        TransacaoService.toYmdLocal(p.dataFim)
      ),
      resumoMensal: this.relatorioService.getRelatorioMensal(ano, mes),
    }).subscribe({
      next: ({ transacoes, resumoMensal }) => {
        this.transacoes = transacoes ?? [];
        this.gruposParcelamentoJuros = buildGrupoParcelamentoTemJuros(this.transacoes);
        this.aplicarFiltrosLocais();
        this.resumo.totalReceitas = Number(resumoMensal?.totalReceitas || 0);
        this.resumo.totalDespesas = Number(resumoMensal?.totalDespesas || 0);
        this.resumo.fluxoMes = Number(resumoMensal?.fluxoMes ?? (this.resumo.totalReceitas - this.resumo.totalDespesas));
        const patrimonio = Number(resumoMensal?.patrimonioLiquido);
        this.resumo.saldo = Number(resumoMensal?.saldo ?? (!Number.isNaN(patrimonio) ? patrimonio : this.resumo.fluxoMes));
        this.montarGraficos();
        this.carregando = false;
        this.dadosCarregados = true;
      },
      error: () => {
        this.carregando = false;
        this.snackBar.open('Não foi possível gerar o relatório.', 'Fechar', {
          duration: 4000,
          panelClass: ['error-snackbar'],
        });
      },
    });
  }

  resolverPeriodo(): PeriodoResolvido {
    const filtros = this.formFiltros.value;
    let dataInicio: Date;
    let dataFim: Date;

    if (filtros.periodo === 'custom') {
      dataInicio = filtros.dataInicio ? new Date(filtros.dataInicio) : new Date();
      dataFim = filtros.dataFim ? new Date(filtros.dataFim) : new Date();
    } else if (filtros.periodo === 'mesAtual') {
      const hoje = new Date();
      dataInicio = new Date(hoje.getFullYear(), hoje.getMonth(), 1);
      dataFim = new Date(hoje.getFullYear(), hoje.getMonth() + 1, 0, 23, 59, 59, 999);
    } else {
      const dias = parseInt(filtros.periodo, 10) || 30;
      dataFim = new Date();
      dataFim.setHours(23, 59, 59, 999);
      dataInicio = new Date();
      dataInicio.setDate(dataInicio.getDate() - dias);
      dataInicio.setHours(0, 0, 0, 0);
    }
    if (dataInicio > dataFim) {
      const tmp = dataInicio;
      dataInicio = dataFim;
      dataFim = tmp;
    }
    dataInicio.setHours(0, 0, 0, 0);
    dataFim.setHours(23, 59, 59, 999);
    return {
      dataInicio,
      dataFim,
      mes: dataFim.getMonth() + 1,
      ano: dataFim.getFullYear(),
    };
  }

  aplicarFiltrosLocais(): void {
    const tipo = this.formFiltros.get('tipoTransacao')?.value as string;
    const cartaoRaw = this.formFiltros.get('cartaoId')?.value;
    const contaRaw = this.formFiltros.get('contaId')?.value;
    const categoriaRaw = this.formFiltros.get('categoriaId')?.value;
    const status = this.formFiltros.get('statusConferencia')?.value as string;
    const descricao = (this.formFiltros.get('descricaoContem')?.value as string)?.trim().toLowerCase();
    const cartaoId = cartaoRaw ? Number(cartaoRaw) : null;
    const contaId = contaRaw ? Number(contaRaw) : null;
    const categoriaId = categoriaRaw ? Number(categoriaRaw) : null;
    this.transacoesFiltradas = this.transacoes.filter((t) => {
      if (tipo && t.tipoTransacao !== tipo) return false;
      if (cartaoId && t.cartaoCreditoId !== cartaoId) return false;
      if (contaId && t.contaBancariaId !== contaId) return false;
      if (categoriaId && t.categoriaId !== categoriaId && t.categoria?.id !== categoriaId) return false;
      if (status && t.statusConferencia !== status) return false;
      if (descricao) {
        const texto = (t.descricao ?? '').toLowerCase();
        if (!texto.includes(descricao)) return false;
      }
      return true;
    });
    this.resumo.totalTransacoes = this.transacoesFiltradas.length;
    this.calcularResumoFiltrado();
  }

  calcularResumoFiltrado(): void {
    this.resumo.totalReceitas = this.transacoesFiltradas
      .filter((t) => t.tipoTransacao === 'RECEITA')
      .reduce((s, t) => s + Number(t.valor || 0), 0);
    this.resumo.totalDespesas = this.transacoesFiltradas
      .filter((t) => t.tipoTransacao === 'DESPESA')
      .reduce((s, t) => s + Number(t.valor || 0), 0);
  }

  temDadosGrafico(): boolean {
    return this.transacoesFiltradas.length > 0;
  }

  temDespesasGrafico(): boolean {
    return this.transacoesFiltradas.some((t) => t.tipoTransacao === 'DESPESA');
  }

  montarGraficos(): void {
    this.aplicarFiltrosLocais();
    const receitas = this.resumo.totalReceitas;
    const despesas = this.resumo.totalDespesas;

    this.pizzaData = {
      labels: ['Receitas', 'Despesas'],
      datasets: [{
        data: [receitas, despesas],
        backgroundColor: ['#34d399', '#f87171'],
        borderWidth: 0,
      }],
    };

    const porDia = new Map<string, { receita: number; despesa: number }>();
    for (const t of this.transacoesFiltradas) {
      const raw = t.dataTransacao ?? t.data;
      const d = raw ? new Date(raw) : null;
      if (!d || Number.isNaN(d.getTime())) continue;
      const key = d.toLocaleDateString('pt-BR');
      const bucket = porDia.get(key) ?? { receita: 0, despesa: 0 };
      if (t.tipoTransacao === 'RECEITA') bucket.receita += Number(t.valor || 0);
      else bucket.despesa += Number(t.valor || 0);
      porDia.set(key, bucket);
    }
    const dias = [...porDia.keys()].sort((a, b) => {
      const [da, ma, ya] = a.split('/').map(Number);
      const [db, mb, yb] = b.split('/').map(Number);
      return new Date(ya, ma - 1, da).getTime() - new Date(yb, mb - 1, db).getTime();
    });
    let acum = 0;
    const saldos: number[] = [];
    for (const d of dias) {
      const b = porDia.get(d)!;
      acum += b.receita - b.despesa;
      saldos.push(acum);
    }
    this.linhaData = {
      labels: dias,
      datasets: [{
        label: 'Saldo acumulado',
        data: saldos,
        borderColor: '#38bdf8',
        backgroundColor: 'rgba(56,189,248,.12)',
        tension: 0.3,
        fill: true,
      }],
    };

    const porCat = new Map<string, number>();
    for (const t of this.transacoesFiltradas.filter((x) => x.tipoTransacao === 'DESPESA')) {
      const cat = this.categoriaRotulo(t);
      porCat.set(cat, (porCat.get(cat) ?? 0) + Number(t.valor || 0));
    }
    const cats = [...porCat.entries()].sort((a, b) => b[1] - a[1]).slice(0, 12);
    this.barrasData = {
      labels: cats.map((c) => c[0]),
      datasets: [{
        label: 'Despesas',
        data: cats.map((c) => c[1]),
        backgroundColor: '#818cf8',
      }],
    };

    const porCartao = new Map<string, number>();
    for (const t of this.transacoesFiltradas.filter((x) => x.tipoTransacao === 'DESPESA')) {
      const nome = t.cartaoCreditoId ? this.getNomeCartao(t.cartaoCreditoId) : 'Conta / débito';
      porCartao.set(nome, (porCartao.get(nome) ?? 0) + Number(t.valor || 0));
    }
    const cart = [...porCartao.entries()].sort((a, b) => b[1] - a[1]);
    this.roscaData = {
      labels: cart.map((c) => c[0]),
      datasets: [{
        data: cart.map((c) => c[1]),
        backgroundColor: ['#f472b6', '#fb923c', '#a78bfa', '#4ade80', '#22d3ee', '#facc15'],
      }],
    };
  }

  getNomeCartao(cartaoId: number | undefined): string {
    if (!cartaoId) return 'Conta / débito';
    const cartao = this.cartoes.find((c) => c.id === cartaoId);
    return cartao ? cartao.nome : 'Cartão';
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
