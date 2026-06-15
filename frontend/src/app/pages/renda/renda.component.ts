import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { BaseChartDirective } from 'ng2-charts';
import { ChartConfiguration, ChartOptions } from 'chart.js';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatInputModule } from '@angular/material/input';
import { ContrachequeDto, RendaConfigDto, RendaConfigService, TipoConfiguracaoRenda } from '../../services/renda-config.service';
import {
  ConfiguracaoFiscalDto,
  PlanejamentoFiscalResumoDto,
  PlanejamentoFiscalService
} from '../../services/planejamento-fiscal.service';
import { ToastService } from '../../services/toast.service';
import { ChartMetodologiaComponent } from '../../shared/chart-metodologia/chart-metodologia.component';
import { WhatsappParityHintComponent } from '../../shared/whatsapp-parity-hint/whatsapp-parity-hint.component';

interface MesOpcao {
  valor: number;
  rotulo: string;
}

@Component({
  selector: 'app-renda',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    BaseChartDirective,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatSelectModule,
    MatSlideToggleModule,
    MatInputModule,
    ChartMetodologiaComponent,
    WhatsappParityHintComponent
  ],
  templateUrl: './renda.component.html',
  styleUrl: './renda.component.scss'
})
export class RendaComponent implements OnInit {
  contracheques: ContrachequeDto[] = [];
  expandedId: string | number | null = null;
  carregando = true;
  enviandoPdf = false;
  carregandoConfig = true;
  salvandoConfig = false;

  config: RendaConfigDto | null = null;
  tipoConfiguracaoRenda: TipoConfiguracaoRenda = 'CONTRACHEQUE';
  valorRecebimentoUnico: number | null = null;
  salarioBruto: number | null = null;
  diaPagamento: number | null = null;
  receitaAutomaticaAtiva = false;

  readonly tiposRenda: { valor: TipoConfiguracaoRenda; rotulo: string; descricao: string }[] = [
    { valor: 'CONTRACHEQUE', rotulo: 'Contracheque (CLT)', descricao: 'Salário fixo com holerite e descontos' },
    { valor: 'RECEBIMENTO_UNICO', rotulo: 'Recebimento único', descricao: 'Um valor fixo por mês (PIX, aluguel, honorário)' },
    { valor: 'FLUXO_DIARIO', rotulo: 'Fluxo diário', descricao: 'Renda variável — estimativa pela média dos últimos 30 dias' }
  ];

  Math = Math;
  chartData: ChartConfiguration<'bar'>['data'] = { labels: [], datasets: [] };
  chartOptions: ChartOptions<'bar'> = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: { legend: { labels: { color: '#94a3b8' } } },
    scales: {
      x: { ticks: { color: '#64748b' }, grid: { color: 'rgba(0, 229, 255, 0.06)' } },
      y: { ticks: { color: '#64748b' }, grid: { color: 'rgba(0, 229, 255, 0.06)' } }
    }
  };

  readonly meses: MesOpcao[] = [
    { valor: 1, rotulo: 'Janeiro' },
    { valor: 2, rotulo: 'Fevereiro' },
    { valor: 3, rotulo: 'Março' },
    { valor: 4, rotulo: 'Abril' },
    { valor: 5, rotulo: 'Maio' },
    { valor: 6, rotulo: 'Junho' },
    { valor: 7, rotulo: 'Julho' },
    { valor: 8, rotulo: 'Agosto' },
    { valor: 9, rotulo: 'Setembro' },
    { valor: 10, rotulo: 'Outubro' },
    { valor: 11, rotulo: 'Novembro' },
    { valor: 12, rotulo: 'Dezembro' }
  ];

  readonly diasPagamento = Array.from({ length: 28 }, (_, i) => i + 1);

  fiscal: ConfiguracaoFiscalDto = {
    mesRestituicaoIr: null,
    valorRestituicao: null,
    tipoRecebimento13: null,
    mesParcelaUnica: null,
    mesPrimeiraParcela: null,
    mesSegundaParcela: 12,
    diaPagamento13: null,
    provisionamentoAtivo: true
  };

  simulacao: PlanejamentoFiscalResumoDto | null = null;
  carregandoFiscal = true;
  salvandoFiscal = false;

  constructor(
    private rendaService: RendaConfigService,
    private fiscalService: PlanejamentoFiscalService,
    private toast: ToastService
  ) {}

  ngOnInit(): void {
    this.carregarConfig();
  }

  isPerfilClt(): boolean {
    return this.tipoConfiguracaoRenda === 'CONTRACHEQUE';
  }

  private sincronizarSecaoClt(): void {
    if (this.isPerfilClt()) {
      this.carregarFiscal();
      this.carregar();
      return;
    }
    this.carregandoFiscal = false;
    this.simulacao = null;
    this.contracheques = [];
    this.carregando = false;
    this.syncChart();
  }

  carregarConfig(): void {
    this.carregandoConfig = true;
    this.rendaService.obter().subscribe({
      next: (cfg) => {
        this.config = cfg;
        this.tipoConfiguracaoRenda = cfg.tipoConfiguracaoRenda ?? 'CONTRACHEQUE';
        this.valorRecebimentoUnico = cfg.valorRecebimentoUnico ?? null;
        this.salarioBruto = cfg.salarioBruto > 0 ? cfg.salarioBruto : null;
        this.diaPagamento = cfg.diaPagamento;
        this.receitaAutomaticaAtiva = cfg.receitaAutomaticaAtiva ?? false;
        this.carregandoConfig = false;
        this.sincronizarSecaoClt();
      },
      error: () => {
        this.toast.error('Erro ao carregar perfil de renda.');
        this.carregandoConfig = false;
      }
    });
  }

  salvarPerfilRenda(): void {
    if (this.tipoConfiguracaoRenda === 'RECEBIMENTO_UNICO' && this.valorRecebimentoUnico != null && this.valorRecebimentoUnico < 0) {
      this.toast.warning('O valor recebido não pode ser negativo.');
      return;
    }
    if (this.tipoConfiguracaoRenda !== 'FLUXO_DIARIO' && !this.diaPagamento) {
      this.toast.warning('Informe o dia do recebimento/pagamento.');
      return;
    }

    this.salvandoConfig = true;
    this.rendaService.salvar({
      tipoConfiguracaoRenda: this.tipoConfiguracaoRenda,
      valorRecebimentoUnico: this.tipoConfiguracaoRenda === 'RECEBIMENTO_UNICO' ? this.valorRecebimentoUnico ?? undefined : undefined,
      salarioBruto: this.tipoConfiguracaoRenda === 'CONTRACHEQUE' ? (this.salarioBruto ?? 0) : undefined,
      diaPagamento: this.tipoConfiguracaoRenda === 'FLUXO_DIARIO' ? undefined : this.diaPagamento ?? undefined,
      receitaAutomaticaAtiva: this.tipoConfiguracaoRenda === 'FLUXO_DIARIO' ? false : this.receitaAutomaticaAtiva
    }).subscribe({
      next: (cfg) => {
        this.config = cfg;
        this.tipoConfiguracaoRenda = cfg.tipoConfiguracaoRenda ?? this.tipoConfiguracaoRenda;
        this.toast.success('Perfil de renda salvo.');
        this.salvandoConfig = false;
        this.sincronizarSecaoClt();
      },
      error: (e) => {
        this.toast.error(e?.error?.message || 'Erro ao salvar perfil de renda.');
        this.salvandoConfig = false;
      }
    });
  }

  rendaEstimadaAtual(): number {
    return Number(this.config?.rendaMensalEstimada ?? this.config?.salarioLiquido ?? 0);
  }

  carregar(): void {
    this.carregando = true;
    this.rendaService.historicoContracheques().subscribe({
      next: (res) => {
        this.contracheques = res;
        this.syncChart();
        this.carregando = false;
      },
      error: () => {
        this.toast.error('Erro ao carregar histórico de contracheques.');
        this.carregando = false;
      }
    });
  }

  carregarFiscal(): void {
    this.carregandoFiscal = true;
    this.fiscalService.obter().subscribe({
      next: (cfg) => {
        this.fiscal = this.normalizarFiscal(cfg);
        this.atualizarSimulacao();
        this.carregandoFiscal = false;
      },
      error: () => {
        this.toast.error('Erro ao carregar calendário do 13º.');
        this.carregandoFiscal = false;
      }
    });
  }

  atualizarSimulacao(): void {
    this.fiscalService.simular().subscribe({
      next: (res) => {
        this.simulacao = res;
      },
      error: () => {
        this.simulacao = null;
      }
    });
  }

  salvarCalendario(): void {
    this.fiscal = this.normalizarFiscal(this.fiscal);

    if (!this.fiscal.tipoRecebimento13) {
      this.toast.warning('Escolha como você recebe o 13º salário.');
      return;
    }
    if (this.fiscal.tipoRecebimento13 === 'DUAS_PARCELAS') {
      if (!this.fiscal.mesPrimeiraParcela) {
        this.toast.warning('Informe o mês da 1ª parcela do 13º.');
        return;
      }
      if (!this.fiscal.mesSegundaParcela) {
        this.fiscal.mesSegundaParcela = 12;
      }
    } else if (!this.fiscal.mesParcelaUnica) {
      this.toast.warning('Informe o mês do pagamento integral do 13º.');
      return;
    }

    this.salvandoFiscal = true;
    this.fiscalService.salvar({
      tipoRecebimento13: this.fiscal.tipoRecebimento13,
      mesParcelaUnica: this.fiscal.mesParcelaUnica,
      mesPrimeiraParcela: this.fiscal.mesPrimeiraParcela,
      mesSegundaParcela: this.fiscal.mesSegundaParcela,
      diaPagamento13: this.fiscal.diaPagamento13,
      provisionamentoAtivo: this.fiscal.provisionamentoAtivo,
      mesRestituicaoIr: this.fiscal.mesRestituicaoIr,
      valorRestituicao: this.fiscal.valorRestituicao
    }).subscribe({
      next: () => {
        this.toast.success('Calendário do 13º salvo. Provisões atualizadas no fluxo de caixa.');
        this.salvandoFiscal = false;
        this.atualizarSimulacao();
      },
      error: (e) => {
        this.toast.error(e?.error?.message || 'Erro ao salvar calendário do 13º.');
        this.salvandoFiscal = false;
      }
    });
  }

  onTipoRecebimentoChange(): void {
    this.fiscal = this.normalizarFiscal(this.fiscal);
  }

  /** Preenche padrões CLT quando a config ainda está vazia (primeiro uso). */
  private normalizarFiscal(cfg: ConfiguracaoFiscalDto): ConfiguracaoFiscalDto {
    const tipo = cfg.tipoRecebimento13 ?? 'DUAS_PARCELAS';
    return {
      ...cfg,
      tipoRecebimento13: tipo,
      mesSegundaParcela: cfg.mesSegundaParcela ?? 12,
      mesPrimeiraParcela:
        tipo === 'DUAS_PARCELAS' ? (cfg.mesPrimeiraParcela ?? 11) : cfg.mesPrimeiraParcela,
      mesParcelaUnica:
        tipo === 'PARCELA_UNICA' ? (cfg.mesParcelaUnica ?? 12) : cfg.mesParcelaUnica,
      provisionamentoAtivo: cfg.provisionamentoAtivo ?? true,
    };
  }

  parcelasDecimo(): Array<{ mes: number; dia: number; rotulo: string; valor: number }> {
    const base = this.simulacao?.baseContracheque;
    if (!base || !this.fiscal.tipoRecebimento13) {
      return [];
    }

    const bruto = Number(base.salarioBruto || 0);
    const liquido = Number(base.salarioLiquido || 0);
    if (bruto <= 0 || liquido <= 0) {
      return [];
    }

    const dia = this.fiscal.diaPagamento13
      ?? this.simulacao?.parcelas?.find((p) => p.origem?.includes('DECIMO'))?.dia
      ?? 5;
    const ano = new Date().getFullYear();

    if (this.fiscal.tipoRecebimento13 === 'PARCELA_UNICA') {
      if (!this.fiscal.mesParcelaUnica) {
        return [];
      }
      return [{
        mes: this.fiscal.mesParcelaUnica,
        dia,
        rotulo: `13º salário — parcela única ${ano} (previsto)`,
        valor: liquido
      }];
    }

    if (!this.fiscal.mesPrimeiraParcela) {
      return [];
    }

    const primeira = Math.round(bruto * 0.5 * 100) / 100;
    const segunda = Math.max(0, Math.round((liquido - primeira) * 100) / 100);
    const mesSegunda = this.fiscal.mesSegundaParcela ?? 12;

    return [
      {
        mes: this.fiscal.mesPrimeiraParcela,
        dia,
        rotulo: `13º salário — 1ª parcela (50% bruto) ${ano} (previsto)`,
        valor: primeira
      },
      {
        mes: mesSegunda,
        dia,
        rotulo: `13º salário — 2ª parcela ${ano} (previsto)`,
        valor: segunda
      }
    ];
  }

  mesRotulo(mes: number | null | undefined): string {
    if (!mes) {
      return '—';
    }
    return this.meses.find((m) => m.valor === mes)?.rotulo ?? String(mes);
  }

  dataParcela(mes: number, dia: number): string {
    const ano = new Date().getFullYear();
    return `${String(dia).padStart(2, '0')}/${String(mes).padStart(2, '0')}/${ano}`;
  }

  confirmar(c: ContrachequeDto): void {
    this.rendaService.confirmarContracheque(c.id).subscribe({
      next: () => {
        this.toast.success(
          'Contracheque confirmado. Renda atualizada e receita automática do salário líquido ativada ' +
            '(lança todo mês no teu dia de pagamento, à soma das outras receitas).'
        );
        this.carregar();
        this.atualizarSimulacao();
      },
      error: (e) => this.toast.error(e?.error?.message || 'Erro ao confirmar contracheque.')
    });
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    this.enviarPdf(file);
    input.value = '';
  }

  enviarPdf(file: File): void {
    if (!file.name.toLowerCase().endsWith('.pdf')) {
      this.toast.warning('Envie um PDF de contracheque.');
      return;
    }
    this.enviandoPdf = true;
    this.rendaService.uploadContracheque(file).subscribe({
      next: () => {
        this.toast.success('Contracheque lido pela IA. Revise e confirme.');
        this.enviandoPdf = false;
        this.carregar();
      },
      error: (e) => {
        this.toast.error(e?.error?.message || 'Erro ao processar PDF.');
        this.enviandoPdf = false;
      }
    });
  }

  brl(v: number): string {
    return Number(v || 0).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  }

  brlSemPrefixoDelta(delta: number | null | undefined): string {
    const v = Math.abs(Number(delta ?? 0));
    return v.toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }

  toggleExpand(id: string | number): void {
    this.expandedId = this.expandedId === id ? null : id;
  }

  lineId(i: number): string {
    return String(i + 1).padStart(2, '0');
  }

  private syncChart(): void {
    const ordered = [...this.contracheques].reverse().slice(-12);
    this.chartData = {
      labels: ordered.map(c => `${String(c.mes).padStart(2, '0')}/${c.ano}`),
      datasets: [
        { label: 'Bruto', data: ordered.map(c => Number(c.salarioBruto || 0)), backgroundColor: '#00e5ff' },
        { label: 'Líquido', data: ordered.map(c => Number(c.salarioLiquido || 0)), backgroundColor: '#7b1fa2' }
      ]
    };
  }
}
