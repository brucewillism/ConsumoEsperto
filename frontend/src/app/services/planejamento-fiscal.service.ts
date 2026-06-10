import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AuthService } from './auth.service';
import { environment } from '../../environments/environment';

export type TipoRecebimento13 = 'PARCELA_UNICA' | 'DUAS_PARCELAS';

export interface ConfiguracaoFiscalDto {
  mesRestituicaoIr: number | null;
  valorRestituicao: number | null;
  tipoRecebimento13: TipoRecebimento13 | null;
  mesParcelaUnica: number | null;
  mesPrimeiraParcela: number | null;
  mesSegundaParcela: number | null;
  diaPagamento13: number | null;
  provisionamentoAtivo: boolean;
}

export interface BaseContrachequeFiscalDto {
  salarioBruto: number;
  salarioLiquido: number;
  descontosImposto: number;
  mesReferencia: number | null;
  anoReferencia: number | null;
  fonte: string;
  estimado: boolean;
}

export interface ParcelaReceitaFiscalDto {
  origem: string;
  rotulo: string;
  mes: number;
  dia: number;
  valor: number;
  observacao: string | null;
}

export interface PlanejamentoFiscalResumoDto {
  configuracao: ConfiguracaoFiscalDto;
  baseContracheque: BaseContrachequeFiscalDto | null;
  parcelas: ParcelaReceitaFiscalDto[];
  totalProvisionado: number;
  transacoesSincronizadas: number;
  aviso: string | null;
}

export interface ConfiguracaoFiscalRequest {
  mesRestituicaoIr?: number | null;
  valorRestituicao?: number | null;
  tipoRecebimento13?: TipoRecebimento13 | null;
  mesParcelaUnica?: number | null;
  mesPrimeiraParcela?: number | null;
  mesSegundaParcela?: number | null;
  diaPagamento13?: number | null;
  provisionamentoAtivo?: boolean;
}

@Injectable({ providedIn: 'root' })
export class PlanejamentoFiscalService {
  private readonly apiUrl = `${environment.apiUrl}/planejamento-fiscal`;

  constructor(
    private http: HttpClient,
    private authService: AuthService
  ) {}

  private headers(): HttpHeaders {
    const token = this.authService.getToken();
    return new HttpHeaders({
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json'
    });
  }

  obter(): Observable<ConfiguracaoFiscalDto> {
    return this.http.get<ConfiguracaoFiscalDto>(this.apiUrl, { headers: this.headers() });
  }

  salvar(body: ConfiguracaoFiscalRequest): Observable<ConfiguracaoFiscalDto> {
    return this.http.put<ConfiguracaoFiscalDto>(this.apiUrl, body, { headers: this.headers() });
  }

  simular(): Observable<PlanejamentoFiscalResumoDto> {
    return this.http.get<PlanejamentoFiscalResumoDto>(`${this.apiUrl}/simulacao`, { headers: this.headers() });
  }
}
