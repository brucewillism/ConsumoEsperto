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
}
