import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatSliderModule } from '@angular/material/slider';
import { MatIconModule } from '@angular/material/icon';
import { forkJoin } from 'rxjs';
import { PrevisaoFuturoChartComponent } from '../../components/previsao-futuro-chart/previsao-futuro-chart.component';
import { OportunidadeInvestimento, ProjecaoDashboardService } from '../../services/projecao-dashboard.service';
import { ScoreService, UsuarioScore } from '../../services/score.service';

@Component({
  selector: 'app-investimentos',
  standalone: true,
  imports: [CommonModule, FormsModule, MatCardModule, MatSliderModule, MatIconModule, PrevisaoFuturoChartComponent],
  templateUrl: './investimentos.component.html',
  styleUrl: './investimentos.component.scss'
})
export class InvestimentosComponent implements OnInit {
  oportunidade: OportunidadeInvestimento | null = null;
  usuarioScore: UsuarioScore | null = null;
  valor = 1000;

  constructor(private projecaoService: ProjecaoDashboardService, private scoreService: ScoreService) {}

  ngOnInit(): void {
    forkJoin({
      op: this.projecaoService.oportunidadeInvestimento(),
      sc: this.scoreService.obter()
    }).subscribe({
      next: ({ op, sc }) => {
        this.oportunidade = op;
        this.usuarioScore = sc;
        this.valor = Math.max(500, Math.round(Number(op.saldoOcioso || 1000)));
      },
      error: () => {
        this.projecaoService.oportunidadeInvestimento().subscribe({
          next: (o) => {
            this.oportunidade = o;
            this.valor = Math.max(500, Math.round(Number(o.saldoOcioso || 1000)));
          },
          error: () => (this.oportunidade = null)
        });
        this.scoreService.obter().subscribe({
          next: (s) => (this.usuarioScore = s),
          error: () => (this.usuarioScore = null)
        });
      }
    });
  }

  /** 0,35–1,15: score alto reduz “custo de oportunidade” exibido (disciplina / eficiência). */
  get fatorDisciplina(): number {
    const s = Number(this.usuarioScore?.score ?? 500);
    const raw = s / 720;
    return Math.min(1.15, Math.max(0.35, raw));
  }

  /** Mensagem técnica: rendimento CDB de referência ajustado pelo fator de score. */
  get custoOportunidadeMensal(): number {
    const base = Number(this.oportunidade?.rendimentoCdb ?? 0);
    if (base <= 0) {
      return 0;
    }
    const gap = base * (1 - this.fatorDisciplina / 1.15);
    return Math.max(0, Math.round(gap * 100) / 100);
  }

  rendimento(taxaMensal: number, meses: number): number {
    return this.valor * (Math.pow(1 + taxaMensal, meses) - 1);
  }

  brl(v: number): string {
    return Number(v || 0).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  }
}
