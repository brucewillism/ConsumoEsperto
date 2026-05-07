import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { BaseChartDirective } from 'ng2-charts';
import { ChartConfiguration, ChartOptions } from 'chart.js';
import { MatCardModule } from '@angular/material/card';
import { HistoricoScore, ScoreService, UsuarioScore } from '../../services/score.service';

@Component({
  selector: 'app-score',
  standalone: true,
  imports: [CommonModule, BaseChartDirective, MatCardModule],
  templateUrl: './score.component.html',
  styleUrl: './score.component.scss'
})
export class ScoreComponent implements OnInit {
  score: UsuarioScore | null = null;
  historico: HistoricoScore[] = [];
  chartData: ChartConfiguration<'line'>['data'] = { labels: [], datasets: [] };
  chartOptions: ChartOptions<'line'> = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: { legend: { labels: { color: '#e5e7eb' } } },
    scales: {
      x: { ticks: { color: '#94a3b8' }, grid: { color: 'rgba(51,65,85,.45)' } },
      y: { ticks: { color: '#94a3b8' }, grid: { color: 'rgba(51,65,85,.45)' }, min: 0, max: 1000 }
    }
  };

  constructor(private scoreService: ScoreService) {}

  ngOnInit(): void {
    this.scoreService.obter().subscribe((score) => this.score = score);
    this.scoreService.historico().subscribe((historico) => {
      this.historico = historico;
      const ordered = [...historico].reverse();
      this.chartData = {
        labels: ordered.map(h => new Date(h.dataEvento).toLocaleDateString('pt-BR')),
        datasets: [{
          label: 'Score',
          data: ordered.map(h => h.scoreResultante),
          borderColor: '#38bdf8',
          backgroundColor: 'rgba(56, 189, 248, .16)',
          tension: .35,
          fill: true
        }]
      };
    });
  }
}
