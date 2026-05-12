import { Component, DestroyRef, Input, OnChanges, OnInit, SimpleChanges, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { BaseChartDirective } from 'ng2-charts';
import { ChartConfiguration, ChartOptions } from 'chart.js';
import { ProjecaoDashboardService, PrevisaoFuturoChart, ProvisaoMemoriaHud } from '../../services/projecao-dashboard.service';
import { catchError, of } from 'rxjs';

@Component({
  selector: 'app-previsao-futuro-chart',
  standalone: true,
  imports: [CommonModule, BaseChartDirective],
  templateUrl: './previsao-futuro-chart.component.html',
  styleUrl: './previsao-futuro-chart.component.scss',
})
export class PrevisaoFuturoChartComponent implements OnInit, OnChanges {
  private readonly destroyRef = inject(DestroyRef);

  /** Quando true, dados vêm do painel (evita GET duplicado). */
  @Input() usarEntradaPai = false;
  @Input() chartInput: PrevisaoFuturoChart | null = null;
  @Input() painelCarregando = false;
  /** Varredura tipo radar enquanto o protocolo roda no servidor. */
  @Input() radarAtivo = false;
  /** Trajetória “novo futuro” em ciano (pós-protocolo). */
  @Input() novoFuturoCiano = false;

  carregando = true;
  erro = false;
  resumoFimMes = '';
  negativa = false;

  lineData: ChartConfiguration<'line'>['data'] = { labels: [], datasets: [] };
  lineOptions: ChartOptions<'line'> = {
    responsive: true,
    maintainAspectRatio: false,
    interaction: { mode: 'index', intersect: false },
    plugins: {
      legend: {
        labels: {
          color: '#94a3b8',
          font: { family: 'Inter, sans-serif', size: 11 },
          boxWidth: 10,
        },
      },
      tooltip: {
        enabled: true,
        backgroundColor: 'rgba(13, 14, 18, 0.94)',
        titleColor: '#00e5ff',
        bodyColor: '#cbd5e1',
        borderColor: 'rgba(0, 229, 255, 0.45)',
        borderWidth: 1,
        padding: 12,
        cornerRadius: 4,
        displayColors: true,
        titleFont: { family: 'Inter, sans-serif', size: 12, weight: 'bold' },
        bodyFont: { family: 'Inter, sans-serif', size: 12 },
        callbacks: {
          label: (ctx) => {
            const v = ctx.raw;
            if (v == null || typeof v !== 'number') {
              return `${ctx.dataset.label}: —`;
            }
            return `${ctx.dataset.label}: ${new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(v)}`;
          },
        },
      },
    },
    scales: {
      x: {
        ticks: { color: '#64748b', font: { family: 'Inter, sans-serif', size: 10 } },
        grid: { color: 'rgba(0, 229, 255, 0.06)' },
      },
      y: {
        ticks: { color: '#64748b', font: { family: 'Inter, sans-serif', size: 10 } },
        grid: { color: 'rgba(0, 229, 255, 0.06)' },
      },
    },
  };

  constructor(private projecao: ProjecaoDashboardService) {}

  ngOnInit(): void {
    if (this.usarEntradaPai) {
      this.carregando = this.painelCarregando;
      return;
    }
    this.carregarInterno();
  }

  ngOnChanges(ch: SimpleChanges): void {
    if (!this.usarEntradaPai) {
      return;
    }
    if (this.painelCarregando) {
      this.carregando = true;
      this.erro = false;
      return;
    }
    this.carregando = false;
    if (this.chartInput?.pontos?.length) {
      this.montarGrafico(this.chartInput);
      this.erro = false;
    } else {
      this.erro = true;
    }
  }

  private carregarInterno(): void {
    this.projecao
      .previsaoFuturo()
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        catchError(() => {
          this.erro = true;
          this.carregando = false;
          return of(null);
        })
      )
      .subscribe((dto) => {
        this.carregando = false;
        if (!dto || !dto.pontos?.length) {
          this.erro = true;
          return;
        }
        this.montarGrafico(dto);
      });
  }

  private montarGrafico(dto: PrevisaoFuturoChart): void {
    const negativaVisual = !!dto.projecaoNegativa && !this.novoFuturoCiano;
    this.negativa = negativaVisual;
    const labels = dto.pontos.map((p) => `Dia ${p.dia}`);
    const real: (number | null)[] = dto.pontos.map((p) => (p.serie === 'REAL' ? p.saldo : null));
    const proj: (number | null)[] = dto.pontos.map((p) =>
      p.serie === 'PROJETADO' || p.serie === 'REAL' ? p.saldo : null
    );

    const marcos = new Set(dto.diasVencimentoDespesasFixas ?? []);
    const marcosProv = new Set(dto.diasProvisaoMemoria ?? []);
    const provPorDia = new Map<number, ProvisaoMemoriaHud>();
    for (const pr of dto.provisoesMemoria ?? []) {
      provPorDia.set(pr.diaAlvo, pr);
    }
    const brl = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });

    const riskFill = (ctx: { chart: { chartArea?: { top: number; bottom: number }; ctx: CanvasRenderingContext2D } }) => {
      if (!negativaVisual) {
        return 'transparent';
      }
      const chart = ctx.chart;
      const { chartArea, ctx: c } = chart;
      if (!chartArea) {
        return 'transparent';
      }
      const g = c.createLinearGradient(0, chartArea.top, 0, chartArea.bottom);
      g.addColorStop(0, 'rgba(255, 23, 68, 0.42)');
      g.addColorStop(0.55, 'rgba(255, 193, 7, 0.22)');
      g.addColorStop(1, 'rgba(255, 193, 7, 0.05)');
      return g;
    };

    const projBorder = this.novoFuturoCiano ? '#00e5ff' : negativaVisual ? '#ff9800' : '#4fc3f7';
    const projLabel = this.novoFuturoCiano ? 'Projeção — novo futuro (protocolo)' : 'Projeção (preditiva)';

    const pointRadiiProj = dto.pontos.map((p) => {
      if (p.serie !== 'PROJETADO' || p.dia <= dto.diaHoje) {
        return 0;
      }
      if (marcos.has(p.dia)) {
        return 9;
      }
      if (marcosProv.has(p.dia)) {
        return 7;
      }
      return 0;
    });

    this.lineData = {
      labels,
      datasets: [
        {
          label: 'Real (conta)',
          data: real,
          borderColor: '#00e5ff',
          backgroundColor: 'rgba(0, 229, 255, 0.08)',
          borderWidth: 2.5,
          tension: 0.25,
          pointRadius: 3,
          pointBackgroundColor: '#00e5ff',
          pointBorderColor: '#0d0e12',
          spanGaps: false,
          fill: true,
        },
        {
          label: projLabel,
          data: proj,
          borderColor: projBorder,
          backgroundColor: riskFill,
          borderWidth: this.novoFuturoCiano ? 2.5 : 2,
          borderDash: this.novoFuturoCiano ? [14, 7] : [10, 6],
          tension: 0.2,
          pointRadius: pointRadiiProj,
          pointHoverRadius: pointRadiiProj.map((r) => (r > 0 ? r + 2 : 0)),
          pointBackgroundColor: (ctx: { dataIndex: number }) => {
            const p = dto.pontos[ctx.dataIndex];
            if (p?.serie === 'PROJETADO' && marcos.has(p.dia) && p.dia > dto.diaHoje) {
              return 'rgba(255, 193, 7, 0.95)';
            }
            if (p?.serie === 'PROJETADO' && marcosProv.has(p.dia) && p.dia > dto.diaHoje) {
              return 'rgba(186, 104, 200, 0.95)';
            }
            return 'rgba(0,0,0,0)';
          },
          pointBorderColor: (ctx: { dataIndex: number }) => {
            const p = dto.pontos[ctx.dataIndex];
            if (p?.serie === 'PROJETADO' && marcos.has(p.dia) && p.dia > dto.diaHoje) {
              return '#0d0e12';
            }
            if (p?.serie === 'PROJETADO' && marcosProv.has(p.dia) && p.dia > dto.diaHoje) {
              return '#0d0e12';
            }
            return 'transparent';
          },
          pointStyle: (ctx: { dataIndex: number }) => {
            const p = dto.pontos[ctx.dataIndex];
            if (p?.serie === 'PROJETADO' && marcos.has(p.dia) && p.dia > dto.diaHoje) {
              return 'rectRot';
            }
            if (p?.serie === 'PROJETADO' && marcosProv.has(p.dia) && p.dia > dto.diaHoje) {
              return 'triangle';
            }
            return 'circle';
          },
          spanGaps: false,
          fill: negativaVisual ? 'origin' : false,
        },
      ],
    };

    this.lineOptions = {
      ...this.lineOptions,
      plugins: {
        ...this.lineOptions.plugins,
        tooltip: {
          ...this.lineOptions.plugins?.tooltip,
          callbacks: {
            ...this.lineOptions.plugins?.tooltip?.callbacks,
            afterBody: (items) => {
              const it = items[0];
              if (!it) {
                return [];
              }
              const p = dto.pontos[it.dataIndex];
              if (p && marcosProv.has(p.dia) && p.serie === 'PROJETADO') {
                const pr = provPorDia.get(p.dia);
                const v = pr?.valor != null ? brl.format(pr.valor) : '—';
                const per = pr?.periodoHistorico ?? 'histórico';
                return [
                  `❔ Provisão de memória — Sentinela previu ~${v} com base em ${per} (${pr?.rotulo ?? 'evento sazonal'}).`,
                ];
              }
              if (p && marcos.has(p.dia) && p.serie === 'PROJETADO') {
                return [`🔒 Obrigação fixa — queda de caixa neste dia (${brl.format(p.saldo)} saldo projetado)`];
              }
              return [];
            },
          },
        },
      },
    };

    this.resumoFimMes = brl.format(dto.saldoProjetadoFimMes ?? 0);
  }
}
