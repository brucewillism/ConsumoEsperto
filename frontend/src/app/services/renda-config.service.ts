import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AuthService } from './auth.service';
import { environment } from '../../environments/environment';

export interface DescontoFixoDto {
  rotulo: string;
  valor: number;
}

export interface RendaConfigDto {
  salarioBruto: number;
  descontosFixos: DescontoFixoDto[];
  diaPagamento: number | null;
  salarioLiquido: number;
  totalDescontos: number;
  percentualDescontosSobreBruto: number;
  receitaAutomaticaAtiva: boolean;
}

export interface ContrachequeDto {
  id: number;
  empresa: string;
  mes: number;
  ano: number;
  salarioBruto: number;
  salarioLiquido: number;
  totalDescontos: number;
  descontos: DescontoFixoDto[];
  insights: string[];
  status: string;
  dataCriacao: string;
  /** null = legado; true = bruto ≈ líquido + Σ descontos (tolerância no backend). */
  auditoriaSomaBrutoOk?: boolean | null;
  /** Diferença bruto − (líquido + descontos), quando a API envia. */
  auditoriaDeltaBruto?: number | null;
}

@Injectable({ providedIn: 'root' })
export class RendaConfigService {
  private readonly apiUrl = `${environment.apiUrl}/renda-config`;

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

  obter(): Observable<RendaConfigDto> {
    return this.http.get<RendaConfigDto>(this.apiUrl, { headers: this.headers() });
  }

  historicoContracheques(): Observable<ContrachequeDto[]> {
    return this.http.get<ContrachequeDto[]>(`${this.apiUrl}/contracheques`, { headers: this.headers() });
  }

  confirmarContracheque(id: number): Observable<ContrachequeDto> {
    return this.http.post<ContrachequeDto>(`${this.apiUrl}/contracheques/${id}/confirmar`, {}, { headers: this.headers() });
  }

  uploadContracheque(file: File): Observable<ContrachequeDto> {
    const form = new FormData();
    form.append('file', file);
    const token = this.authService.getToken();
    return this.http.post<ContrachequeDto>(`${this.apiUrl}/contracheques/upload`, form, {
      headers: new HttpHeaders({ Authorization: `Bearer ${token}` })
    });
  }
}
