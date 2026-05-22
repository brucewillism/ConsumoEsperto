import { ChartConfiguration } from 'chart.js';
import {
  DashboardProjection,
  SerieProjecaoSafraDTO,
  normalizarSafra,
} from '../models/dashboard-projection.model';

export interface SafraCascadeChartResult {
  labels: string[];
  real: Array<number | null>;
  projetado: Array<number | null>;
  simulado: Array<number | null>;
  safraCascata: Array<number | null>;
  mesesSafra: SerieProjecaoSafraDTO[];
  indiceInicioSafra: number;
}

const PONTOS_POR_MES_FUTURO = 4;

/**
 * Estende a série diária do mês corrente com a curva cascata M+1 e M+2.
 * O saldo final de cada mês alimenta o patrimônio inicial do seguinte.
 */
export function buildSafraCascadeChart(projection: DashboardProjection | null | undefined): SafraCascadeChartResult | null {
  if (!projection?.labels?.length || !projection.projetado?.length) {
    return null;
  }

  const mesesSafra = normalizarSafra(projection.safraPatrimonio);
  const labels = [...projection.labels.map(String)];
  const real: Array<number | null> = [...projection.real];
  const projetado: Array<number | null> = [...projection.projetado];
  const simulado: Array<number | null> = [...(projection.simulado ?? projection.projetado)];
  const safraCascata: Array<number | null> = new Array(projection.labels.length).fill(null);

  if (mesesSafra.length <= 1) {
    return { labels, real, projetado, simulado, safraCascata, mesesSafra, indiceInicioSafra: -1 };
  }

  const indiceInicioSafra = labels.length;
  const ultimoIdx = projetado.length - 1;
  const saldoFimMesAtual = mesesSafra[0]?.saldoFinalProjetado ?? num(projetado[ultimoIdx]);

  if (ultimoIdx >= 0) {
    projetado[ultimoIdx] = saldoFimMesAtual;
    if (real[ultimoIdx] != null) {
      real[ultimoIdx] = saldoFimMesAtual;
    }
    simulado[ultimoIdx] = num(simulado[ultimoIdx]) || saldoFimMesAtual;
    safraCascata[ultimoIdx] = saldoFimMesAtual;
  }

  let valorCorrente = saldoFimMesAtual;

  for (let i = 1; i < mesesSafra.length; i++) {
    const mes = mesesSafra[i];
    const saldoFinal = mes.saldoFinalProjetado;
    const saldoInicial = mes.saldoInicial ?? valorCorrente;
    const rotulo = formatarRotuloCurto(mes.mesReferencia, i);

    for (let p = 1; p <= PONTOS_POR_MES_FUTURO; p++) {
      const t = p / PONTOS_POR_MES_FUTURO;
      const valor = saldoInicial + (saldoFinal - saldoInicial) * t;
      const sufixo = p === PONTOS_POR_MES_FUTURO ? ' fim' : p === 1 ? ' início' : ` +${Math.round((t * 100))}%`;
      labels.push(`${rotulo}${sufixo}`);
      real.push(null);
      projetado.push(valor);
      simulado.push(valor);
      safraCascata.push(valor);
    }

    valorCorrente = saldoFinal;
  }

  return { labels, real, projetado, simulado, safraCascata, mesesSafra, indiceInicioSafra };
}

export function toLineChartDatasets(
  cascade: SafraCascadeChartResult,
  modoSimulacao: boolean,
  simulacoesAtivas: number
): ChartConfiguration<'line'>['data']['datasets'] {
  const datasets: ChartConfiguration<'line'>['data']['datasets'] = [
    {
      label: 'Real',
      data: cascade.real,
      borderColor: '#10b981',
      backgroundColor: 'rgba(16, 185, 129, 0.12)',
      fill: false,
      tension: 0.35,
      pointBackgroundColor: '#10b981',
      borderWidth: 2,
      spanGaps: false,
    },
    {
      label: 'Projetado (mês)',
      data: cascade.projetado,
      borderColor: '#38bdf8',
      backgroundColor: 'rgba(56, 189, 248, 0.10)',
      fill: false,
      tension: 0.35,
      pointBackgroundColor: '#38bdf8',
      borderWidth: 2,
      borderDash: [6, 4],
      spanGaps: true,
    },
  ];

  if (cascade.indiceInicioSafra >= 0 && cascade.mesesSafra.length > 1) {
    datasets.push({
      label: 'Safra cascata (M+1 · M+2)',
      data: cascade.safraCascata,
      borderColor: '#a78bfa',
      backgroundColor: 'rgba(167, 139, 250, 0.12)',
      fill: false,
      tension: 0.4,
      pointBackgroundColor: '#a78bfa',
      pointRadius: 3,
      borderWidth: 2.5,
      spanGaps: true,
    });
  }

  if (modoSimulacao && simulacoesAtivas > 0) {
    datasets.push({
      label: 'Simulado',
      data: cascade.simulado,
      borderColor: '#f59e0b',
      backgroundColor: 'rgba(245, 158, 11, 0.10)',
      fill: false,
      tension: 0.35,
      pointBackgroundColor: '#f59e0b',
      borderWidth: 2,
      borderDash: [2, 5],
      spanGaps: true,
    });
  }

  return datasets;
}

function num(v: number | null | undefined): number {
  const n = Number(v);
  return Number.isFinite(n) ? n : 0;
}

function formatarRotuloCurto(mesReferencia: string, offset: number): string {
  const m = /^(\d{4})-(\d{2})$/.exec(mesReferencia || '');
  if (m) {
    const d = new Date(Number(m[1]), Number(m[2]) - 1, 1);
    const nome = d.toLocaleDateString('pt-BR', { month: 'short' }).replace('.', '');
    return `${nome}/${m[1].slice(2)} (M+${offset})`;
  }
  return mesReferencia || `M+${offset}`;
}
