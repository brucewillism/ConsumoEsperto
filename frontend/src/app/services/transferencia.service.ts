import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable, catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';
import { environment } from '../../environments/environment';
import { TransferenciaConta, TransferenciaRequest } from '../models/transferencia.model';

@Injectable({ providedIn: 'root' })
export class TransferenciaService {
  private readonly API_URL = `${environment.apiUrl}/transferencias`;

  constructor(
    private http: HttpClient,
    private authService: AuthService
  ) {}

  private getHeaders(): HttpHeaders {
    const token = this.authService.getToken();
    return new HttpHeaders({
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
    });
  }

  fazerTransferencia(dados: TransferenciaRequest): Observable<TransferenciaConta> {
    return this.http
      .post<TransferenciaConta>(this.API_URL, dados, { headers: this.getHeaders() })
      .pipe(catchError((err) => throwError(() => err)));
  }

  listarHistorico(): Observable<TransferenciaConta[]> {
    return this.http
      .get<TransferenciaConta[]>(this.API_URL, { headers: this.getHeaders() })
      .pipe(catchError((err) => throwError(() => err)));
  }
}
