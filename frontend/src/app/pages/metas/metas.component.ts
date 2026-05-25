import { Component, OnInit, TemplateRef, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSliderModule } from '@angular/material/slider';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog.component';
import { openCeFormDialog } from '../../shared/ce-form-dialog.util';
import { NovaMetaDialogComponent } from '../../shared/nova-meta-dialog/nova-meta-dialog.component';
import { CeInputMaskDirective } from '../../shared/directives/ce-input-mask.directive';
import { FinancaAlteracaoService } from '../../services/financa-alteracao.service';
import {
  MetaFinanceira,
  MetaFinanceiraRequest,
  MetaFinanceiraService,
  RendaMediaResponse
} from '../../services/meta-financeira.service';
import { ToastService } from '../../services/toast.service';
import { resolveHttpError } from '../../shared/utils/form.utils';
import { ChartMetodologiaComponent } from '../../shared/chart-metodologia/chart-metodologia.component';
import { forkJoin } from 'rxjs';

@Component({
  selector: 'app-metas',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSliderModule,
    MatIconModule,
    MatSelectModule,
    MatDialogModule,
    ChartMetodologiaComponent,
    CeInputMaskDirective,
  ],
  templateUrl: './metas.component.html',
  styleUrl: './metas.component.scss'
})
export class MetasComponent implements OnInit {
  @ViewChild('editMetaTpl') editMetaTpl!: TemplateRef<unknown>;

  metas: MetaFinanceira[] = [];
  rendaInfo: RendaMediaResponse | null = null;
  alertaListagem: string | null = null;
  totalPercentualComprometido = 0;

  percentualSimulador = 15;
  valorSimulado = 3500;

  valorPoupadoPreview = 0;
  prazoMesesPreview = 0;
  dataPrevista: Date | null = null;

  salvando = false;
  carregando = true;

  metaEmEdicao: MetaFinanceira | null = null;
  editDescricao = '';
  editValor = 0;
  editPercentual = 15;
  editPrioridade = 3;
  editMetaAlerta = '';

  constructor(
    private metaService: MetaFinanceiraService,
    private toast: ToastService,
    private dialog: MatDialog,
    private financaAlteracao: FinancaAlteracaoService
  ) {}

  ngOnInit(): void {
    this.carregar();
  }

  carregar(): void {
    this.carregando = true;
    forkJoin({
      renda: this.metaService.rendaMedia(),
      lista: this.metaService.listar()
    }).subscribe({
      next: ({ renda, lista }) => {
        this.rendaInfo = renda;
        this.metas = lista.metas;
        this.alertaListagem = lista.alertaComprometimento;
        this.totalPercentualComprometido = Number(lista.totalPercentualComprometido) || 0;
        this.recalcularPreview();
        this.carregando = false;
      },
      error: () => {
        this.toast.error('Erro ao carregar metas ou renda média.');
        this.carregando = false;
      }
    });
  }

  formatPct = (v: number): string => `${v}%`;

  onSliderChange(): void {
    this.recalcularPreview();
  }

  onValorSimuladoChange(): void {
    this.recalcularPreview();
  }

  recalcularPreview(): void {
    const renda = this.rendaInfo?.rendaMensalMedia ?? 0;
    this.valorPoupadoPreview = Math.round((renda * this.percentualSimulador) / 100 * 100) / 100;
    if (this.valorPoupadoPreview <= 0 || !this.valorSimulado || this.valorSimulado <= 0) {
      this.prazoMesesPreview = 0;
      this.dataPrevista = null;
      return;
    }
    this.prazoMesesPreview = Math.round((this.valorSimulado / this.valorPoupadoPreview) * 100) / 100;
    const dias = Math.ceil(this.prazoMesesPreview * 30);
    const d = new Date();
    d.setDate(d.getDate() + dias);
    this.dataPrevista = d;
  }

  /** Soma dos % das metas já cadastradas + % do simulador (nova meta). */
  get totalComprometidoProjetado(): number {
    const base = this.metas.reduce((s, m) => s + Number(m.percentualComprometimento), 0);
    return Math.round((base + this.percentualSimulador) * 100) / 100;
  }

  get alertaPreviewSimulador(): string | null {
    const t = this.totalComprometidoProjetado;
    if (t < 15) {
      return null;
    }
    const pct =
      t % 1 === 0 ? String(Math.round(t)) : t.toLocaleString('pt-BR', { minimumFractionDigits: 1, maximumFractionDigits: 1 });
    return `Cuidado, com essa nova meta, você agora compromete ${pct}% da sua renda total!`;
  }

  prioridadeLabel(p: number): string {
    const labels: Record<number, string> = {
      1: 'Baixa',
      2: 'Moderada',
      3: 'Média',
      4: 'Alta',
      5: 'Máxima'
    };
    return labels[p] ?? String(p);
  }

  abrirNovaMeta(): void {
    openCeFormDialog(this.dialog, NovaMetaDialogComponent, {
      width: '480px',
      data: {
        percentualSimulador: this.percentualSimulador,
        rendaInfo: this.rendaInfo,
      },
    })
      .afterClosed()
      .subscribe((criada) => {
        if (criada) {
          this.financaAlteracao.notificar();
          this.carregar();
        }
      });
  }

  abrirEdicao(m: MetaFinanceira): void {
    this.metaEmEdicao = m;
    this.editMetaAlerta = '';
    this.editDescricao = m.descricao;
    this.editValor = m.valorTotal;
    this.editPercentual = m.percentualComprometimento;
    this.editPrioridade = m.prioridade;
    openCeFormDialog(this.dialog, this.editMetaTpl, { width: '460px' });
  }

  confirmarEdicaoMeta(): void {
    if (!this.metaEmEdicao?.id) {
      return;
    }
    this.editMetaAlerta = '';
    if (!this.editDescricao?.trim()) {
      this.editMetaAlerta = 'Informe a descrição da meta.';
      return;
    }
    if (!this.editValor || this.editValor <= 0) {
      this.editMetaAlerta = 'Informe um valor total maior que zero.';
      return;
    }
    if (this.editPercentual < 1 || this.editPercentual > 100) {
      this.editMetaAlerta = 'O percentual da renda deve estar entre 1% e 100%.';
      return;
    }
    this.salvando = true;
    const body: MetaFinanceiraRequest = {
      descricao: this.editDescricao.trim(),
      valorTotal: this.editValor,
      percentualComprometimento: this.editPercentual,
      prioridade: this.editPrioridade
    };
    this.metaService.atualizar(this.metaEmEdicao.id, body).subscribe({
      next: (res) => {
        this.salvando = false;
        this.dialog.closeAll();
        this.metaEmEdicao = null;
        this.toast.success('Meta atualizada.');
        if (res.alertaComprometimento) {
          this.toast.warning(res.alertaComprometimento);
        }
        this.financaAlteracao.notificar();
        this.carregar();
      },
      error: (e) => {
        this.salvando = false;
        this.editMetaAlerta = resolveHttpError(e, 'Erro ao atualizar meta.');
      }
    });
  }

  excluir(id: number): void {
    const ref = this.dialog.open(ConfirmDialogComponent, {
      width: '400px',
      data: {
        title: 'Excluir meta',
        message: 'Remover esta meta permanentemente?',
        confirmLabel: 'Excluir',
        destructive: true
      }
    });
    ref.afterClosed().subscribe((ok) => {
      if (!ok) {
        return;
      }
      this.metaService.excluir(id).subscribe({
        next: () => {
          this.toast.success('Meta removida.');
          this.financaAlteracao.notificar();
          this.carregar();
        },
        error: () => this.toast.error('Erro ao remover meta.')
      });
    });
  }

  formatMoney(v: number): string {
    return v.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  }

  formatDate(d: Date | null): string {
    if (!d) {
      return '—';
    }
    return d.toLocaleDateString('pt-BR');
  }

  /** Circunferência do anel SVG (r = 42 em viewBox 100). */
  readonly energyRingCircumference = 2 * Math.PI * 42;

  ringDashOffset(percent: number): number {
    const p = Math.max(0, Number(percent) || 0);
    const capped = Math.min(p, 100);
    return this.energyRingCircumference * (1 - capped / 100);
  }

  ringEnergyClass(percent: number): string {
    const p = Number(percent) || 0;
    if (p > 100) {
      return 'ring-energy-critical';
    }
    if (p >= 80) {
      return 'ring-energy-high';
    }
    return 'ring-energy-norm';
  }
}
