import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, timeout } from 'rxjs';
import { environment } from '../../environments/environment';

export type ImportacaoTipoArquivo =
  | 'INVOICE_PDF'
  | 'BANK_STATEMENT_CSV'
  | 'CARD_STATEMENT_CSV'
  | 'NEEDS_REVIEW';

export type ImportacaoItemStatus =
  | 'NOVO'
  | 'DUPLICATE'
  | 'MATCHED_EXISTING'
  | 'NEEDS_REVIEW'
  | 'IGNORED'
  | 'INVALID';

export interface ImportacaoFaturaItem {
  data: string;
  descricao: string;
  valor: number;
  parcelaAtual?: number | null;
  totalParcelas?: number | null;
  novo: boolean;
  selecionado: boolean;
  sourceLine?: number | null;
  merchant?: string | null;
  currency?: string | null;
  tipoLinha?: string | null;
  statusPreview?: ImportacaoItemStatus | string | null;
  accountHint?: string | null;
  cardHint?: string | null;
  externalId?: string | null;
  fingerprint?: string | null;
  matchedTransacaoId?: number | null;
  categoriaId?: number | null;
  contaBancariaId?: number | null;
  cartaoCreditoId?: number | null;
  faturaId?: number | null;
  faturaPrevistaLabel?: string | null;
  invalidReason?: string | null;
}

export interface ImportacaoFatura {
  id: number;
  cartaoCreditoId?: number | null;
  cartaoCreditoNome?: string | null;
  bancoCartao: string;
  dataVencimento?: string | null;
  dataFechamento?: string | null;
  valorTotal: number;
  pagamentoMinimo: number;
  status: string;
  novosDetectados: number;
  itens: ImportacaoFaturaItem[];
  auditorias: string[];
  dataCriacao: string;
  aguardandoEscolhaSaldoAnterior?: boolean;
  saldoFaturaAnterior?: number | null;
  saldoFaturaAtual?: number | null;
  somaLancamentos?: number | null;
  diferencaLancamentos?: number | null;
  situacaoLeituraPdf?: 'ABERTA' | 'PAGA_NO_BANCO' | null;
  tipoArquivo?: ImportacaoTipoArquivo | string | null;
  arquivoNome?: string | null;
  contaBancariaId?: number | null;
  contaBancariaNome?: string | null;
  precisaEscolhaRecurso?: boolean | null;
  periodoInicio?: string | null;
  periodoFim?: string | null;
  quantidadeLinhas?: number | null;
  quantidadeValidas?: number | null;
  quantidadeInvalidas?: number | null;
  totalDespesas?: number | null;
  totalReceitas?: number | null;
  duplicadas?: number | null;
  matchedExistentes?: number | null;
  necessitamRevisao?: number | null;
}

export interface ImportacaoConfirmacaoFaturaImpacto {
  faturaId?: number;
  rotulo?: string;
  periodo?: string;
  transacoesAdicionadas?: number;
  totalAntes?: number;
  totalDepois?: number;
}

export interface ImportacaoConfirmacao {
  criadas: number;
  conciliadas: number;
  futuras?: number;
  registrosNaFaturaAtual?: number;
  duplicadas?: number;
  matched?: number;
  revisao?: number;
  falhas?: number;
  processadas?: number;
  mensagem?: string;
  faturas?: ImportacaoConfirmacaoFaturaImpacto[];
}

@Injectable({ providedIn: 'root' })
export class ImportacaoFaturaService {
  private readonly base = `${environment.apiUrl}/importacoes/faturas`;

  constructor(private http: HttpClient) {}

  pendentes(): Observable<ImportacaoFatura[]> {
    return this.http.get<ImportacaoFatura[]>(`${this.base}/pendentes`);
  }

  excluirTodasPendentes(): Observable<{ removidas: number }> {
    return this.http.delete<{ removidas: number }>(`${this.base}/pendentes`);
  }

  excluirPendente(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }

  confirmar(
    id: number,
    indices: number[],
    ignorarDivergencia = false,
    extra?: {
      tipoRecurso?: string;
      contaBancariaId?: number | null;
      cartaoCreditoId?: number | null;
      ajustes?: Array<{
        index: number;
        selecionado?: boolean;
        data?: string;
        tipoLinha?: string;
        categoriaId?: number | null;
        contaBancariaId?: number | null;
        cartaoCreditoId?: number | null;
        statusPreview?: string;
      }>;
    }
  ): Observable<ImportacaoConfirmacao> {
    return this.http
      .post<ImportacaoConfirmacao>(`${this.base}/${id}/confirmar`, {
        indices,
        ignorarDivergencia,
        ...(extra || {}),
      })
      .pipe(timeout(300_000));
  }

  escolhaSaldoAnterior(id: number, somar: boolean): Observable<ImportacaoFatura> {
    return this.http.post<ImportacaoFatura>(
      `${this.base}/${id}/escolha-saldo-anterior`,
      null,
      { params: { somar: String(somar) } }
    );
  }

  escolhaRecurso(
    id: number,
    tipoRecurso: 'CONTA' | 'CARTAO',
    contaBancariaId?: number | null,
    cartaoCreditoId?: number | null
  ): Observable<ImportacaoFatura> {
    return this.http.post<ImportacaoFatura>(`${this.base}/${id}/escolha-recurso`, {
      tipoRecurso,
      contaBancariaId,
      cartaoCreditoId,
    });
  }

  atualizarItens(
    id: number,
    ajustes: NonNullable<Parameters<ImportacaoFaturaService['confirmar']>[3]>['ajustes']
  ): Observable<ImportacaoFatura> {
    return this.http.post<ImportacaoFatura>(`${this.base}/${id}/itens`, { ajustes });
  }

  /** PDF de fatura ou CSV de extrato. PDF+IA pode levar vários minutos. */
  upload(
    file: File,
    senhaPdf?: string,
    tipoRecurso?: string,
    contaBancariaId?: number,
    cartaoCreditoId?: number
  ): Observable<ImportacaoFatura> {
    const form = new FormData();
    form.append('file', file);
    const senha = senhaPdf?.trim();
    if (senha) {
      form.append('senhaPdf', senha);
    }
    if (tipoRecurso) {
      form.append('tipoRecurso', tipoRecurso);
    }
    if (contaBancariaId != null) {
      form.append('contaBancariaId', String(contaBancariaId));
    }
    if (cartaoCreditoId != null) {
      form.append('cartaoCreditoId', String(cartaoCreditoId));
    }
    return this.http.post<ImportacaoFatura>(`${this.base}/upload`, form).pipe(timeout(300_000));
  }
}
