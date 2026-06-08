import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatIconModule } from '@angular/material/icon';
import { ImportacaoFatura, ImportacaoFaturaService } from '../../services/importacao-fatura.service';
import { ConfirmDialogService } from '../../services/confirm-dialog.service';
import { ToastService } from '../../services/toast.service';
import { resolveHttpError } from '../../shared/utils/form.utils';
import { WhatsappParityHintComponent } from '../../shared/whatsapp-parity-hint/whatsapp-parity-hint.component';

@Component({
  selector: 'app-importacoes-pendentes',
  standalone: true,
  imports: [CommonModule, FormsModule, MatButtonModule, MatCardModule, MatCheckboxModule, MatIconModule, WhatsappParityHintComponent],
  templateUrl: './importacoes-pendentes.component.html',
  styleUrl: './importacoes-pendentes.component.scss'
})
export class ImportacoesPendentesComponent implements OnInit {
  importacoes: ImportacaoFatura[] = [];
  carregando = true;
  confirmandoId: number | null = null;
  dragOver = false;
  enviandoPdf = false;
  apagandoTodas = false;
  apagandoId: number | null = null;

  constructor(
    private importacaoService: ImportacaoFaturaService,
    private toast: ToastService,
    private confirmDialog: ConfirmDialogService
  ) {}

  ngOnInit(): void {
    this.carregar();
  }

  carregar(): void {
    this.carregando = true;
    this.importacaoService.pendentes().subscribe({
      next: (res) => {
        this.importacoes = res;
        this.carregando = false;
      },
      error: () => {
        this.toast.error('Erro ao carregar importações pendentes.');
        this.carregando = false;
      }
    });
  }

  escolhaSaldoAnterior(imp: ImportacaoFatura, somar: boolean): void {
    this.confirmandoId = imp.id;
    this.importacaoService.escolhaSaldoAnterior(imp.id, somar).subscribe({
      next: () => {
        this.toast.success(
          somar
            ? 'Total atualizado: saldo anterior + saldo desta fatura.'
            : 'Total atualizado: apenas saldo desta fatura.'
        );
        this.confirmandoId = null;
        this.carregar();
      },
      error: (e: HttpErrorResponse) => {
        this.toast.errorFromHttpResponse(e, 'Erro ao aplicar escolha do saldo.');
        this.confirmandoId = null;
      }
    });
  }

  apagarTodas(): void {
    if (!this.importacoes.length || this.apagandoTodas) {
      return;
    }
    this.confirmDialog.ask({
      title: 'Apagar todas as importações',
      message: 'Os lançamentos ainda não foram gravados no sistema. Esta ação não pode ser desfeita.',
      confirmLabel: 'Apagar todas',
      destructive: true,
    }).subscribe((ok) => {
      if (!ok) {
        return;
      }
      this.apagandoTodas = true;
      this.importacaoService.excluirTodasPendentes().subscribe({
        next: (res) => {
          this.toast.success(
            res.removidas > 0
              ? `${res.removidas} importação(ões) pendente(s) removida(s).`
              : 'Nenhuma importação pendente para remover.'
          );
          this.apagandoTodas = false;
          this.carregar();
        },
        error: (e: HttpErrorResponse) => {
          this.toast.errorFromHttpResponse(e, 'Erro ao apagar importações pendentes.');
          this.apagandoTodas = false;
        },
      });
    });
  }

  apagarUma(imp: ImportacaoFatura): void {
    this.confirmDialog.ask({
      title: `Descartar importação — ${imp.bancoCartao}`,
      message: 'Os lançamentos desta fatura não serão gravados no sistema.',
      confirmLabel: 'Descartar',
      destructive: true,
    }).subscribe((ok) => {
      if (!ok) {
        return;
      }
      this.apagandoId = imp.id;
      this.importacaoService.excluirPendente(imp.id).subscribe({
        next: () => {
          this.toast.success('Importação pendente removida.');
          this.apagandoId = null;
          this.carregar();
        },
        error: (e: HttpErrorResponse) => {
          this.toast.errorFromHttpResponse(e, 'Erro ao remover importação.');
          this.apagandoId = null;
        },
      });
    });
  }

  confirmar(imp: ImportacaoFatura): void {
    if (imp.aguardandoEscolhaSaldoAnterior) {
      this.toast.warning('Escolha primeiro se deseja somar o saldo anterior ou importar só o saldo atual.');
      return;
    }
    const indices = imp.itens
      .map((item, index) => ({ item, index }))
      .filter(({ item }) => item.novo && item.selecionado)
      .map(({ index }) => index);
    if (!indices.length) {
      this.toast.warning('Selecione pelo menos um lançamento novo.');
      return;
    }
    this.executarConfirmacao(imp, indices, false);
  }

  private executarConfirmacao(imp: ImportacaoFatura, indices: number[], ignorarDivergencia: boolean): void {
    this.confirmandoId = imp.id;
    this.importacaoService.confirmar(imp.id, indices, ignorarDivergencia).subscribe({
      next: (res) => {
        this.toast.success(
          `${res.criadas} lançamento(s) importado(s). Veja no Dashboard se há protocolos de teto sugeridos.`
        );
        this.confirmandoId = null;
        this.carregar();
      },
      error: (e: HttpErrorResponse) => {
        this.confirmandoId = null;
        // 422 = soma dos lançamentos não bate com o total: avisa e deixa confirmar mesmo assim.
        if (e.status === 422) {
          const msg = (e.error && (e.error.message || e.error.error)) || 'A soma dos lançamentos não bate com o total da fatura.';
          this.confirmDialog.ask({
            title: 'Soma não bate com o total',
            message: `${msg}\n\nDeseja importar mesmo assim os lançamentos selecionados? Você pode completar/ajustar os faltantes depois.`,
            confirmLabel: 'Importar mesmo assim',
            cancelLabel: 'Revisar',
          }).subscribe((ok) => {
            if (ok) {
              this.executarConfirmacao(imp, indices, true);
            }
          });
          return;
        }
        this.toast.errorFromHttpResponse(e, 'Erro ao confirmar importação.');
      }
    });
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (file) this.enviarPdf(file);
    input.value = '';
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.dragOver = true;
  }

  onDragLeave(event: DragEvent): void {
    event.preventDefault();
    this.dragOver = false;
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    this.dragOver = false;
    const file = event.dataTransfer?.files?.[0];
    if (file) this.enviarPdf(file);
  }

  enviarPdf(file: File): void {
    if (!file.name.toLowerCase().endsWith('.pdf')) {
      this.toast.warning('Arraste ou selecione uma fatura em PDF.');
      return;
    }
    this.enviandoPdf = true;
    this.importacaoService.upload(file).subscribe({
      next: () => {
        this.toast.success('Fatura processada. Revise a conciliação antes de confirmar.');
        this.enviandoPdf = false;
        this.carregar();
      },
      error: (e: HttpErrorResponse) => {
        this.toast.errorFromHttpResponse(e, resolveHttpError(e, 'Erro ao processar PDF.'));
        this.enviandoPdf = false;
      }
    });
  }

  brl(v: number | null | undefined): string {
    return Number(v || 0).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  }
}
