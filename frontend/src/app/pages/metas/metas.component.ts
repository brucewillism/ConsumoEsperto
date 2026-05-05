import { Component, OnInit, TemplateRef, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSliderModule } from '@angular/material/slider';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog.component';
import { FinancaAlteracaoService } from '../../services/financa-alteracao.service';
import {
  MetaFinanceira,
  MetaFinanceiraRequest,
  MetaFinanceiraService,
  RendaMediaResponse
} from '../../services/meta-financeira.service';
import { ToastService } from '../../services/toast.service';
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
    MatProgressBarModule,
    MatIconModule,
    MatSelectModule,
    MatDialogModule
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

  novaDescricao = '';
  novaValor = 0;
  novaPrioridade = 3;
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

  salvarMeta(): void {
    if (!this.novaDescricao?.trim()) {
      this.toast.warning('Informe a descrição da meta.');
      return;
    }
    if (!this.novaValor || this.novaValor <= 0) {
      this.toast.warning('Informe o valor total da meta.');
      return;
    }
    if (!this.rendaInfo?.calculadaDeLancamentos) {
      this.toast.warning('Cadastre receitas nos últimos 3 meses para calcular a meta, ou use o WhatsApp informando sua renda.');
      return;
    }
    const body: MetaFinanceiraRequest = {
      descricao: this.novaDescricao.trim(),
      valorTotal: this.novaValor,
      percentualComprometimento: this.percentualSimulador,
      prioridade: this.novaPrioridade
    };
    this.salvando = true;
    this.metaService.criar(body).subscribe({
      next: (res) => {
        this.salvando = false;
        this.toast.success('Meta salva com sucesso.');
        if (res.alertaComprometimento) {
          this.toast.warning(res.alertaComprometimento);
        }
        this.financaAlteracao.notificar();
        this.novaDescricao = '';
        this.novaValor = 0;
        this.novaPrioridade = 3;
        this.carregar();
      },
      error: (e) => {
        this.salvando = false;
        this.toast.error(e?.error?.message || 'Erro ao salvar meta.');
      }
    });
  }

  abrirEdicao(m: MetaFinanceira): void {
    this.metaEmEdicao = m;
    this.editDescricao = m.descricao;
    this.editValor = m.valorTotal;
    this.editPercentual = m.percentualComprometimento;
    this.editPrioridade = m.prioridade;
    this.dialog.open(this.editMetaTpl, { width: '460px' });
  }

  confirmarEdicaoMeta(): void {
    if (!this.metaEmEdicao?.id) {
      return;
    }
    if (!this.editDescricao?.trim()) {
      this.toast.warning('Informe a descrição.');
      return;
    }
    if (!this.editValor || this.editValor <= 0) {
      this.toast.warning('Valor inválido.');
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
        this.toast.error(e?.error?.message || 'Erro ao atualizar meta.');
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
}
