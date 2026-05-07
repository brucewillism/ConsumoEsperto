import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { forkJoin } from 'rxjs';
import { Categoria } from '../../models/categoria.model';
import { CategoriaService } from '../../services/categoria.service';
import { ForecastFinanceiro, Orcamento, OrcamentoService } from '../../services/orcamento.service';
import { ToastService } from '../../services/toast.service';

@Component({
  selector: 'app-orcamentos',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressBarModule,
    MatSelectModule,
    MatCheckboxModule
  ],
  templateUrl: './orcamentos.component.html',
  styleUrl: './orcamentos.component.scss'
})
export class OrcamentosComponent implements OnInit {
  orcamentos: Orcamento[] = [];
  categorias: Categoria[] = [];
  forecast: ForecastFinanceiro | null = null;
  carregando = true;
  salvando = false;

  categoriaId: number | null = null;
  valorLimite: number | null = null;
  mes = new Date().getMonth() + 1;
  ano = new Date().getFullYear();
  compartilhado = false;

  constructor(
    private orcamentoService: OrcamentoService,
    private categoriaService: CategoriaService,
    private toast: ToastService
  ) {}

  ngOnInit(): void {
    this.carregar();
  }

  carregar(): void {
    this.carregando = true;
    forkJoin({
      orcamentos: this.orcamentoService.listar(this.mes, this.ano),
      categorias: this.categoriaService.buscarPorUsuario(),
      forecast: this.orcamentoService.forecast()
    }).subscribe({
      next: ({ orcamentos, categorias, forecast }) => {
        this.orcamentos = orcamentos;
        this.categorias = categorias;
        this.forecast = forecast;
        this.carregando = false;
      },
      error: () => {
        this.toast.error('Erro ao carregar orçamentos.');
        this.carregando = false;
      }
    });
  }

  salvar(): void {
    if (!this.categoriaId || !this.valorLimite || this.valorLimite <= 0) {
      this.toast.warning('Informe categoria e limite mensal.');
      return;
    }
    this.salvando = true;
    this.orcamentoService.salvar({
      categoriaId: this.categoriaId,
      valorLimite: this.valorLimite,
      mes: this.mes,
      ano: this.ano,
      compartilhado: this.compartilhado
    }).subscribe({
      next: () => {
        this.toast.success('Orçamento salvo.');
        this.categoriaId = null;
        this.valorLimite = null;
        this.compartilhado = false;
        this.salvando = false;
        this.carregar();
      },
      error: (e) => {
        this.toast.error(e?.error?.message || 'Erro ao salvar orçamento.');
        this.salvando = false;
      }
    });
  }

  excluir(o: Orcamento): void {
    this.orcamentoService.excluir(o.id).subscribe({
      next: () => {
        this.toast.success('Orçamento removido.');
        this.carregar();
      },
      error: () => this.toast.error('Erro ao remover orçamento.')
    });
  }

  cor(o: Orcamento): string {
    if (o.percentualUso >= 90) return 'danger';
    if (o.percentualUso >= 70) return 'warning';
    return 'success';
  }

  progress(o: Orcamento): number {
    return Math.min(100, Math.max(0, Number(o.percentualUso) || 0));
  }

  brl(v: number | null | undefined): string {
    return Number(v || 0).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  }
}
