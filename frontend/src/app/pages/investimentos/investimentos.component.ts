import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatSliderModule } from '@angular/material/slider';
import { MatIconModule } from '@angular/material/icon';
import { OportunidadeInvestimento, ProjecaoDashboardService } from '../../services/projecao-dashboard.service';

@Component({
  selector: 'app-investimentos',
  standalone: true,
  imports: [CommonModule, FormsModule, MatCardModule, MatSliderModule, MatIconModule],
  templateUrl: './investimentos.component.html',
  styleUrl: './investimentos.component.scss'
})
export class InvestimentosComponent implements OnInit {
  oportunidade: OportunidadeInvestimento | null = null;
  valor = 1000;

  constructor(private projecaoService: ProjecaoDashboardService) {}

  ngOnInit(): void {
    this.projecaoService.oportunidadeInvestimento().subscribe({
      next: (o) => {
        this.oportunidade = o;
        this.valor = Math.max(500, Math.round(Number(o.saldoOcioso || 1000)));
      },
      error: () => {
        this.oportunidade = null;
      }
    });
  }

  rendimento(taxaMensal: number, meses: number): number {
    return this.valor * (Math.pow(1 + taxaMensal, meses) - 1);
  }

  brl(v: number): string {
    return Number(v || 0).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  }
}
