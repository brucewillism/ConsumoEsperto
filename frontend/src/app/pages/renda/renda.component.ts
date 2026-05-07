import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { BaseChartDirective } from 'ng2-charts';
import { ChartConfiguration, ChartOptions } from 'chart.js';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { ContrachequeDto, RendaConfigService } from '../../services/renda-config.service';
import { ToastService } from '../../services/toast.service';

@Component({
  selector: 'app-renda',
  standalone: true,
  imports: [CommonModule, BaseChartDirective, MatButtonModule, MatCardModule, MatIconModule],
  templateUrl: './renda.component.html',
  styleUrl: './renda.component.scss'
})
export class RendaComponent implements OnInit {
  contracheques: ContrachequeDto[] = [];
  carregando = true;
  enviandoPdf = false;
  chartData: ChartConfiguration<'bar'>['data'] = { labels: [], datasets: [] };
  chartOptions: ChartOptions<'bar'> = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: { legend: { labels: { color: '#e2e8f0' } } },
    scales: {
      x: { ticks: { color: '#94a3b8' }, grid: { color: 'rgba(51,65,85,.45)' } },
      y: { ticks: { color: '#94a3b8' }, grid: { color: 'rgba(51,65,85,.45)' } }
    }
  };

  constructor(private rendaService: RendaConfigService, private toast: ToastService) {}

  ngOnInit(): void {
    this.carregar();
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

  confirmar(c: ContrachequeDto): void {
    this.rendaService.confirmarContracheque(c.id).subscribe({
      next: () => {
        this.toast.success('Contracheque confirmado e renda atualizada.');
        this.carregar();
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

  private syncChart(): void {
    const ordered = [...this.contracheques].reverse().slice(-12);
    this.chartData = {
      labels: ordered.map(c => `${String(c.mes).padStart(2, '0')}/${c.ano}`),
      datasets: [
        { label: 'Bruto', data: ordered.map(c => Number(c.salarioBruto || 0)), backgroundColor: '#38bdf8' },
        { label: 'Líquido', data: ordered.map(c => Number(c.salarioLiquido || 0)), backgroundColor: '#10b981' }
      ]
    };
  }
}
