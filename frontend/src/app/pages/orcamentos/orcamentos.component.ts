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
import { MatDialog } from '@angular/material/dialog';
import { CE_DIALOG_IMPORTS } from '../../shared/ce-dialog-imports';
import { forkJoin } from 'rxjs';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { openCeFormDialog } from '../../shared/ce-form-dialog.util';
import { NovoOrcamentoDialogComponent } from '../../shared/novo-orcamento-dialog/novo-orcamento-dialog.component';
import { Categoria } from '../../models/categoria.model';
import { CategoriaService } from '../../services/categoria.service';
import { ForecastFinanceiro, Orcamento, OrcamentoService } from '../../services/orcamento.service';
import { ToastService } from '../../services/toast.service';
import { ChartMetodologiaComponent } from '../../shared/chart-metodologia/chart-metodologia.component';
import { PageLoadingComponent } from '../../shared/page-loading/page-loading.component';
import { WhatsappParityHintComponent } from '../../shared/whatsapp-parity-hint/whatsapp-parity-hint.component';

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
    MatCheckboxModule,
    ...CE_DIALOG_IMPORTS,
    MatProgressSpinnerModule,
    ChartMetodologiaComponent,
    PageLoadingComponent,
    WhatsappParityHintComponent,
  ],
  templateUrl: './orcamentos.component.html',
  styleUrl: './orcamentos.component.scss'
})
export class OrcamentosComponent implements OnInit {
  orcamentos: Orcamento[] = [];
  categorias: Categoria[] = [];
  forecast: ForecastFinanceiro | null = null;
  carregando = true;
  carregandoForecast = false;
  mes = new Date().getMonth() + 1;
  ano = new Date().getFullYear();

  constructor(
    private orcamentoService: OrcamentoService,
    private categoriaService: CategoriaService,
    private toast: ToastService,
    private dialog: MatDialog
  ) {}

  ngOnInit(): void {
    this.carregar();
  }

  carregar(): void {
    // Lista + categorias são rápidas: liberam a página de imediato.
    this.carregando = true;
    forkJoin({
      orcamentos: this.orcamentoService.listar(this.mes, this.ano),
      categorias: this.categoriaService.buscarPorUsuario()
    }).subscribe({
      next: ({ orcamentos, categorias }) => {
        this.orcamentos = orcamentos;
        this.categorias = categorias;
        this.carregando = false;
      },
      error: () => {
        this.toast.error('Erro ao carregar orçamentos.');
        this.carregando = false;
      }
    });
    // Forecast usa IA e pode demorar: carrega à parte, sem prender a página.
    this.carregarForecast();
  }

  private carregarForecast(): void {
    this.carregandoForecast = true;
    this.orcamentoService.forecast().subscribe({
      next: (forecast) => {
        this.forecast = forecast;
        this.carregandoForecast = false;
      },
      error: () => {
        this.carregandoForecast = false;
      }
    });
  }

  abrirNovoOrcamento(): void {
    if (!this.categorias.length) {
      this.toast.warning('Cadastre categorias antes de criar um orçamento.');
      return;
    }
    openCeFormDialog(this.dialog, NovoOrcamentoDialogComponent, {
      width: '520px',
      data: { categorias: this.categorias, mes: this.mes, ano: this.ano },
    })
      .afterClosed()
      .subscribe((salvo) => {
        if (salvo) {
          this.carregar();
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
