import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { AuthService } from './auth.service';
import { environment } from '../../environments/environment';
import { ExportacaoCsvDownload, ExportacaoTransacaoFiltro } from '../models/exportacao-transacao-filtro.model';
import { montarParamsCsv, resolverNomeArquivoCsv } from '../utils/exportacao-csv.util';

@Injectable({ providedIn: 'root' })
export class ExportacaoDadosService {
  private readonly base = `${environment.apiUrl}/exportacao`;

  constructor(
    private http: HttpClient,
    private authService: AuthService
  ) {}

  private authHeaders(): HttpHeaders {
    return new HttpHeaders({ Authorization: `Bearer ${this.authService.getToken()}` });
  }

  exportarTransacoesCsv(filtro: ExportacaoTransacaoFiltro): Observable<ExportacaoCsvDownload> {
    const params = montarParamsCsv(filtro);
    return this.http.get(`${this.base}/csv/transacoes`, {
      headers: this.authHeaders(),
      params,
      responseType: 'blob',
      observe: 'response',
    }).pipe(
      map((resp: HttpResponse<Blob>) => ({
        blob: resp.body ?? new Blob(),
        nomeArquivo: resolverNomeArquivoCsv(resp.headers.get('Content-Disposition')),
      }))
    );
  }

  exportarCompletoCsv(dataInicio: string, dataFim: string): Observable<Blob> {
    return this.http.get(`${this.base}/csv/completo`, {
      headers: this.authHeaders(),
      params: { dataInicio, dataFim },
      responseType: 'blob',
    });
  }
}
