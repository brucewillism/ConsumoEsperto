import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatRadioModule } from '@angular/material/radio';
import { MatSelectModule } from '@angular/material/select';
import {
  ImportacaoConfirmacao,
  ImportacaoFatura,
  ImportacaoFaturaService,
} from '../../services/importacao-fatura.service';
import { ConfirmDialogService } from '../../services/confirm-dialog.service';
import { ToastService } from '../../services/toast.service';
import { ContaBancariaService } from '../../services/conta-bancaria.service';
import { CartaoCreditoService } from '../../services/cartao-credito.service';
import { CategoriaService } from '../../services/categoria.service';
import { ContaBancaria } from '../../models/conta-bancaria.model';
import { CartaoCredito } from '../../models/cartao-credito.model';
import { Categoria } from '../../models/categoria.model';
import { resolveHttpError } from '../../shared/utils/form.utils';
import { WhatsappParityHintComponent } from '../../shared/whatsapp-parity-hint/whatsapp-parity-hint.component';
import { LoadingIndicatorComponent } from '../../components/loading-indicator/loading-indicator.component';

@Component({
  selector: 'app-importacoes-pendentes',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatCardModule,
    MatCheckboxModule,
    MatExpansionModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatRadioModule,
    MatSelectModule,
    WhatsappParityHintComponent,
    LoadingIndicatorComponent,
  ],
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
  senhaPdf = '';
  contas: ContaBancaria[] = [];
  cartoes: CartaoCredito[] = [];
  categorias: Categoria[] = [];
  escolhaTipo: Record<number, 'CONTA' | 'CARTAO'> = {};
  escolhaContaId: Record<number, number | null> = {};
  escolhaCartaoId: Record<number, number | null> = {};
  ultimoRelatorio: ImportacaoConfirmacao | null = null;

  constructor(
    private importacaoService: ImportacaoFaturaService,
    private toast: ToastService,
    private confirmDialog: ConfirmDialogService,
    private contaService: ContaBancariaService,
    private cartaoService: CartaoCreditoService,
    private categoriaService: CategoriaService
  ) {}

  ngOnInit(): void {
    this.carregar();
    this.contaService.listarContasAtivas().subscribe({ next: (c) => (this.contas = c || []) });
    this.cartaoService.getCartoes().subscribe({ next: (c) => (this.cartoes = c || []) });
    this.categoriaService.buscarPorUsuario().subscribe({ next: (c) => (this.categorias = c || []) });
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

  isCsv(imp: ImportacaoFatura): boolean {
    const t = imp.tipoArquivo || 'INVOICE_PDF';
    return t === 'BANK_STATEMENT_CSV' || t === 'CARD_STATEMENT_CSV' || t === 'NEEDS_REVIEW';
  }

  tipoDetectadoLabel(imp: ImportacaoFatura): string {
    switch (imp.tipoArquivo) {
      case 'INVOICE_PDF':
        return 'Fatura PDF';
      case 'BANK_STATEMENT_CSV':
        return 'Extrato bancário CSV';
      case 'CARD_STATEMENT_CSV':
        return 'Extrato de cartão CSV';
      case 'NEEDS_REVIEW':
        return 'CSV — indicar se é conta ou cartão';
      default:
        return this.isCsv(imp) ? 'Extrato CSV' : 'Fatura PDF';
    }
  }

  statusLabel(status?: string | null): string {
    switch (status) {
      case 'NOVO':
        return 'NOVO';
      case 'DUPLICATE':
        return 'DUPLICATE';
      case 'MATCHED_EXISTING':
        return 'MATCHED_EXISTING';
      case 'NEEDS_REVIEW':
        return 'NEEDS_REVIEW';
      case 'IGNORED':
        return 'IGNORED';
      case 'INVALID':
        return 'INVALID';
      default:
        return status || '';
    }
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

  confirmarEscolhaRecurso(imp: ImportacaoFatura): void {
    const tipo = this.escolhaTipo[imp.id];
    if (!tipo) {
      this.toast.warning('Indique se este CSV é de conta bancária ou de cartão.');
      return;
    }
    const contaId = tipo === 'CONTA' ? this.escolhaContaId[imp.id] : null;
    const cartaoId = tipo === 'CARTAO' ? this.escolhaCartaoId[imp.id] : null;
    if (tipo === 'CONTA' && !contaId) {
      this.toast.warning('Selecione a conta bancária.');
      return;
    }
    if (tipo === 'CARTAO' && !cartaoId) {
      this.toast.warning('Selecione o cartão.');
      return;
    }
    this.confirmandoId = imp.id;
    this.importacaoService.escolhaRecurso(imp.id, tipo, contaId, cartaoId).subscribe({
      next: () => {
        this.toast.success('Recurso associado. Revise os lançamentos.');
        this.confirmandoId = null;
        this.carregar();
      },
      error: (e: HttpErrorResponse) => {
        this.toast.errorFromHttpResponse(e, 'Não foi possível associar a conta ou o cartão.');
        this.confirmandoId = null;
      }
    });
  }

  onItemChanged(imp: ImportacaoFatura): void {
    if (!this.isCsv(imp)) {
      return;
    }
    const ajustes = imp.itens.map((item, index) => ({
      index,
      selecionado: item.selecionado,
      data: item.data,
      tipoLinha: item.tipoLinha || undefined,
      categoriaId: item.categoriaId,
      contaBancariaId: item.contaBancariaId,
      cartaoCreditoId: item.cartaoCreditoId,
      statusPreview: item.statusPreview || undefined,
    }));
    this.importacaoService.atualizarItens(imp.id, ajustes).subscribe({
      next: (atualizado) => {
        const idx = this.importacoes.findIndex((i) => i.id === imp.id);
        if (idx >= 0) {
          this.importacoes[idx] = atualizado;
        }
      },
      error: () => {
        /* preview local permanece; a confirmação revalida */
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
    if (imp.precisaEscolhaRecurso || imp.tipoArquivo === 'NEEDS_REVIEW') {
      this.toast.warning('Indique se este CSV é de conta bancária ou de cartão.');
      return;
    }
    const indices = imp.itens
      .map((item, index) => ({ item, index }))
      .filter(({ item }) => this.itemConfirmavel(imp, item))
      .map(({ index }) => index);
    if (!indices.length) {
      if (this.podeRegistrarSoHistorico(imp)) {
        this.executarConfirmacao(imp, [], false);
        return;
      }
      this.toast.warning('Selecione pelo menos um lançamento novo.');
      return;
    }
    this.executarConfirmacao(imp, indices, false);
  }

  private itemConfirmavel(imp: ImportacaoFatura, item: ImportacaoFatura['itens'][number]): boolean {
    if (this.isCsv(imp)) {
      return !!item.selecionado && (item.statusPreview === 'NOVO' || item.novo);
    }
    return item.novo && item.selecionado;
  }

  podeRegistrarSoHistorico(imp: ImportacaoFatura): boolean {
    return this.faturaPagaNoBanco(imp) && (imp.itens?.length ?? 0) > 0 && this.conciliacaoOk(imp);
  }

  confirmarLabel(imp: ImportacaoFatura): string {
    if (this.confirmandoId === imp.id) {
      return 'Salvando...';
    }
    if (this.podeRegistrarSoHistorico(imp) && imp.novosDetectados === 0) {
      return 'Registrar fatura no histórico';
    }
    return 'Confirmar selecionados';
  }

  private executarConfirmacao(imp: ImportacaoFatura, indices: number[], ignorarDivergencia: boolean): void {
    this.confirmandoId = imp.id;
    const extra = this.isCsv(imp)
      ? {
          ajustes: imp.itens.map((item, index) => ({
            index,
            selecionado: item.selecionado,
            data: item.data,
            tipoLinha: item.tipoLinha || undefined,
            categoriaId: item.categoriaId,
            contaBancariaId: item.contaBancariaId,
            cartaoCreditoId: item.cartaoCreditoId,
            statusPreview: item.statusPreview || undefined,
          })),
        }
      : undefined;
    this.importacaoService.confirmar(imp.id, indices, ignorarDivergencia, extra).subscribe({
      next: (res) => {
        this.ultimoRelatorio = res;
        const msg = res.mensagem
          ? res.mensagem.split('\n')[0]
          : res.criadas > 0
            ? `${res.criadas} lançamento(s) importado(s). Veja no Dashboard se há protocolos de teto sugeridos.`
            : res.conciliadas > 0
              ? `Fatura registrada: ${res.conciliadas} compra(s) já existente(s) vinculada(s) à fatura.`
              : 'Fatura registrada no histórico.';
        this.toast.success(msg);
        this.confirmandoId = null;
        this.carregar();
      },
      error: (e: HttpErrorResponse) => {
        this.confirmandoId = null;
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
    if (file) this.enviarArquivo(file);
    input.value = '';
  }

  onDragOver(event: DragEvent): void {
    if (this.enviandoPdf) {
      return;
    }
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
    if (this.enviandoPdf) {
      return;
    }
    const file = event.dataTransfer?.files?.[0];
    if (file) this.enviarArquivo(file);
  }

  enviarArquivo(file: File): void {
    const name = file.name.toLowerCase();
    const csv = name.endsWith('.csv') || name.endsWith('.txt')
      || file.type === 'text/csv' || file.type === 'application/csv' || file.type === 'text/plain';
    const pdf = name.endsWith('.pdf') || file.type === 'application/pdf';
    if (!csv && !pdf) {
      this.toast.warning('Arraste ou selecione uma fatura em PDF ou um extrato em CSV.');
      return;
    }
    this.enviandoPdf = true;
    const senha = pdf ? (this.senhaPdf?.trim() || undefined) : undefined;
    this.importacaoService.upload(file, senha).subscribe({
      next: (imp) => {
        this.toast.success(
          this.isCsv(imp)
            ? 'Extrato processado. Revise os lançamentos antes de confirmar.'
            : 'Fatura processada. Revise a conciliação antes de confirmar.'
        );
        this.senhaPdf = '';
        this.enviandoPdf = false;
        this.carregar();
      },
      error: (e: HttpErrorResponse) => {
        this.toast.errorFromHttpResponse(e, resolveHttpError(e, 'Erro ao processar o arquivo.'));
        this.enviandoPdf = false;
      }
    });
  }

  brl(v: number | null | undefined): string {
    return Number(v || 0).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  }

  somaSelecionados(imp: ImportacaoFatura): number {
    const incluir = this.faturaPagaNoBanco(imp)
      ? (i: ImportacaoFatura['itens'][number]) => i.selecionado
      : (i: ImportacaoFatura['itens'][number]) => this.itemConfirmavel(imp, i);
    return imp.itens
      .filter(incluir)
      .reduce((acc, i) => acc + Number(i.valor || 0), 0);
  }

  resumoSelecionados(imp: ImportacaoFatura): string {
    if (this.isCsv(imp)) {
      return `${imp.novosDetectados} novos · ${imp.duplicadas || 0} dup. · ${imp.matchedExistentes || 0} conciliados · ${imp.necessitamRevisao || 0} revisão`;
    }
    if (this.faturaPagaNoBanco(imp) && imp.novosDetectados === 0) {
      return `${imp.itens.length} já lançados · ${this.brl(imp.somaLancamentos)} na fatura`;
    }
    return `${imp.novosDetectados} novos · ${this.brl(this.somaSelecionados(imp))} selecionados`;
  }

  conciliacaoOk(imp: ImportacaoFatura): boolean {
    if (this.isCsv(imp)) {
      return true;
    }
    if (imp.situacaoLeituraPdf === 'PAGA_NO_BANCO') {
      return (imp.itens?.length ?? 0) > 0 && Number(imp.somaLancamentos || 0) > 0;
    }
    const tol = Math.max(5, Math.min(120, Number(imp.valorTotal || 0) * 0.02));
    return Number(imp.diferencaLancamentos ?? 999) <= tol;
  }

  faturaPagaNoBanco(imp: ImportacaoFatura): boolean {
    return imp.situacaoLeituraPdf === 'PAGA_NO_BANCO';
  }

  faturaTotalZerada(imp: ImportacaoFatura): boolean {
    return this.faturaPagaNoBanco(imp);
  }

  checkboxDisabled(imp: ImportacaoFatura, item: ImportacaoFatura['itens'][number]): boolean {
    if (this.isCsv(imp)) {
      return item.statusPreview === 'DUPLICATE'
        || item.statusPreview === 'MATCHED_EXISTING'
        || item.statusPreview === 'INVALID';
    }
    return !item.novo;
  }

  loadingMessage(): string {
    return 'Lendo o arquivo enviado';
  }
}
